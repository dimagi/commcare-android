	/**
 * 
 */
package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.Vector;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.ACase;
import org.commcare.android.models.User;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.Base64;
import org.commcare.android.util.Base64DecoderException;
import org.commcare.android.util.CommCareUtil;
import org.commcare.android.util.CryptUtil;
import org.commcare.android.util.HttpRequestGenerator;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.bitcache.BitCache;
import org.commcare.android.util.bitcache.BitCacheFactory;
import org.commcare.cases.util.CasePurgeFilter;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.xml.CommCareTransactionParserFactory;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.IStorageIterator;
import org.javarosa.core.services.storage.StorageFullException;
import org.javarosa.core.util.StreamsUtil;
import org.javarosa.model.xform.XPathReference;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

/**
 * @author ctsims
 *
 */
public class DataPullTask extends AsyncTask<Void, Integer, Integer> {

	String server;
	String keyProvider;
	String username;
	String password;
	Context c;
	
	DataPullListener listener;
	
	public static final int DOWNLOAD_SUCCESS = 0;
	public static final int AUTH_FAILED = 1;
	public static final int BAD_DATA = 2;
	public static final int UNKNOWN_FAILURE = 4;
	public static final int UNREACHABLE_HOST = 8;
	
	public static final int PROGRESS_STARTED = 0;
	public static final int PROGRESS_CLEANED = 1;
	public static final int PROGRESS_AUTHED = 2;
	public static final int PROGRESS_DONE= 4;
	public static final int PROGRESS_RECOVERY_NEEDED= 8;
	public static final int PROGRESS_RECOVERY_STARTED= 16;
	public static final int PROGRESS_RECOVERY_FAIL_SAFE = 32;
	public static final int PROGRESS_RECOVERY_FAIL_BAD = 64;

	
	public DataPullTask(String username, String password, String server, String keyProvider, Context c) {
		this.server = server;
		this.keyProvider = keyProvider;
		this.username = username;
		this.password = password;
		this.c = c;
	}
	
	public void setPullListener(DataPullListener listener) {
		this.listener = listener;
	}

	@Override
	protected void onPostExecute(Integer result) {
		if(listener != null) {
			listener.finished(result);
		}
		//These will never get Zero'd otherwise
		c = null;
		server = null;
		password = null;
	}

	@Override
	protected void onProgressUpdate(Integer... values) {
		if(listener != null) {
			listener.progressUpdate(values);
		}
	}

	protected Integer doInBackground(Void... params) {
		publishProgress(PROGRESS_STARTED);
		
		boolean loginNeeded = true;
		try {
			loginNeeded = !CommCareApplication._().getSession().isLoggedIn();
		} catch(SessionUnavailableException sue) {
			//expected if we aren't initialized.
		}
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);

		if(loginNeeded) {
	    	if("true".equals(prefs.getString("cc-auto-update","false"))) {
	    		Editor e = prefs.edit();
	    		e.putLong("last-ota-restore", new Date().getTime());
	    		e.commit();
	    	}
		}
		CommCareTransactionParserFactory factory = new CommCareTransactionParserFactory(c) {
			@Override
			public void reportProgress(int progress) {
				DataPullTask.this.publishProgress(PROGRESS_AUTHED,progress);
			}
		};

		HttpRequestGenerator requestor = new HttpRequestGenerator(username, password);
			
			try {
				//This is a dangerous way to do this (the null settings), should revisit later
				SecretKeySpec spec = null;
				if(loginNeeded) {
					//Get the key 
					//SecretKeySpec spec = getKeyForDevice();
					spec = generateTestKey();
					
					if(spec == null) {
						this.publishProgress(PROGRESS_DONE);
						return UNKNOWN_FAILURE;
					}
					
					//add to transaction parser factory
					byte[] wrappedKey = CryptUtil.wrapKey(spec,password);
					factory.initUserParser(wrappedKey);
				} else {
					factory.initUserParser(CommCareApplication._().getSession().getLoggedInUser().getWrappedKey());
				}
				
				if(loginNeeded) {
					//This is necessary (currently) to make sure that data
					//is encoded. Probably a better way to do this.
					CommCareApplication._().logIn(spec.getEncoded(), null);
				}
				
				//Purge
				
				purgeCases();
				this.publishProgress(PROGRESS_CLEANED);
					
				
				HttpResponse response = requestor.makeCaseFetchRequest(server);
				int responseCode = response.getStatusLine().getStatusCode();
				if(responseCode == 401) {
					//If we logged in, we need to drop those credentials
					if(loginNeeded) {
						CommCareApplication._().logout();
					}
					return AUTH_FAILED;
				} else if(responseCode >= 200 && responseCode < 300) {
					
					this.publishProgress(PROGRESS_AUTHED,0);
					
					int dataSizeGuess = -1;
					if(response.containsHeader("Content-Length")) {
						String length = response.getFirstHeader("Content-Length").getValue();
						try{
							dataSizeGuess = Integer.parseInt(length);
						} catch(Exception e) {
							//Whatever.
						}
					}
					
					BitCache cache = BitCacheFactory.getCache(c, dataSizeGuess);
					
					cache.initializeCache();
					
					try {
						OutputStream cacheOut = cache.getCacheStream();
						AndroidStreamUtil.writeFromInputToOutput(response.getEntity().getContent(), cacheOut);
					
						InputStream cacheIn = cache.retrieveCache();
						String syncToken = readInput(cacheIn, factory);
						updateUserSyncToken(syncToken);
						
						//record when we last synced
			    		Editor e = prefs.edit();
			    		e.putLong("last-succesful-sync", new Date().getTime());
			    		e.commit();
						
							
						this.publishProgress(PROGRESS_DONE);
						return DOWNLOAD_SUCCESS;
					} catch (InvalidStructureException e) {
						e.printStackTrace();
						return BAD_DATA;
					} catch (XmlPullParserException e) {
						e.printStackTrace();
						return BAD_DATA;
					} catch (UnfullfilledRequirementsException e) {
						e.printStackTrace();
					} catch (IllegalStateException e) {
						e.printStackTrace();
					} catch (StorageFullException e) {
						e.printStackTrace();
					} finally {
						//destroy temp file
						cache.release();
					}
				} else if(responseCode == 412) {
					//Our local state is bad. We need to do a full restore.
					int returnCode = recover(requestor, factory);
					
					if(returnCode == PROGRESS_DONE) {
						//All set! Awesome recovery
						this.publishProgress(PROGRESS_DONE);
						return DOWNLOAD_SUCCESS;
					}
					
					else if(returnCode == PROGRESS_RECOVERY_FAIL_SAFE) {
						//Things didn't go super well, but they might next time!
						
						//wipe our login if one happened
						if(loginNeeded) {
							CommCareApplication._().logout();
						}
						this.publishProgress(PROGRESS_DONE);
						return UNKNOWN_FAILURE;
					} else if(returnCode == PROGRESS_RECOVERY_FAIL_BAD) {
						//WELL! That wasn't so good. TODO: Is there anything 
						//we can do about this?
						
						//wipe our login if one happened
						if(loginNeeded) {
							CommCareApplication._().logout();
						}
						this.publishProgress(PROGRESS_DONE);
						return UNKNOWN_FAILURE;
					}
					
					
					
					if(loginNeeded) {
						CommCareApplication._().logout();
					}
				}
				
				
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnknownHostException e) {
				this.publishProgress(PROGRESS_DONE);
				if(loginNeeded) {
					CommCareApplication._().logout();
				}
				return UNREACHABLE_HOST;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}catch (SessionUnavailableException sue) {
				//TODO: Keys were lost somehow.
				sue.printStackTrace();
			}
			if(loginNeeded) {
				CommCareApplication._().logout();
			}
			this.publishProgress(PROGRESS_DONE);
			return UNKNOWN_FAILURE;
			
	}
		
	//TODO: This and the normal sync share a ton of code. It's hard to really... figure out the right way to 
	private int recover(HttpRequestGenerator requestor, CommCareTransactionParserFactory factory) {
		this.publishProgress(PROGRESS_RECOVERY_NEEDED);
		
		InputStream cacheIn;
		BitCache cache = null;
		
		//This chunk is the safe field of operations which can all fail in IO in such a way that we can
		//just report back that things didn't work and don't need to attempt any recovery or additional
		//work
		try {

			//Make a new request without all of the flags
			HttpResponse response = requestor.makeCaseFetchRequest(server, false);
			int responseCode = response.getStatusLine().getStatusCode();
			
			//We basically only care about a positive response, here. Anything else would have been caught by the other request.
			if(!(responseCode >= 200 && responseCode < 300)) {
				return PROGRESS_RECOVERY_FAIL_SAFE;
			}
			
			//Otherwise proceed with the restore
			int dataSizeGuess = -1;
			if(response.containsHeader("Content-Length")) {
				String length = response.getFirstHeader("Content-Length").getValue();
				try{
					dataSizeGuess = Integer.parseInt(length);
				} catch(Exception e) {
					//Whatever.
				}
			}
			//Grab a cache. The plan is to download the incoming data, wipe (move) the existing db, and then
			//restore fresh from the downloaded file
			cache = BitCacheFactory.getCache(c, dataSizeGuess);
			cache.initializeCache();
			
			OutputStream cacheOut = cache.getCacheStream();
			AndroidStreamUtil.writeFromInputToOutput(response.getEntity().getContent(), cacheOut);
		
			cacheIn = cache.retrieveCache();
				
		} catch(IOException e) {
			e.printStackTrace();
			if(cache != null) {
				//If we made a temp file, we're done with it here.
				cache.release();
			}
			//Ok, well, we're bailing here, but we didn't make any changes
			return PROGRESS_RECOVERY_FAIL_SAFE;
		}
		
		
		this.publishProgress(PROGRESS_RECOVERY_STARTED);

		//Ok. Here's where things get real. We now have a stable copy of the fresh data from the
		//server, so it's "safe" for us to wipe the casedb copy of it.
		
		//CTS: We're not doing this in a super good way right now, need to be way more fault tolerant.
		//this is the temporary implementation of everything past this point
		
		//Wipe storage
		//TODO: move table instead.
		CommCareApplication._().getStorage(ACase.STORAGE_KEY, ACase.class).removeAll();
		
		
		try { 
			//Get new data
			String syncToken = readInput(cacheIn, factory);
			updateUserSyncToken(syncToken);
			return PROGRESS_DONE;
		} catch (InvalidStructureException e) {
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (UnfullfilledRequirementsException e) {
			e.printStackTrace();
		} catch (StorageFullException e) {
			e.printStackTrace();
		} 
		
		//These last two aren't a sign that the incoming data is bad, but
		//we still can't recover from them usefully
		catch (SessionUnavailableException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			//destroy temp file
			cache.release();
		}
		
		//OK, so we would have returned success by now if things had worked out, which means that instead we got an error
		//while trying to parse everything out. We need to recover from that error here and rollback the changes
		
		//TODO: Roll back changes
		return PROGRESS_RECOVERY_FAIL_BAD;
	}

	private void updateUserSyncToken(String syncToken) throws StorageFullException {
		SqlIndexedStorageUtility<User> storage = CommCareApplication._().getStorage(User.STORAGE_KEY, User.class);
		try {
			User u = storage.getRecordForValue(User.META_USERNAME, username);
			u.setSyncToken(syncToken);
			storage.write(u);
		} catch(NoSuchElementException nsee) {
			//TODO: Something here? Maybe figure out if we downloaded a user from the server and attach the data to it?
		}
	}

	private void purgeCases() {
		//We need to determine if we're using ownership for purging. For right now, only in sync mode
		Vector<String> owners = new Vector<String>();
		Vector<String> users = new Vector<String>(); 
		for(IStorageIterator<User> userIterator = CommCareApplication._().getStorage(User.STORAGE_KEY, User.class).iterate(); userIterator.hasMore();) {
			String id = userIterator.nextRecord().getUniqueId();
			owners.addElement(id);
			users.addElement(id);
		}
		
		//Now add all of the relevant groups
		//TODO: Wow. This is.... kind of megasketch
		for(String userId : users) {
			DataInstance instance = CommCareUtil.loadFixture("user-groups", userId);
			if(instance == null) { continue; }
			EvaluationContext ec = new EvaluationContext(instance);
			for(TreeReference ref : ec.expandReference(XPathReference.getPathExpr("/groups/group/@id").getReference())) {
				AbstractTreeElement<AbstractTreeElement> idelement = ec.resolveReference(ref);
				if(idelement.getValue() != null) {
					owners.addElement(idelement.getValue().uncast().getString());
				}
			}
		}
			
		SqlIndexedStorageUtility<ACase> storage = CommCareApplication._().getStorage(ACase.STORAGE_KEY, ACase.class);
		CasePurgeFilter filter = new CasePurgeFilter(storage, owners);
		storage.removeAll(filter);		
	}

	private String readInput(InputStream stream, CommCareTransactionParserFactory factory) throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException, SessionUnavailableException{
		DataModelPullParser parser;
		
		factory.initCaseParser();
		
		Hashtable<String,String> formNamespaces = new Hashtable<String, String>(); 
		
		for(String xmlns : CommCareApplication._().getCommCarePlatform().getInstalledForms()) {
			//TODO: rewrite this based on content providers
			//formNamespaces.put(xmlns, CommCareApplication._().getCommCarePlatform().getFormContentUri(xmlns));
		}
		factory.initFormInstanceParser(formNamespaces);
		
//		SqlIndexedStorageUtility<FormRecord> formRecordStorge = CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
//
//		for(SqlStorageIterator<FormRecord> i = formRecordStorge.iterate(); i.hasNext() ;) {
//			
//		}
		
		parser = new DataModelPullParser(stream, factory);
		parser.parse();
		
		//Return the sync token ID
		return factory.getSyncToken();
	}
		
	private SecretKeySpec getKeyForDevice(HttpRequestGenerator generator) throws ClientProtocolException, IOException {
		//Fetch the symetric key for this phone.
		HttpResponse response = generator.makeKeyFetchRequest(keyProvider);
		InputStream input = response.getEntity().getContent();
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		StreamsUtil.writeFromInputToOutput(input, bos);
		byte[] bytes = bos.toByteArray();
		
		try {
			JSONObject json = new JSONObject(new JSONTokener(new String(bytes)));
			
			String aesKey = json.getString("aesKeyString");
			
			byte[] encoded = Base64.decodeWebSafe(aesKey);
			SecretKeySpec spec = new SecretKeySpec(encoded, "AES");
			return spec;
		} catch(JSONException e) {
			e.printStackTrace();
		} catch (Base64DecoderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		//return generateTestKey();
	}
	
	private SecretKeySpec generateTestKey() {
		KeyGenerator generator;
		try {
			generator = KeyGenerator.getInstance("AES");
			generator.init(256, new SecureRandom(CommCareApplication._().getPhoneId().getBytes()));
			return new SecretKeySpec(generator.generateKey().getEncoded(), "AES");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
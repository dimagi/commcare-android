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
import java.util.Vector;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.SqlStorageIterator;
import org.commcare.android.models.Case;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.Base64;
import org.commcare.android.util.Base64DecoderException;
import org.commcare.android.util.CryptUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.bitcache.BitCache;
import org.commcare.android.util.bitcache.BitCacheFactory;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.xml.CommCareTransactionParserFactory;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.util.StreamsUtil;
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

	Credentials credentials;
	String server;
	String keyProvider;
	Context c;
	
	DataPullListener listener;
	
	public static final int DOWNLOAD_SUCCESS = 0;
	public static final int AUTH_FAILED = 1;
	public static final int BAD_DATA = 2;
	public static final int UNKNOWN_FAILURE = 4;
	public static final int UNREACHABLE_HOST = 8;
	
	public static final int PROGRESS_STARTED = 0;
	public static final int PROGRESS_AUTHED = 1;
	public static final int PROGRESS_DONE= 2;
	
	public DataPullTask(String username, String password, String server, String keyProvider, Context c) {
		this.server = server;
		this.keyProvider = keyProvider;
		credentials = new UsernamePasswordCredentials(username, password);
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
		credentials = null;
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
		
		if(loginNeeded) {
	    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
	    	
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

			DefaultHttpClient client = new DefaultHttpClient();
			client.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);
			
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
					byte[] wrappedKey = CryptUtil.wrapKey(spec,credentials.getPassword());
					factory.initUserParser(wrappedKey);
				} else {
					factory.initUserParser(CommCareApplication._().getSession().getLoggedInUser().getWrappedKey());
				}
					
				
				HttpResponse response = client.execute(new HttpGet(server));
				int responseCode = response.getStatusLine().getStatusCode();
				if(responseCode == 401) {
					return AUTH_FAILED;
				}
				
				if(responseCode >= 200 && responseCode < 300) {
					
					this.publishProgress(PROGRESS_AUTHED,0);
					
					if(loginNeeded) {
						//This is necessary (currently) to make sure that data
						//is encoded. Probably a better way to do this.
						CommCareApplication._().logIn(spec.getEncoded(), null);
					}
					
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
						readInput(cacheIn, factory);
							
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
					} finally {
						//destroy temp file
						cache.release();
					}
				}
				
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnknownHostException e) {
				this.publishProgress(PROGRESS_DONE);
				return UNREACHABLE_HOST;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}catch (SessionUnavailableException sue) {
				//TODO: Keys were lost somehow.
				sue.printStackTrace();
			}
			this.publishProgress(PROGRESS_DONE);
			return UNKNOWN_FAILURE;
			
	}
	
	private void readInput(InputStream stream, CommCareTransactionParserFactory factory) throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException, SessionUnavailableException{
		DataModelPullParser parser;
		
		//Go collect existing case data models that we should skip.
		final Vector<String> existingCases = new Vector<String>();
		SqlIndexedStorageUtility<Case> caseStorage = CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class);
		for(SqlStorageIterator<Case> i = caseStorage.iterate(); i.hasNext();) {
			caseStorage.getMetaDataFieldForRecord(i.nextID(), Case.META_CASE_ID);
		}
		long time = new Date().getTime();
		for(Case c : caseStorage) {
			existingCases.add(c.getCaseId());
		}
		System.out.println("Caching existing cases took: " + (new Date().getTime() - time) + "ms");
		
		factory.initCaseParser(existingCases);
		
		Hashtable<String,String> formNamespaces = new Hashtable<String, String>(); 
		
		for(String xmlns : CommCareApplication._().getCommCarePlatform().getInstalledForms()) {
			formNamespaces.put(xmlns, CommCareApplication._().getCommCarePlatform().getFormPath(xmlns));
		}
		factory.initFormInstanceParser(formNamespaces);
		
//		SqlIndexedStorageUtility<FormRecord> formRecordStorge = CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
//
//		for(SqlStorageIterator<FormRecord> i = formRecordStorge.iterate(); i.hasNext() ;) {
//			
//		}
		
		parser = new DataModelPullParser(stream, factory);
		parser.parse();
	}
		
	private SecretKeySpec getKeyForDevice() throws ClientProtocolException, IOException {
		DefaultHttpClient client = new DefaultHttpClient();
		client.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);
		//Fetch the symetric key for this phone.
		HttpGet get = new HttpGet(keyProvider);
		get.addHeader("x-openrosa-deviceid", CommCareApplication._().getPhoneId());
		HttpResponse response = client.execute(get);
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
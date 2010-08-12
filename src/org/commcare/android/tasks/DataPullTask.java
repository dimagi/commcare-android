/**
 * 
 */
package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Vector;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
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
import org.commcare.android.models.Case;
import org.commcare.android.util.Base64;
import org.commcare.android.util.Base64DecoderException;
import org.commcare.android.util.CryptUtil;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.UserXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.util.StreamUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.os.AsyncTask;

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
	
	public static final int PROGRESS_NONE = 0;
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
			listener.progressUpdate(values[0]);
		}
	}

	protected Integer doInBackground(Void... params) {

			DefaultHttpClient client = new DefaultHttpClient();
			client.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);
			
			try {
				SecretKeySpec spec = getKeyForDevice();
				if(spec == null) {
					this.publishProgress(PROGRESS_DONE);
					return UNKNOWN_FAILURE;
				}
				
				//This is necessary (currently) to make sure that data
				//is encoded. Probably a better way to do this.
				CommCareApplication._().logIn(spec.getEncoded());
				
				HttpResponse response = client.execute(new HttpGet(server));
				int responseCode = response.getStatusLine().getStatusCode();
				if(responseCode == 401) {
					return AUTH_FAILED;
				}
				this.publishProgress(PROGRESS_AUTHED);
				
				if(responseCode >= 200 && responseCode < 300) {
					
					try {
						readInput(response.getEntity().getContent(), spec);
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
					} 
				}
				
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			this.publishProgress(PROGRESS_DONE);
			return UNKNOWN_FAILURE;
			
	}
	
	private void readInput(InputStream stream, SecretKeySpec key) throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
		DataModelPullParser parser;
		final byte[] wrappedKey = CryptUtil.wrapKey(key,credentials.getPassword());
		
		//Go collect existing case data models that we should skip.
		final Vector<String> existingCases = new Vector<String>();
		SqlIndexedStorageUtility<Case> caseStorage = CommCareApplication._().getStorage(Case.STORAGE_KEY, Case.class);
		for(Case c : caseStorage) {
			existingCases.add(c.getCaseId());
		}
		
		
		parser = new DataModelPullParser(stream, new TransactionParserFactory() {
			
			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(name.toLowerCase().equals("case")) {
					return new CaseXmlParser(parser, c, existingCases);
				} else if(name.toLowerCase().equals("registration")) {
					return new UserXmlParser(parser, c, wrappedKey);
				}
				return null;
			}
			
		});
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
		StreamUtil.transfer(input, bos);
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
	
	private SecretKey generateTestKey() {
		CommCareApplication._().getPhoneId();
		KeyGenerator generator;
		try {
			generator = KeyGenerator.getInstance("AES");
			generator.init(256, new SecureRandom(CommCareApplication._().getPhoneId().getBytes()));
			return generator.generateKey();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
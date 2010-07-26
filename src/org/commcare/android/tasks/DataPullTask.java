/**
 * 
 */
package org.commcare.android.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.util.CryptUtil;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.UserXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
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
	Context c;
	
	DataPullListener listener;
	
	public static final int DOWNLOAD_SUCCESS = 0;
	public static final int AUTH_FAILED = 1;
	public static final int BAD_DATA = 2;
	public static final int UNKNOWN_FAILURE = 4;
	
	public static final int PROGRESS_NONE = 0;
	public static final int PROGRESS_AUTHED = 1;
	public static final int PROGRESS_DONE= 2;
	
	public DataPullTask(String username, String password, String server, Context c) {
		this.server = server;
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
				SecretKey key = getKeyForDevice(client);
				
				//This is necessary (currently) to make sure that data
				//is encoded. Probably a better way to do this.
				CommCareApplication._().logIn(key.getEncoded());
				
				HttpResponse response = client.execute(new HttpGet(server));
				int responseCode = response.getStatusLine().getStatusCode();
				if(responseCode == 401) {
					return AUTH_FAILED;
				}
				this.publishProgress(PROGRESS_AUTHED);
				
				if(responseCode >= 200 && responseCode < 300) {
					
					try {
						readInput(response.getEntity().getContent(), key);
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
	
	private void readInput(InputStream stream, SecretKey key) throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
		DataModelPullParser parser;
		final byte[] wrappedKey = CryptUtil.wrapKey(key,credentials.getPassword());
			parser = new DataModelPullParser(stream, new TransactionParserFactory() {
				
				public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
					if(name.toLowerCase().equals("case")) {
						return new CaseXmlParser(parser, c);
					} else if(name.toLowerCase().equals("registration")) {
						return new UserXmlParser(parser, c, wrappedKey);
					}
					return null;
				}
				
			});
			parser.parse();

	}
		
	private SecretKey getKeyForDevice(DefaultHttpClient client) throws ClientProtocolException, IOException {
		//Fetch the symetric key for this phone.
//		HttpGet get = new HttpGet(server);
//		get.addHeader("deviceid", CommCareApplication._().getPhoneId());
//		client.execute(get);
		
		return generateTestKey();
	}
	
	private SecretKey generateTestKey() {
		CommCareApplication._().getPhoneId();
		//SecretKeyFactory factory = SecretKeyFactory.getInstance("AES");
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
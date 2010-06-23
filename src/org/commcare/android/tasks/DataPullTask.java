/**
 * 
 */
package org.commcare.android.tasks;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.UserXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
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
		
		try {
			Reference r = ReferenceManager._().DeriveReference("jr://file/commcare/data.xml");
			this.publishProgress(PROGRESS_AUTHED);
			readInput(r.getStream());
			this.publishProgress(PROGRESS_DONE);
			return DOWNLOAD_SUCCESS;
		} catch(Exception e) {
			this.publishProgress(PROGRESS_DONE);
			return UNKNOWN_FAILURE;
		}
					/**
		
		

			DefaultHttpClient client = new DefaultHttpClient();
			client.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);
			
			try {
				HttpResponse response = client.execute(new HttpGet(server));
				int responseCode = response.getStatusLine().getStatusCode();
				if(responseCode == 401) {
					return AUTH_FAILED;
				}
				this.publishProgress(PROGRESS_AUTHED);
				
				if(responseCode >= 200 && responseCode < 300) {
					
					try {
						readInput(response.getEntity().getContent());
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
			**/
	}
	
	private void readInput(InputStream stream) throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
		DataModelPullParser parser;
			parser = new DataModelPullParser(stream, new TransactionParserFactory() {
				
				public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
					if(name.toLowerCase().equals("case")) {
						return new CaseXmlParser(parser, c);
					} else if(name.toLowerCase().equals("registration")) {
						return new UserXmlParser(parser, c);
					}
					return null;
				}
				
			});
			parser.parse();

	}
		

}
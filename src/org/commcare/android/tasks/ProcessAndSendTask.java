/**
 * 
 */
package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.commcare.android.activities.CommCareHomeActivity;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.mime.EncryptedFileBody;
import org.commcare.android.models.ACase;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.SessionStateDescriptor;
import org.commcare.android.util.AndroidSessionWrapper;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.suite.model.Profile;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * @author ctsims
 *
 */
public class ProcessAndSendTask extends AsyncTask<FormRecord, Integer, Integer> {

	Context c;
	String url;
	Integer[] results;
	
	public static final int FULL_SUCCESS = 0;
	public static final int PARTIAL_SUCCESS = 1;
	public static final int FAILURE = 2;
	public static final int TRANSPORT_FAILURE = 4;
	
	ProcessAndSendListener listener;
	SqlIndexedStorageUtility<FormRecord> storage;
	
	private static long MAX_BYTES = 1048576-1024; // 1MB less 1KB overhead
	
	public ProcessAndSendTask(Context c, String url) throws SessionUnavailableException{
		this.c = c;
		this.url = url;
		storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	protected Integer doInBackground(FormRecord... records) {
		results = new Integer[records.length];
		for(int i = 0; i < records.length ; ++i ) {
			//Assume failure
			results[i] = FAILURE;
		}
		Vector<Exception> thrownwhileprocessing = new Vector<Exception>();
		for(int i = 0 ; i < records.length ; ++i) {
			
			FormRecord record = records[i];
			try{
				
				//If the form is complete, but unprocessed, process it.
				if(FormRecord.STATUS_COMPLETE.equals(record.getStatus())) {
					try {
						record = process(record);
					} catch (InvalidStructureException e) {
						thrownwhileprocessing.add(e);
						new FormRecordCleanupTask(c).wipeRecord(record);
						continue;
					} catch (XmlPullParserException e) {
						thrownwhileprocessing.add(e);
						new FormRecordCleanupTask(c).wipeRecord(record);
						continue;
					} catch (UnfullfilledRequirementsException e) {
						thrownwhileprocessing.add(e);
						new FormRecordCleanupTask(c).wipeRecord(record);
						continue;
					}
				}
				
				//If it's unsent, go ahead and send it
				
				if(FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
					File folder;
					try {
						folder = new File(record.getPath()).getCanonicalFile().getParentFile();
					} catch (IOException e) {
						thrownwhileprocessing.add(e);
						new FormRecordCleanupTask(c).wipeRecord(record);
						continue;
					}
					
					//Good!
					//Time to Send!
					try {
						results[i] = sendInstance(folder, new SecretKeySpec(record.getAesKey(), "AES"));
					} catch (FileNotFoundException e) {
						thrownwhileprocessing.add(e);
						new FormRecordCleanupTask(c).wipeRecord(record);
						continue;
					}
					
			        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
					//Check for success
					if(results[i].intValue() == FULL_SUCCESS) {
						//Only delete if this device isn't set up to review.
					    if(p == null || !p.isFeatureActive(Profile.FEATURE_REVIEW)) {
	                    	new FormRecordCleanupTask(c).wipeRecord(record);
						} else {
							//Otherwise save and move appropriately
							moveRecord(record, CommCareApplication._().fsPath(GlobalConstants.FILE_CC_STORED), FormRecord.STATUS_SAVED);
						}
			        }
				}
			}   catch (IOException e) {
				thrownwhileprocessing.add(e);
				new FormRecordCleanupTask(c).wipeRecord(record);
				continue;
			}  catch (StorageFullException e) {
				new FormRecordCleanupTask(c).wipeRecord(record);
				throw new RuntimeException(e);
			}
		}
		
		int result = 0;
		for(int i = 0 ; i < records.length ; ++ i) {
			if(results[i] > result) {
				result = results[i];
			}
		}
		
		if(thrownwhileprocessing.size() > 0) {
			//although some may have succeeded, we need to know that we failed, so send something to the server;
			ExceptionReportTask ert = new ExceptionReportTask();
			ert.execute(thrownwhileprocessing.toArray(new Exception[0]));
		}
		return result;
	}
	
	private FormRecord process(FormRecord record) throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException, StorageFullException {
		String form = record.getPath();
		
		DataModelPullParser parser;
		File f = new File(form);

		InputStream is = new CipherInputStream(new FileInputStream(f), getDecryptCipher(new SecretKeySpec(record.getAesKey(), "AES")));
		parser = new DataModelPullParser(is, new TransactionParserFactory() {
			
			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(name.toLowerCase().equals("case")) {
					return new AndroidCaseXmlParser(parser, CommCareApplication._().getStorage(ACase.STORAGE_KEY, ACase.class));
				} 
				return null;
			}
			
		});
		
		parser.parse();
		is.close();
		
		return moveRecord(record, CommCareApplication._().fsPath(GlobalConstants.FILE_CC_PROCESSED), FormRecord.STATUS_UNSENT);
	}
	
	private FormRecord moveRecord(FormRecord record, String newPath, String newStatus) throws IOException, StorageFullException{
		String form = record.getPath();
		File f = new File(form);
		//Ok, file's all parsed. Move the instance folder to be ready for sending.
		File folder = f.getCanonicalFile().getParentFile();

		String folderName = folder.getName();
		File newFolder = new File(newPath + folderName);
		if(folder.renameTo(newFolder)) {
			String newFormPath = newFolder.getAbsolutePath() + File.separator + f.getName();
			if(!new File(newFormPath).exists()) {
				throw new IOException("Couldn't find processed instance");
			}
			//update the records to show that the form has been processed and is ready to be sent;
			record = record.updateStatus(newFormPath, newStatus);
			storage.write(record);
			return record;
		} else {
			throw new IOException("Couldn't move processed files");
		}
	}
	
	private static Cipher getDecryptCipher(SecretKeySpec key) {
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, key);
			return cipher;
			//TODO: Something smart here;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	
	private static Cipher getDecryptCipher(byte[] key) {
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
			return cipher;
			//TODO: Something smart here;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public void setListener(ProcessAndSendListener listener) {
		this.listener = listener;
	}

	@Override
	protected void onPostExecute(Integer result) {
		if(listener != null) {
			if(result == null) {
				result = FAILURE;
			}
			int successes = 0;
			for(Integer formResult : results) {
				if(formResult != null && FULL_SUCCESS == formResult.intValue()) {
					successes ++;
				}
			}
			listener.processAndSendFinished(result, successes);
		}
		//These will never get Zero'd otherwise
		c = null;
		url = null;
		results = null;
	}
	
	private int sendInstance(File folder, SecretKeySpec key) throws FileNotFoundException {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, GlobalConstants.CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, GlobalConstants.CONNECTION_TIMEOUT);
        HttpClientParams.setRedirecting(params, false);

        // setup client
        DefaultHttpClient httpclient = new DefaultHttpClient(params);
        HttpPost httppost = new HttpPost(url);
        
        String t = "p+a+s";
        
        File[] files = folder.listFiles();
        if (files == null) {
            Log.e(t, "no files to upload");
            cancel(true);
        }
        
        // mime post
        MultipartEntity entity = new MultipartEntity();
        
        for (int j = 0; j < files.length; j++) {
            File f = files[j];
            ContentBody fb;
            if (f.getName().endsWith(".xml")) {
            	
            	//fb = new InputStreamBody(new CipherInputStream(new FileInputStream(f), getDecryptCipher(aesKey)), "text/xml", f.getName());
            	fb = new EncryptedFileBody(f, getDecryptCipher(key), "text/xml");
                entity.addPart("xml_submission_file", fb);
                
                //fb = new FileBody(f, "text/xml");
                //Don't know if we can ask for the content length on the input stream, so skip it.
//                if (fb.getContentLength() <= MAX_BYTES) {
//                    Log.i(t, "added xml file " + f.getName());
//                } else {
//                    Log.i(t, "file " + f.getName() + " is too big");
//                }
            } else if (f.getName().endsWith(".jpg")) {
                fb = new FileBody(f, "image/jpeg");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added image file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else if (f.getName().endsWith(".3gpp")) {
                fb = new FileBody(f, "audio/3gpp");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added audio file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else if (f.getName().endsWith(".3gp")) {
                fb = new FileBody(f, "video/3gpp");
                if (fb.getContentLength() <= MAX_BYTES) {
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added video file " + f.getName());
                } else {
                    Log.i(t, "file " + f.getName() + " is too big");
                }
            } else {
                Log.w(t, "unsupported file type, not adding file: " + f.getName());
            }
        }
        httppost.setEntity(entity);

        // prepare response and return uploaded
        HttpResponse response = null;
        try {
            response = httpclient.execute(httppost);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        } catch (IOException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return TRANSPORT_FAILURE;
        }

        String serverLocation = null;
        Header[] h = response.getHeaders("Location");
        if (h != null && h.length > 0) {
            serverLocation = h[0].getValue();
        } else {
            // something should be done here...
            Log.e(t, "Location header was absent");
        }
        int responseCode = response.getStatusLine().getStatusCode();
        Log.e(t, "Response code:" + responseCode);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        try {
        	AndroidStreamUtil.writeFromInputToOutput(response.getEntity().getContent(), bos);
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        System.out.println(new String(bos.toByteArray()));
        

        if(responseCode >= 200 && responseCode < 300) {
            return FULL_SUCCESS;
        } else {
        	return FAILURE;
        }
	}

}

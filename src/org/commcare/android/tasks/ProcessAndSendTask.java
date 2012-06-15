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
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.io.FormSubmissionEntity;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.mime.EncryptedFileBody;
import org.commcare.android.models.ACase;
import org.commcare.android.models.FormRecord;
import org.commcare.android.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.HttpRequestGenerator;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.suite.model.Profile;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentValues;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * @author ctsims
 *
 */
public class ProcessAndSendTask extends AsyncTask<FormRecord, Long, Integer> implements FormSubmissionListener {

	Context c;
	String url;
	Long[] results;
	
	public static final long FULL_SUCCESS = 0;
	public static final long PARTIAL_SUCCESS = 1;
	public static final long FAILURE = 2;
	public static final long TRANSPORT_FAILURE = 4;
	public static final long PROGRESS_ALL_PROCESSED = 8;
	
	public static final long SUBMISSION_BEGIN = 16;
	public static final long SUBMISSION_START = 32;
	public static final long SUBMISSION_NOTIFY = 64;
	public static final long SUBMISSION_DONE = 128;
	
	public static final long PROGRESS_LOGGED_OUT = 256;
	
	ProcessTaskListener listener;
	FormSubmissionListener formSubmissionListener;
	CommCarePlatform platform;
	
	SqlIndexedStorageUtility<FormRecord> storage;
	
	static Queue<ProcessAndSendTask> processTasks = new LinkedList<ProcessAndSendTask>();
	
	private static long MAX_BYTES = (5 * 1048576)-1024; // 5MB less 1KB overhead
	
	public ProcessAndSendTask(Context c, CommCarePlatform platform, String url) throws SessionUnavailableException{
		this.c = c;
		this.url = url;
		storage =  CommCareApplication._().getStorage(FormRecord.STORAGE_KEY, FormRecord.class);
		platform = this.platform;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	protected Integer doInBackground(FormRecord... records) {
		try { 
		results = new Long[records.length];
		for(int i = 0; i < records.length ; ++i ) {
			//Assume failure
			results[i] = FAILURE;
		}
		Vector<Exception> thrownwhileprocessing = new Vector<Exception>();
		
		//The first thing we need to do is make sure everything is processed,
		//we can't actually proceed before that.
		
		for(int i = 0 ; i < records.length ; ++i) {
			FormRecord record = records[i];

			//If the form is complete, but unprocessed, process it.
			if(FormRecord.STATUS_COMPLETE.equals(record.getStatus())) {
				try {
					records[i] = process(record);
				} catch (InvalidStructureException e) {
					thrownwhileprocessing.add(e);
					new FormRecordCleanupTask(c, platform).wipeRecord(record);
					continue;
				} catch (XmlPullParserException e) {
					thrownwhileprocessing.add(e);
					new FormRecordCleanupTask(c, platform).wipeRecord(record);
					continue;
				} catch (UnfullfilledRequirementsException e) {
					thrownwhileprocessing.add(e);
					new FormRecordCleanupTask(c, platform).wipeRecord(record);
					continue;
				}  catch (StorageFullException e) {
					new FormRecordCleanupTask(c, platform).wipeRecord(record);
					throw new RuntimeException(e);
				}   catch (IOException e) {
					thrownwhileprocessing.add(e);
					new FormRecordCleanupTask(c, platform).wipeRecord(record);
					continue;
				} 
			}
		}
		
		this.publishProgress(PROGRESS_ALL_PROCESSED);
		
		//Put us on the queue!
		synchronized(processTasks) {
			processTasks.add(this);
		}
		
		boolean proceed = false;
		while(!proceed) {
			//TODO: Terrible?
			
			//See if it's our turn to go
			synchronized(processTasks) {
				//Are we at the head of the queue?
				ProcessAndSendTask head = processTasks.peek();
				if(processTasks.peek() == this) {
					proceed = true;
					break;
				}
				//Otherwise, is the head of the queue busted?
				if(head.getStatus() != AsyncTask.Status.RUNNING) {
					//If so, get rid of it
					processTasks.remove(head);
				}
			}
			//If it's not yet quite our turn, take a nap
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
		//Ok, all forms are now processed. Time to focus on sending
		if(formSubmissionListener != null) {
			formSubmissionListener.beginSubmissionProcess(records.length);
		}
		
		for(int i = 0 ; i < records.length ; ++i) {
			FormRecord record = records[i];
			try{
				//If it's unsent, go ahead and send it
				if(FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
					File folder;
					try {
						folder = new File(record.getPath(c)).getCanonicalFile().getParentFile();
					} catch (IOException e) {
						thrownwhileprocessing.add(e);
						new FormRecordCleanupTask(c, platform).wipeRecord(record);
						continue;
					}
					
					//Good!
					//Time to Send!
					try {
						results[i] = sendInstance(i, folder, new SecretKeySpec(record.getAesKey(), "AES"));
					} catch (FileNotFoundException e) {
						thrownwhileprocessing.add(e);
						new FormRecordCleanupTask(c, platform).wipeRecord(record);
						continue;
					}
					
			        Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
					//Check for success
					if(results[i].intValue() == FULL_SUCCESS) {
						//Only delete if this device isn't set up to review.
					    if(p == null || !p.isFeatureActive(Profile.FEATURE_REVIEW)) {
	                    	new FormRecordCleanupTask(c, platform).wipeRecord(record);
						} else {
							//Otherwise save and move appropriately
							moveRecord(record, CommCareApplication._().fsPath(GlobalConstants.FILE_CC_STORED), FormRecord.STATUS_SAVED);
						}
			        }
				}
			}   catch (IOException e) {
				thrownwhileprocessing.add(e);
				new FormRecordCleanupTask(c, platform).wipeRecord(record);
				continue;
			}  catch (StorageFullException e) {
				new FormRecordCleanupTask(c, platform).wipeRecord(record);
				throw new RuntimeException(e);
			}
		}
		
		long result = 0;
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
		this.endSubmissionProcess();
		synchronized(processTasks) {
			processTasks.remove(this);
		}
		
		return (int)result;
		} catch(SessionUnavailableException sue) {
			return (int)PROGRESS_LOGGED_OUT;
		}
	}
	
	public static int pending() {
		synchronized(processTasks) {
			return processTasks.size();
		}
	}
	
	private FormRecord process(FormRecord record) throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException, StorageFullException {
		String form = record.getPath(c);
		
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
			
		}, true);
		
		parser.parse();
		is.close();
		
		return moveRecord(record, CommCareApplication._().fsPath(GlobalConstants.FILE_CC_PROCESSED), FormRecord.STATUS_UNSENT);
	}
	
	private FormRecord moveRecord(FormRecord record, String newPath, String newStatus) throws IOException, StorageFullException{
		String form = record.getPath(c);
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
			record = record.updateStatus(record.getInstanceURI().toString(), newStatus);
			storage.write(record);
			
			//Update the Instance Record
			ContentValues values = new ContentValues();
			values.put(InstanceColumns.INSTANCE_FILE_PATH, newFormPath);
			c.getContentResolver().update(record.getInstanceURI(), values,null, null);
			
			return record;
		} else {
			throw new IOException("Couldn't move processed files");
		}
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	protected void onProgressUpdate(Long... values) {
		super.onProgressUpdate(values);
		
		if(values.length == 1 && values[0] == PROGRESS_ALL_PROCESSED) {
			listener.processTaskAllProcessed();
		}
		
		if(values.length > 0 ) {
			if(formSubmissionListener != null) {
				//Parcel updates out
				if(values[0] == SUBMISSION_BEGIN) {
					formSubmissionListener.beginSubmissionProcess(values[1].intValue());
				} else if(values[0] == SUBMISSION_START) {
					int item = values[1].intValue();
					long size = values[2];
					formSubmissionListener.startSubmission(item, size);
				} else if(values[0] == SUBMISSION_NOTIFY) {
					int item = values[1].intValue();
					long progress = values[2];
					formSubmissionListener.notifyProgress(item, progress);
				} else if(values[0] == SUBMISSION_DONE) {
					formSubmissionListener.endSubmissionProcess();
				}
			}
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
	
	public void setListeners(ProcessTaskListener listener, FormSubmissionListener submissionListener) {
		this.listener = listener;
		this.formSubmissionListener = submissionListener;
	}

	@Override
	protected void onPostExecute(Integer result) {
		if(listener != null) {
			if(result == null) {
				result = (int)FAILURE;
			}
			int successes = 0;
			for(Long formResult : results) {
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
	
	private static final String[] SUPPORTED_FILE_EXTS = {".xml", ".jpg", ".3gpp", ".3gp"};
	
	private long sendInstance(int submissionNumber, File folder, SecretKeySpec key) throws FileNotFoundException {
		
        File[] files = folder.listFiles();

        //If we're listening, figure out how much (roughly) we have to send
		long bytes = 0;
        for (int j = 0; j < files.length; j++) {
        	//Make sure we'll be sending it
        	boolean supported = false;
        	for(String ext : SUPPORTED_FILE_EXTS) {
        		if(files[j].getName().endsWith(ext)) {
        			supported = true;
        			break;
        		}
        	}
        	if(!supported) { continue;}
        	
        	bytes += files[j].length();
        	System.out.println("Added file: " + files[j].getName() +". Bytes to send: " + bytes);
        }
		this.startSubmission(submissionNumber, bytes);
		
		HttpRequestGenerator generator = new HttpRequestGenerator(CommCareApplication._().getSession().getLoggedInUser());
        
        String t = "p+a+s";
        
        if (files == null) {
            Log.e(t, "no files to upload");
            cancel(true);
        }
        
        // mime post
        MultipartEntity entity = new FormSubmissionEntity(this, submissionNumber);
        
        for (int j = 0; j < files.length; j++) {
            File f = files[j];
            ContentBody fb;
            
            //TODO: Match this with some reasonable library, rather than silly file lines
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
        // prepare response and return uploaded
        HttpResponse response = null;
        try {
        	response = generator.postData(url, entity);
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
	
	
	//Wrappers for the internal stuff
	public void beginSubmissionProcess(int totalItems) {
		this.publishProgress(SUBMISSION_BEGIN, (long)totalItems);
	}

	public void startSubmission(int itemNumber, long length) {
		// TODO Auto-generated method stub
		this.publishProgress(SUBMISSION_START, (long)itemNumber, length);
	}

	public void notifyProgress(int itemNumber, long progress) {
		this.publishProgress(SUBMISSION_NOTIFY, (long)itemNumber, progress);
	}

	public void endSubmissionProcess() {
		this.publishProgress(SUBMISSION_DONE);
	}

}

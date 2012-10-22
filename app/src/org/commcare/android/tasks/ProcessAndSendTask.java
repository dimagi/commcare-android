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
import java.io.PrintStream;
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
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.io.DataSubmissionEntity;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.mime.EncryptedFileBody;
import org.commcare.android.models.ACase;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.HttpRequestGenerator;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.suite.model.Profile;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.Logger;
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
public class ProcessAndSendTask extends AsyncTask<FormRecord, Long, Integer> implements DataSubmissionListener {

	Context c;
	String url;
	Long[] results;
	
	public enum ProcessIssues implements MessageTag {
		
		/** Logs successfully submitted **/
		BadTransactions("notification.processing.badstructure"),
		
		/** Logs saved, but not actually submitted **/
		StorageRemoved("notification.processing.nosdcard"),
		
		/** You were logged out while something was occurring **/
		LoggedOut("notification.sending.loggedout");
		
		ProcessIssues(String root) {this.root = root;}
		private final String root;
		public String getLocaleKeyBase() { return root;}
		public String getCategory() { return "processing"; }
		
	}
	
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
	public static final long PROGRESS_SDCARD_REMOVED = 512;
	
	ProcessTaskListener listener;
	DataSubmissionListener formSubmissionListener;
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
		boolean needToSendLogs = false;
		try {
		results = new Long[records.length];
		for(int i = 0; i < records.length ; ++i ) {
			//Assume failure
			results[i] = FAILURE;
		}
		//The first thing we need to do is make sure everything is processed,
		//we can't actually proceed before that.
		for(int i = 0 ; i < records.length ; ++i) {
			FormRecord record = records[i];

			//If the form is complete, but unprocessed, process it.
			if(FormRecord.STATUS_COMPLETE.equals(record.getStatus())) {
				try {
					records[i] = process(record);
				} catch (InvalidStructureException e) {
					CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
					Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to transaction data|" + getExceptionText(e));
					new FormRecordCleanupTask(c, platform).wipeRecord(record);
					needToSendLogs = true;
					continue;
				} catch (XmlPullParserException e) {
					CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
					Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to bad xml|" + getExceptionText(e));
					new FormRecordCleanupTask(c, platform).wipeRecord(record);
					needToSendLogs = true;
					continue;
				} catch (UnfullfilledRequirementsException e) {
					CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
					Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to bad requirements|" + getExceptionText(e));
					new FormRecordCleanupTask(c, platform).wipeRecord(record);
					needToSendLogs = true;
					continue;
				}  catch (StorageFullException e) {
					Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Really? Storage full?" + getExceptionText(e));
					throw new RuntimeException(e);
				} catch (FileNotFoundException e) {
					if(CommCareApplication._().isStorageAvailable()) {
						//If storage is available generally, this is a bug in the app design
						Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
						new FormRecordCleanupTask(c, platform).wipeRecord(record);
					} else {
						CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
						//Otherwise, the SD card just got removed, and we need to bail anyway.
						return (int)PROGRESS_SDCARD_REMOVED;
					}
					continue;
				}   catch (IOException e) {
					Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "IO Issues processing a form. Tentatively not removing in case they are resolvable|" + getExceptionText(e));
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
						Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Bizarre. Exception just getting the file reference. Not removing." + getExceptionText(e));
						continue;
					}
					
					//Good!
					//Time to Send!
					try {
						results[i] = sendInstance(i, folder, new SecretKeySpec(record.getAesKey(), "AES"));
					} catch (FileNotFoundException e) {
						if(CommCareApplication._().isStorageAvailable()) {
							//If storage is available generally, this is a bug in the app design
							Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
							new FormRecordCleanupTask(c, platform).wipeRecord(record);
						} else {
							//Otherwise, the SD card just got removed, and we need to bail anyway.
							CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
							break;
						}
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
							updateRecordStatus(record, FormRecord.STATUS_SAVED);
						}
			        }
				}
				
				
			} catch (StorageFullException e) {
				Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Really? Storage full?" + getExceptionText(e));
				throw new RuntimeException(e);
			} catch(SessionUnavailableException sue) {
				throw sue;
			} catch (Exception e) {
				//Just try to skip for now. Hopefully this doesn't wreck the model :/
				Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Totally Unexpected Error during form submission" + getExceptionText(e));
				continue;
			}  
		}
		
		long result = 0;
		for(int i = 0 ; i < records.length ; ++ i) {
			if(results[i] > result) {
				result = results[i];
			}
		}
		
		this.endSubmissionProcess();
		synchronized(processTasks) {
			processTasks.remove(this);
		}
		
		return (int)result;
		} catch(SessionUnavailableException sue) {
			this.cancel(false);
			return (int)PROGRESS_LOGGED_OUT;
		} finally {
			if(needToSendLogs) {
				CommCareApplication._().notifyLogsPending();
			}
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
			
		}, true, true);
		
		parser.parse();
		is.close();
		
		return updateRecordStatus(record, FormRecord.STATUS_UNSENT);
	}
	
	private FormRecord updateRecordStatus(FormRecord record, String newStatus) throws IOException, StorageFullException{
		//update the records to show that the form has been processed and is ready to be sent;
		record = record.updateStatus(record.getInstanceURI().toString(), newStatus);
		storage.write(record);
		return record;
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
	
	public void setListeners(ProcessTaskListener listener, DataSubmissionListener submissionListener) {
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
        MultipartEntity entity = new DataSubmissionEntity(this, submissionNumber);
        
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
	
	private String getExceptionText (Exception e) {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			e.printStackTrace(new PrintStream(bos));
			return new String(bos.toByteArray());
		} catch(Exception ex) {
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onCancelled()
	 */
	@Override
	protected void onCancelled() {
		super.onCancelled();
		if(this.formSubmissionListener != null) {
			formSubmissionListener.endSubmissionProcess();
		}
		CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
	}

}

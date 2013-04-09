/**
 * 
 */
package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Queue;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.HttpRequestGenerator;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
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

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;

/**
 * @author ctsims
 *
 */
public class ProcessAndDumpTask extends AsyncTask<FormRecord, Long, Integer> implements DataSubmissionListener {

	File dumpFolder;
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
	
	SqlStorage<FormRecord> storage;
	
	Context mContext;
	CommCarePlatform platform;
	
	static Queue<ProcessAndDumpTask> processTasks = new LinkedList<ProcessAndDumpTask>();
	
	public ProcessAndDumpTask(Context c, CommCarePlatform p, File dumpFolder) throws SessionUnavailableException{
		this.dumpFolder = dumpFolder;
		this.mContext = c;
		storage =  CommCareApplication._().getUserStorage(FormRecord.class);
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	protected Integer doInBackground(FormRecord... records) {
		try{
			
		results = new Long[records.length];
		for(int i = 0; i < records.length ; ++i ) {
			//Assume failure
			results[i] = FAILURE;
		}
		
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
						folder = new File(record.getPath(mContext)).getCanonicalFile().getParentFile();
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
						} else {
							//Otherwise, the SD card just got removed, and we need to bail anyway.
							CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
							break;
						}
						continue;
					}
			        
					//Check for success
					if(results[i].intValue() == FULL_SUCCESS) {
					    new FormRecordCleanupTask(mContext, platform).wipeRecord(record);
					    notifyProgress(i, results.length);
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
		} 
		catch(SessionUnavailableException sue) {
			this.cancel(false);
			return (int)PROGRESS_LOGGED_OUT;
		}
	}
	
	public static int pending() {
		synchronized(processTasks) {
			return processTasks.size();
		}
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	protected void onProgressUpdate(Long... values) {
		
		System.out.println("405 on progress update!");
		
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
	
	public void setListeners(DataSubmissionListener submissionListener) {
		System.out.println("405: listener set");
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
		dumpFolder = null;
		results = null;
	}
	
	private static final String[] SUPPORTED_FILE_EXTS = {".xml", ".jpg", ".3gpp", ".3gp"};
	
	private long sendInstance(int submissionNumber, File folder, SecretKeySpec key) throws FileNotFoundException {
		
        File[] files = folder.listFiles();
        
        File myDir = new File(dumpFolder, folder.getName());
        myDir.mkdirs();
        
        if(files == null) {
        	//make sure external storage is available to begin with.
        	String state = Environment.getExternalStorageState();
        	if (!Environment.MEDIA_MOUNTED.equals(state)) {
        		//If so, just bail as if the user had logged out.
        		throw new SessionUnavailableException("External Storage Removed");
        	} else {
        		throw new FileNotFoundException("No directory found at: " + folder.getAbsoluteFile());
        	}
        } 

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
        	System.out.println("405 Added file: " + files[j].getName() +". Bytes to send: " + bytes);
        }
		this.startSubmission(submissionNumber, bytes);
		
		final Cipher decrypter = getDecryptCipher(key);
		
		for(int j=0;j<files.length;j++){
			
			System.out.println("405: entering decrpy and copy loop");
			
			File f = files[j];
			if (f.getName().endsWith(".xml")) {
				try{
					FileUtil.copyFile(f, new File(myDir, f.getName()), decrypter, null);
				}
				catch(IOException ie){
					System.out.println("405 caught an ioexception: " + ie.getMessage());
				}
			}
			else{
				try{
					FileUtil.copyFile(f, new File(myDir, f.getName()));
				}
				catch(IOException ie){
					System.out.println("405 caught an ioexception: " + ie.getMessage());
				}
			}
		}
        return FULL_SUCCESS;
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
		System.out.println("405: processed: " + itemNumber + " out of: " + progress);
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

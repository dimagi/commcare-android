/**
 * 
 */
package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.io.DataSubmissionEntity;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.HttpRequestGenerator;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareFormDumpActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
public abstract class SendTask extends CommCareTask<FormRecord, String, Boolean, CommCareFormDumpActivity>{

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
	
	SqlStorage<FormRecord> storage;
	TextView outputTextView;
	
	static Queue<SendTask> processTasks = new LinkedList<SendTask>();
	
	private static long MAX_BYTES = (5 * 1048576)-1024; // 5MB less 1KB overhead
	
	public SendTask(Context c, CommCarePlatform platform, String url, TextView outputTextView) throws SessionUnavailableException{
		this.c = c;
		this.url = url;
		storage =  CommCareApplication._().getUserStorage(FormRecord.class);
		this.outputTextView = outputTextView;
		platform = this.platform;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	
	public static int pending() {
		synchronized(processTasks) {
			return processTasks.size();
		}
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	protected void onProgressUpdate(String... values) {
		super.onProgressUpdate(values);
/*		
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
		*/
	}
	
	public void setListeners(ProcessTaskListener listener, DataSubmissionListener submissionListener) {
		this.listener = listener;
		this.formSubmissionListener = submissionListener;
	}

	@Override
	protected void onPostExecute(Boolean result) {
		super.onPostExecute(result);
		//These will never get Zero'd otherwise
		c = null;
		url = null;
		results = null;
	}
	
	private static final String[] SUPPORTED_FILE_EXTS = {".xml", ".jpg", ".3gpp", ".3gp"};
	
	private long sendInstance(int submissionNumber, File folder) throws FileNotFoundException {
		
        File[] files = folder.listFiles();
        
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
        	System.out.println("Added file: " + files[j].getName() +". Bytes to send: " + bytes);
        }
		
		HttpRequestGenerator generator = new HttpRequestGenerator(CommCareApplication._().getSession().getLoggedInUser());
        
        String t = "p+a+s";
        
        // mime post
        MultipartEntity entity = new DataSubmissionEntity(null, submissionNumber);
        
        for (int j = 0; j < files.length; j++) {
            File f = files[j];
            ContentBody fb;

            //TODO: Match this with some reasonable library, rather than silly file lines
            if (f.getName().endsWith(".xml")) {

            	fb = new FileBody(f, "text/xml");
                entity.addPart("xml_submission_file", fb);
                
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

	@Override
	protected Boolean doTaskBackground(FormRecord... params) {
		
		publishProgress(Localization.get("bulk.form.send.start"));
		
		//get external SD folder
		
		ArrayList<String> externalMounts = FileUtil.getExternalMounts();
		String baseDir = externalMounts.get(0);
		String folderName = Localization.get("bulk.form.foldername");
		File dumpDirectory = new File( baseDir + "/" + folderName);
		
		//sanity check
		if(!(dumpDirectory.isDirectory())){
			return false;
		}
		
		File[] files = dumpDirectory.listFiles();
		int counter = 0;
		
		results = new Long [files.length];
		
		for(int i = 0; i < files.length ; ++i ) {
			//Assume failure
			results[i] = FAILURE;
		}
		
		boolean allSuccessful = true;
		
		for(int i=0;i<files.length;i++){
			
			publishProgress(Localization.get("bulk.send.dialog.progress",new String[]{""+ (i+1)}));
			
			File f = files[i];
			
			if(!(f.isDirectory())){
				return false;
			}
			try{
				results[i] = sendInstance(counter,f);
				if(results[i] == FULL_SUCCESS){
					FileUtil.deleteFile(f);
				}
				else if(results[i] == TRANSPORT_FAILURE){
					allSuccessful = false;
					publishProgress(Localization.get("bulk.send.transport.error"));
				}
				else{
					allSuccessful = false;
					publishProgress(Localization.get("bulk.send.file.error", new String[] {f.getAbsolutePath()}));
				}
				counter++;
			} catch(FileNotFoundException fe){
				publishProgress(Localization.get("bulk.send.file.error", new String[] {fe.getMessage()}));
			}
		}
		return allSuccessful;
	}
}

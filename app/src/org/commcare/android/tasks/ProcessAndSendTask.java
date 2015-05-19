package org.commcare.android.tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;

import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.User;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.logic.FormRecordProcessor;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.activities.LoginActivity;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.services.CommCareSessionService;
import org.commcare.suite.model.Profile;
import org.commcare.util.CommCarePlatform;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.StorageFullException;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.os.AsyncTask;

/**
 * @author ctsims
 *
 */
public abstract class ProcessAndSendTask<R> extends CommCareTask<FormRecord, Long, Integer, R> implements DataSubmissionListener {

    Context c;
    String url;
    Long[] results;
    
    int sendTaskId;
    
    public enum ProcessIssues implements MessageTag {
        
        /** Logs successfully submitted **/
        BadTransactions("notification.processing.badstructure"),
        
        /** Logs saved, but not actually submitted **/
        StorageRemoved("notification.processing.nosdcard"),
        
        /** You were logged out while something was occurring **/
        LoggedOut("notification.sending.loggedout", LoginActivity.NOTIFICATION_MESSAGE_LOGIN),
        
        /** Logs saved, but not actually submitted **/
        RecordQuarantined("notification.sending.quarantine");
        
        ProcessIssues(String root) {this(root, "processing");}
        ProcessIssues(String root, String category) {this.root = root;this.category = category;}
        private final String root, category;
        public String getLocaleKeyBase() { return root;}
        public String getCategory() { return category; }
        
    }
    
    public static final int PROCESSING_PHASE_ID = 8;
    public static final int SEND_PHASE_ID = 9;
    public static final long PROGRESS_ALL_PROCESSED = 8;
    
    public static final long SUBMISSION_BEGIN = 16;
    public static final long SUBMISSION_START = 32;
    public static final long SUBMISSION_NOTIFY = 64;
    public static final long SUBMISSION_DONE = 128;
    
    public static final long PROGRESS_LOGGED_OUT = 256;
    public static final long PROGRESS_SDCARD_REMOVED = 512;
    
    DataSubmissionListener formSubmissionListener;
    CommCarePlatform platform;
    private FormRecordProcessor processor;
    
    private static int SUBMISSION_ATTEMPTS = 2;
    
    
    static Queue<ProcessAndSendTask> processTasks = new LinkedList<ProcessAndSendTask>();
    
    private static long MAX_BYTES = (5 * 1048576)-1024; // 5MB less 1KB overhead
    
    public ProcessAndSendTask(Context c, String url) throws SessionUnavailableException{
        this(c, url, SEND_PHASE_ID, true);
    }
    
    public ProcessAndSendTask(Context c, String url, int sendTaskId, boolean inSyncMode) 
            throws SessionUnavailableException{
        this.c = c;
        this.url = url;
        this.sendTaskId = sendTaskId;
        this.processor = new FormRecordProcessor(c);
        if (inSyncMode) {
            this.taskId = PROCESSING_PHASE_ID;
        }
        else {
            this.taskId = -1;
        }
    }
    
    /* (non-Javadoc)
     * @see android.os.AsyncTask#doInBackground(Params[])
     */
    protected Integer doTaskBackground(FormRecord... records) {
        // Don't allow session to expire while we are syncing
        synchronized (CommCareSessionService.LOGOUT_LOCK) {
            boolean needToSendLogs = false;
            try {
                results = new Long[records.length];
                for (int i = 0; i < records.length; ++i) {
                    //Assume failure
                    results[i] = FormUploadUtil.FAILURE;
                }
                //The first thing we need to do is make sure everything is processed,
                //we can't actually proceed before that.
                for (int i = 0; i < records.length; ++i) {
                    FormRecord record = records[i];

                    //If the form is complete, but unprocessed, process it.
                    if (FormRecord.STATUS_COMPLETE.equals(record.getStatus())) {
                        try {
                            records[i] = processor.process(record);
                        } catch (InvalidStructureException e) {
                            CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
                            Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to transaction data|" + getExceptionText(e));
                            FormRecordCleanupTask.wipeRecord(c, record);
                            needToSendLogs = true;
                            continue;
                        } catch (XmlPullParserException e) {
                            CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
                            Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to bad xml|" + getExceptionText(e));
                            FormRecordCleanupTask.wipeRecord(c, record);
                            needToSendLogs = true;
                            continue;
                        } catch (UnfullfilledRequirementsException e) {
                            CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
                            Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to bad requirements|" + getExceptionText(e));
                            FormRecordCleanupTask.wipeRecord(c, record);
                            needToSendLogs = true;
                            continue;
                        } catch (FileNotFoundException e) {
                            if (CommCareApplication._().isStorageAvailable()) {
                                //If storage is available generally, this is a bug in the app design
                                Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
                                FormRecordCleanupTask.wipeRecord(c, record);
                            } else {
                                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
                                //Otherwise, the SD card just got removed, and we need to bail anyway.
                                return (int)PROGRESS_SDCARD_REMOVED;
                            }
                            continue;
                        } catch (IOException e) {
                            Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "IO Issues processing a form. Tentatively not removing in case they are resolvable|" + getExceptionText(e));
                            continue;
                        }
                    }
                }

                this.publishProgress(PROGRESS_ALL_PROCESSED);

                //Put us on the queue!
                synchronized (processTasks) {
                    processTasks.add(this);
                }

                boolean proceed = false;
                boolean needToRefresh = false;
                while (!proceed) {
                    //TODO: Terrible?

                    //See if it's our turn to go
                    synchronized (processTasks) {
                        //Are we at the head of the queue?
                        ProcessAndSendTask head = processTasks.peek();
                        if (processTasks.peek() == this) {
                            proceed = true;
                            break;
                        }
                        //Otherwise, is the head of the queue busted?
                        //*sigh*. Apparently Cancelled doesn't result in the task status being set
                        //to !Running for reasons which baffle me.
                        if (head.getStatus() != AsyncTask.Status.RUNNING || head.isCancelled()) {
                            //If so, get rid of it
                            processTasks.remove(head);
                        }
                    }
                    //If it's not yet quite our turn, take a nap
                    try {
                        needToRefresh = true;
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                if (needToRefresh) {
                    //There was another activity before this one. Refresh our models in case
                    //they were updated
                    for (int i = 0; i < records.length; ++i) {
                        int dbId = records[i].getID();
                        records[i] = processor.getRecord(dbId);
                    }
                }


                //Ok, all forms are now processed. Time to focus on sending
                if (formSubmissionListener != null) {
                    formSubmissionListener.beginSubmissionProcess(records.length);
                }

                for (int i = 0; i < records.length; ++i) {
                    //See whether we are OK to proceed based on the last form. We're now guaranteeing
                    //that forms are sent in order, so we won't proceed unless we succeed. We'll also permit
                    //proceeding if there was a local problem with a record, since we'll just move on from that
                    //processing.
                    if (i > 0 && !(results[i - 1] == FormUploadUtil.FULL_SUCCESS || results[i - 1] == FormUploadUtil.RECORD_FAILURE)) {
                        //Something went wrong with the last form, so we need to cancel this whole shebang
                        Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Cancelling submission due to network errors. " + (i - 1) + " forms succesfully sent.");
                        break;
                    }

                    FormRecord record = records[i];
                    try {
                        //If it's unsent, go ahead and send it
                        if (FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
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
                                User mUser = CommCareApplication._().getSession().getLoggedInUser();

                                int attemptsMade = 0;
                                while (attemptsMade < SUBMISSION_ATTEMPTS) {
                                    if (attemptsMade > 0) {
                                        Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Retrying submission. " + (SUBMISSION_ATTEMPTS - attemptsMade) + " attempts remain");
                                    }
                                    results[i] = FormUploadUtil.sendInstance(i, folder, new SecretKeySpec(record.getAesKey(), "AES"), url, this, mUser);
                                    if (results[i] == FormUploadUtil.FULL_SUCCESS) {
                                        break;
                                    } else {
                                        attemptsMade++;
                                    }
                                }

                                if (results[i] == FormUploadUtil.RECORD_FAILURE) {
                                    //We tried to submit multiple times and there was a local problem (not a remote problem).
                                    //This implies that something is wrong with the current record, and we need to quarantine it.
                                    processor.updateRecordStatus(record, FormRecord.STATUS_LIMBO);
                                    Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Quarantined Form Record");
                                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.RecordQuarantined), true);
                                }
                            } catch (FileNotFoundException e) {
                                if (CommCareApplication._().isStorageAvailable()) {
                                    //If storage is available generally, this is a bug in the app design
                                    Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
                                    FormRecordCleanupTask.wipeRecord(c, record);
                                } else {
                                    //Otherwise, the SD card just got removed, and we need to bail anyway.
                                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
                                    break;
                                }
                                continue;
                            }

                            Profile p = CommCareApplication._().getCommCarePlatform().getCurrentProfile();
                            //Check for success
                            if (results[i].intValue() == FormUploadUtil.FULL_SUCCESS) {
                                //Only delete if this device isn't set up to review.
                                if (p == null || !p.isFeatureActive(Profile.FEATURE_REVIEW)) {
                                    FormRecordCleanupTask.wipeRecord(c, record);
                                } else {
                                    //Otherwise save and move appropriately
                                    processor.updateRecordStatus(record, FormRecord.STATUS_SAVED);
                                }
                            }
                        } else {
                            results[i] = FormUploadUtil.FULL_SUCCESS;
                        }


                    } catch (StorageFullException e) {
                        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Really? Storage full?" + getExceptionText(e));
                        throw new RuntimeException(e);
                    } catch (SessionUnavailableException sue) {
                        throw sue;
                    } catch (Exception e) {
                        //Just try to skip for now. Hopefully this doesn't wreck the model :/
                        Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Totally Unexpected Error during form submission" + getExceptionText(e));
                        continue;
                    }
                }

                long result = 0;
                for (int i = 0; i < records.length; ++i) {
                    if (results[i] > result) {
                        result = results[i];
                    }
                }

                return (int)result;
            } catch (SessionUnavailableException sue) {
                this.cancel(false);
                return (int)PROGRESS_LOGGED_OUT;
            } finally {
                this.endSubmissionProcess();

                synchronized (processTasks) {
                    processTasks.remove(this);
                }
                if (needToSendLogs) {
                    CommCareApplication._().notifyLogsPending();
                }
            }
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
        if(values.length == 1 && values[0] == ProcessAndSendTask.PROGRESS_ALL_PROCESSED) {
            this.transitionPhase(sendTaskId);
        }
        
        super.onProgressUpdate(values);
        
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
    
    public void setListeners(DataSubmissionListener submissionListener) {
        this.formSubmissionListener = submissionListener;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTask#onPostExecute(java.lang.Object)
     */
    @Override
    protected void onPostExecute(Integer result) {
        super.onPostExecute(result);
        //These will never get Zero'd otherwise
        c = null;
        url = null;
        results = null;
    }
    
    protected int getSuccesfulSends() {
        int successes = 0;
        for(Long formResult : results) {
            if(formResult != null && FormUploadUtil.FULL_SUCCESS == formResult.intValue()) {
                successes ++;
            }
        }
        return successes; 
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

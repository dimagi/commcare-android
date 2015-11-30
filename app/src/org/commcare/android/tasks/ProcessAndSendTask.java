package org.commcare.android.tasks;

import android.content.Context;
import android.os.AsyncTask;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.models.notifications.ProcessIssues;
import org.javarosa.core.model.User;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.logic.FormRecordProcessor;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.suite.model.Profile;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.Queue;

import javax.crypto.spec.SecretKeySpec;

/**
 * @author ctsims
 */
public abstract class ProcessAndSendTask<R>
        extends CommCareTask<FormRecord, Long, Integer, R>
        implements DataSubmissionListener {

    private Context c;
    private String url;
    private Long[] results;

    private final int sendTaskId;

    public static final int PROCESSING_PHASE_ID = 8;
    public static final int SEND_PHASE_ID = 9;
    private static final long PROGRESS_ALL_PROCESSED = 8;

    private static final long SUBMISSION_BEGIN = 16;
    private static final long SUBMISSION_START = 32;
    private static final long SUBMISSION_NOTIFY = 64;
    private static final long SUBMISSION_DONE = 128;

    public static final long PROGRESS_LOGGED_OUT = 256;
    private static final long PROGRESS_SDCARD_REMOVED = 512;

    private DataSubmissionListener formSubmissionListener;
    private final FormRecordProcessor processor;

    private static final int SUBMISSION_ATTEMPTS = 2;

    private final static Queue<ProcessAndSendTask> processTasks = new LinkedList<>();

    public ProcessAndSendTask(Context c, String url) {
        this(c, url, true);
    }

    /**
     * @param inSyncMode blocks the user with a sync dialog
     */
    public ProcessAndSendTask(Context c, String url, boolean inSyncMode) {
        this.c = c;
        this.url = url;
        this.processor = new FormRecordProcessor(c);
        if (inSyncMode) {
            this.sendTaskId = SEND_PHASE_ID;
            this.taskId = PROCESSING_PHASE_ID;
        } else {
            this.sendTaskId = -1;
            this.taskId = -1;
        }
    }

    @Override
    protected Integer doTaskBackground(FormRecord... records) {
        boolean needToSendLogs = false;

        try {
            results = new Long[records.length];
            for (int i = 0; i < records.length; ++i) {
                //Assume failure
                results[i] = FormUploadUtil.FAILURE;
            }
            //The first thing we need to do is make sure everything is processed,
            //we can't actually proceed before that.
            try {
                needToSendLogs = checkFormRecordStatus(records);
            } catch (FileNotFoundException e) {
                return (int)PROGRESS_SDCARD_REMOVED;
            } catch (TaskCancelledException e) {
                return (int)FormUploadUtil.FAILURE;
            }

            this.publishProgress(PROGRESS_ALL_PROCESSED);

            //Put us on the queue!
            synchronized (processTasks) {
                processTasks.add(this);
            }

            boolean needToRefresh;
            try {
                needToRefresh = blockUntilTopOfQueue();
            } catch (TaskCancelledException e) {
                return (int)FormUploadUtil.FAILURE;
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

            sendForms(records);

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
            endSubmissionProcess();

            synchronized (processTasks) {
                processTasks.remove(this);
            }

            if (needToSendLogs) {
                CommCareApplication._().notifyLogsPending();
            }
        }
    }

    private boolean checkFormRecordStatus(FormRecord[] records)
            throws FileNotFoundException, TaskCancelledException {
        boolean needToSendLogs = false;
        for (int i = 0; i < records.length; ++i) {
            if (isCancelled()) {
                throw new TaskCancelledException();
            }
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
                } catch (XmlPullParserException e) {
                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
                    Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to bad xml|" + getExceptionText(e));
                    FormRecordCleanupTask.wipeRecord(c, record);
                    needToSendLogs = true;
                } catch (UnfullfilledRequirementsException e) {
                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
                    Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record due to bad requirements|" + getExceptionText(e));
                    FormRecordCleanupTask.wipeRecord(c, record);
                    needToSendLogs = true;
                } catch (FileNotFoundException e) {
                    if (CommCareApplication._().isStorageAvailable()) {
                        //If storage is available generally, this is a bug in the app design
                        Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
                        FormRecordCleanupTask.wipeRecord(c, record);
                    } else {
                        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
                        //Otherwise, the SD card just got removed, and we need to bail anyway.
                        throw e;
                    }
                } catch (IOException e) {
                    Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "IO Issues processing a form. Tentatively not removing in case they are resolvable|" + getExceptionText(e));
                }
            }
        }
        return needToSendLogs;
    }

    private boolean blockUntilTopOfQueue() throws TaskCancelledException {
        boolean needToRefresh = false;
        while (true) {
            //TODO: Terrible?

            //See if it's our turn to go
            synchronized (processTasks) {
                if (isCancelled()) {
                    processTasks.remove(this);
                    throw new TaskCancelledException();
                }
                //Are we at the head of the queue?
                ProcessAndSendTask head = processTasks.peek();
                if (head == this) {
                    break;
                }
                //Otherwise, is the head of the queue busted?
                //*sigh*. Apparently Cancelled doesn't result in the task status being set
                //to !Running for reasons which baffle me.
                if (head.getStatus() != AsyncTask.Status.RUNNING || head.isCancelled()) {
                    processTasks.poll();
                }
            }
            //If it's not yet quite our turn, take a nap
            try {
                needToRefresh = true;
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return needToRefresh;
    }

    private void sendForms(FormRecord[] records) throws SessionUnavailableException {
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
            } catch (SessionUnavailableException sue) {
                throw sue;
            } catch (Exception e) {
                //Just try to skip for now. Hopefully this doesn't wreck the model :/
                Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Totally Unexpected Error during form submission" + getExceptionText(e));
            }
        }
    }

    public static int pending() {
        synchronized (processTasks) {
            return processTasks.size();
        }
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        if (values.length == 1 && values[0] == ProcessAndSendTask.PROGRESS_ALL_PROCESSED) {
            this.transitionPhase(sendTaskId);
        }

        super.onProgressUpdate(values);

        if (values.length > 0) {
            if (formSubmissionListener != null) {
                //Parcel updates out
                if (values[0] == SUBMISSION_BEGIN) {
                    formSubmissionListener.beginSubmissionProcess(values[1].intValue());
                } else if (values[0] == SUBMISSION_START) {
                    int item = values[1].intValue();
                    long size = values[2];
                    formSubmissionListener.startSubmission(item, size);
                } else if (values[0] == SUBMISSION_NOTIFY) {
                    int item = values[1].intValue();
                    long progress = values[2];
                    formSubmissionListener.notifyProgress(item, progress);
                } else if (values[0] == SUBMISSION_DONE) {
                    formSubmissionListener.endSubmissionProcess();
                }
            }
        }
    }

    public void setListeners(DataSubmissionListener submissionListener) {
        this.formSubmissionListener = submissionListener;
    }

    @Override
    protected void onPostExecute(Integer result) {
        super.onPostExecute(result);

        clearState();
    }

    private void clearState() {
        c = null;
        url = null;
        results = null;
        formSubmissionListener = null;
    }

    protected int getSuccesfulSends() {
        int successes = 0;
        for (Long formResult : results) {
            if (formResult != null && FormUploadUtil.FULL_SUCCESS == formResult.intValue()) {
                successes++;
            }
        }
        return successes;
    }

    @Override
    public void beginSubmissionProcess(int totalItems) {
        this.publishProgress(SUBMISSION_BEGIN, (long)totalItems);
    }

    @Override
    public void startSubmission(int itemNumber, long length) {
        this.publishProgress(SUBMISSION_START, (long)itemNumber, length);
    }

    @Override
    public void notifyProgress(int itemNumber, long progress) {
        this.publishProgress(SUBMISSION_NOTIFY, (long)itemNumber, progress);
    }

    @Override
    public void endSubmissionProcess() {
        this.publishProgress(SUBMISSION_DONE);
    }

    private String getExceptionText(Exception e) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(bos));
            return new String(bos.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        if (this.formSubmissionListener != null) {
            formSubmissionListener.endSubmissionProcess();
        }

        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));

        clearState();
    }

    private static class TaskCancelledException extends Exception {
    }
}

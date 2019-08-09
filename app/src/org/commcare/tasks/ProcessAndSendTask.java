package org.commcare.tasks;

import android.content.Context;
import android.os.AsyncTask;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.SyncCapableCommCareActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.FormRecordProcessor;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.suite.model.Profile;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.util.LogTypes;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.QuarantineUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.crypto.spec.SecretKeySpec;

/**
 * @author ctsims
 */
public abstract class ProcessAndSendTask<R> extends CommCareTask<FormRecord, Long, FormUploadResult, R> implements DataSubmissionListener {

    private String url;
    private FormUploadResult[] results;

    private final int sendTaskId;

    public static final int PROCESSING_PHASE_ID = 8;
    public static final int SEND_PHASE_ID = 9;
    public static final int PROCESSING_PHASE_ID_NO_DIALOG = -8;
    public static final int SEND_PHASE_ID_NO_DIALOG = -9;

    public static final long PROGRESS_ALL_PROCESSED = 8;

    public static final long SUBMISSION_BEGIN = 16;
    public static final long SUBMISSION_START = 32;
    public static final long SUBMISSION_NOTIFY = 64;
    public static final long SUBMISSION_DONE = 128;

    private static final long SUBMISSION_SUCCESS = 1;
    private static final long SUBMISSION_FAIL = 0;

    private FormSubmissionProgressBarListener progressBarListener;
    private List<DataSubmissionListener> formSubmissionListeners;
    private final FormRecordProcessor processor;

    private static final int SUBMISSION_ATTEMPTS = 2;

    private static final Queue<ProcessAndSendTask> processTasks = new LinkedList<>();

    public ProcessAndSendTask(Context c, String url) {
        this(c, url, true);
    }

    /**
     * @param inSyncMode blocks the user with a sync dialog
     */
    public ProcessAndSendTask(Context c, String url, boolean inSyncMode) {
        this.url = url;
        this.processor = new FormRecordProcessor(c);
        this.formSubmissionListeners = new ArrayList<>();
        if (inSyncMode) {
            this.sendTaskId = SEND_PHASE_ID;
            this.taskId = PROCESSING_PHASE_ID;
        } else {
            this.sendTaskId = SEND_PHASE_ID_NO_DIALOG;
            this.taskId = PROCESSING_PHASE_ID_NO_DIALOG;
        }
    }

    @Override
    protected FormUploadResult doTaskBackground(FormRecord... records) {
        boolean wroteErrorToLogs = false;
        try {
            HiddenPreferences.setLastUploadAttemptTime(new Date().getTime());

            results = new FormUploadResult[records.length];
            for (int i = 0; i < records.length; ++i) {
                //Assume failure
                results[i] = FormUploadResult.FAILURE;
            }
            //The first thing we need to do is make sure everything is processed,
            //we can't actually proceed before that.
            try {
                wroteErrorToLogs = checkFormRecordStatus(records);
            } catch (FileNotFoundException e) {
                return FormUploadResult.PROGRESS_SDCARD_REMOVED;
            } catch (TaskCancelledException e) {
                return FormUploadResult.FAILURE;
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
                return FormUploadResult.FAILURE;
            }


            if (needToRefresh) {
                //There was another activity before this one. Refresh our models in case
                //they were updated
                for (int i = 0; i < records.length; ++i) {
                    int dbId = records[i].getID();
                    records[i] = processor.getRecord(dbId);
                }
            }

            // Ok, all forms are now processed. Time to focus on sending
            dispatchBeginSubmissionProcessToListeners(records.length);

            try {
                sendForms(records);
            } catch (TaskCancelledException e) {
                return FormUploadResult.FAILURE;
            }

            return FormUploadResult.getWorstResult(results);
        } catch (SessionUnavailableException sue) {
            this.cancel(false);
            return FormUploadResult.PROGRESS_LOGGED_OUT;
        } finally {
            boolean success =
                    FormUploadResult.FULL_SUCCESS.equals(FormUploadResult.getWorstResult(results));
            this.endSubmissionProcess(success);

            synchronized (processTasks) {
                processTasks.remove(this);
            }

            if (success || wroteErrorToLogs) {
                // Try to send logs if we either know we have a good connection, or know we wrote
                // an error to the logs during form submission attempt
                CommCareApplication.instance().notifyLogsPending();
            }
        }
    }

    private boolean checkFormRecordStatus(FormRecord[] records)
            throws FileNotFoundException, TaskCancelledException {
        boolean wroteErrorToLogs = false;
        processor.beginBulkSubmit();
        for (int i = 0; i < records.length; ++i) {
            if (isCancelled()) {
                throw new TaskCancelledException();
            }
            FormRecord record = records[i];

            //If the form is complete, but unprocessed, process it.
            if (FormRecord.STATUS_COMPLETE.equals(record.getStatus())) {
                SQLiteDatabase userDb =
                        CommCareApplication.instance().getUserDbHandle();
                try {
                    userDb.beginTransaction();
                    try {
                        records[i] = processor.process(record);
                        userDb.setTransactionSuccessful();
                    } finally {
                        userDb.endTransaction();
                    }
                } catch (InvalidStructureException | XmlPullParserException |
                        UnfullfilledRequirementsException e) {
                    records[i] = handleExceptionFromFormProcessing(record, e);
                    wroteErrorToLogs = true;
                } catch (FileNotFoundException e) {
                    if (CommCareApplication.instance().isStorageAvailable()) {
                        //If storage is available generally, this is a bug in the app design
                        Logger.log(LogTypes.TYPE_ERROR_DESIGN,
                                "Removing form record because file was missing|" + getExceptionText(e));
                        record.logPendingDeletion(TAG,
                                "the xml submission file associated with the record could not be found");
                        FormRecordCleanupTask.wipeRecord(record);
                        records[i] = FormRecord.StandInForDeletedRecord();
                        wroteErrorToLogs = true;
                    } else {
                        CommCareApplication.notificationManager().reportNotificationMessage(
                                NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
                        //Otherwise, the SD card just got removed, and we need to bail anyway.
                        throw e;
                    }
                } catch (IOException e) {
                    Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, "IO Issues processing a form. " +
                            "Tentatively not removing in case they are resolvable|" + getExceptionText(e));
                    wroteErrorToLogs = true;
                }
            }
        }
        processor.closeBulkSubmit();
        return wroteErrorToLogs;
    }

    private FormRecord handleExceptionFromFormProcessing(FormRecord record, Exception e) {
        String logMessage = "";
        if (e instanceof InvalidStructureException) {
            logMessage =
                    String.format("Quarantining form record with ID %s due to transaction data|",
                            record.getInstanceID());
        } else if (e instanceof XmlPullParserException) {
            logMessage =
                    String.format("Quarantining form record with ID %s due to bad xml|",
                            record.getInstanceID());
        } else if (e instanceof UnfullfilledRequirementsException) {
            logMessage =
                    String.format("Quarantining form record with ID %s due to bad requirements|",
                            record.getInstanceID());
        }
        logMessage = logMessage + getExceptionText(e);

        CommCareApplication.notificationManager().reportNotificationMessage(
                NotificationMessageFactory.message(ProcessIssues.BadTransactions), true);
        Logger.log(LogTypes.TYPE_ERROR_DESIGN, logMessage);

        return quarantineRecord(record, FormRecord.QuarantineReason_LOCAL_PROCESSING_ERROR);
    }

    private boolean blockUntilTopOfQueue() throws TaskCancelledException {
        boolean needToRefresh = false;
        while (true) {
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
                    //If so, get rid of it
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

    private void sendForms(FormRecord[] records) throws TaskCancelledException {
        for (int i = 0; i < records.length; ++i) {

            if (previousFailurePredictsFutureFailures(results, i)) {
                Logger.log(LogTypes.TYPE_WARNING_NETWORK,
                        "Cancelling submission due to network errors. " + (i - 1) + " forms successfully sent.");
                break;
            }

            if (isCancelled()) {
                Logger.log(LogTypes.TYPE_USER, "Cancelling submission due to a manual stop. " + (i - 1) + " forms succesfully sent.");
                throw new TaskCancelledException();
            }

            FormRecord record = records[i];
            try {
                if (FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
                    File folder;

                    //Good!
                    //Time to Send!
                    try {
                        try {
                            if (StringUtils.isEmpty(record.getFilePath())) {
                                throw new FileNotFoundException("File path empty for formrecord " +
                                        record.getID() + " with xmlns " + record.getFormNamespace());
                            }
                            folder = new File(record.getFilePath()).getCanonicalFile().getParentFile();
                        } catch (FileNotFoundException e) {
                            //This will put us in the same "Missing Form" handling path as below
                            throw e;
                        } catch (IOException e) {
                            // Unexpected/Unknown IO Error path from cannonical file
                            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, "Bizarre. Exception just getting the file reference. Not removing." + getExceptionText(e));
                            continue;
                        }

                        User user = CommCareApplication.instance().getSession().getLoggedInUser();
                        int attemptsMade = 0;
                        logSubmissionAttempt(record);
                        while (attemptsMade < SUBMISSION_ATTEMPTS) {
                            results[i] = FormUploadUtil.sendInstance(i, folder,
                                    new SecretKeySpec(record.getAesKey(), "AES"), url, this, user);
                            if (results[i] == FormUploadResult.FULL_SUCCESS) {
                                logSubmissionSuccess(record);
                                break;
                            } else if (results[i] == FormUploadResult.PROCESSING_FAILURE) {
                                // A processing failure indicates that there there is no point in
                                // trying that submission again immediately
                                break;
                            } else if (results[i] == FormUploadResult.RATE_LIMITED) {
                                // Don't keep retrying, the server is rate limiting submissions
                                break;
                            } else {
                                attemptsMade++;
                            }
                        }
                        if (results[i] == FormUploadResult.RECORD_FAILURE ||
                                results[i] == FormUploadResult.PROCESSING_FAILURE) {
                            quarantineRecord(record, results[i]);
                        }
                    } catch (FileNotFoundException e) {
                        if (CommCareApplication.instance().isStorageAvailable()) {
                            // If storage is available generally, this is a bug in the app design
                            // Log with multiple tags so we can track more easily
                            Logger.log(LogTypes.SOFT_ASSERT, String.format(
                                    "Removed form record with id %s because file was missing| %s",
                                    record.getInstanceID(), getExceptionText(e)));
                            Logger.log(LogTypes.TYPE_FORM_SUBMISSION, String.format(
                                    "Removed form record with id %s because file was missing| %s",
                                    record.getInstanceID(), getExceptionText(e)));
                            record.logPendingDeletion(TAG,
                                    "the xml submission file associated with the record was missing");
                            quarantineRecord(record,
                                    FormRecord.QuarantineReason_FILE_NOT_FOUND);
                            results[i] = FormUploadResult.RECORD_FAILURE;
                        } else {
                            // Otherwise, the SD card just got removed, and we need to bail anyway.
                            CommCareApplication.notificationManager().reportNotificationMessage(
                                    NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
                            break;
                        }
                        continue;
                    }

                    Profile p = CommCareApplication.instance().getCommCarePlatform().getCurrentProfile();
                    // Check for success
                    if (results[i] == FormUploadResult.FULL_SUCCESS) {
                        // Only delete if this device isn't set up to review.
                        if (p == null || !p.isFeatureActive(Profile.FEATURE_REVIEW)) {
                            FormRecordCleanupTask.wipeRecord(record);
                        } else {
                            // Otherwise save and move appropriately
                            processor.updateRecordStatus(record, FormRecord.STATUS_SAVED);
                        }
                    }
                } else if (FormRecord.STATUS_QUARANTINED.equals(record.getStatus()) ||
                        FormRecord.STATUS_JUST_DELETED.equals(record.getStatus())) {
                    // This record was either quarantined or deleted due to an error during the
                    // pre-processing phase
                    results[i] = FormUploadResult.RECORD_FAILURE;
                } else {
                    results[i] = FormUploadResult.FULL_SUCCESS;
                }
            } catch (SessionUnavailableException sue) {
                throw sue;
            } catch (Exception e) {
                //Just try to skip for now. Hopefully this doesn't wreck the model :/
                Logger.exception("Totally Unexpected Error during form submission: " + getExceptionText(e), e);
            }
        }
    }

    /**
     * @param results      the array of submission results
     * @param currentIndex - the index of the submission we are about to attempt
     * @return true if there was a failure in submitting the previous form that indicates future
     * submission attempts will also fail. (We permit proceeding if there was a local problem with
     * a specific record, or a processing error with a specific record, since that is unrelated to
     * how future submissions will fair).
     */
    private boolean previousFailurePredictsFutureFailures(FormUploadResult[] results, int currentIndex) {
        if (currentIndex > 0) {
            FormUploadResult lastResult = results[currentIndex - 1];
            return !(lastResult == FormUploadResult.FULL_SUCCESS ||
                    lastResult == FormUploadResult.RECORD_FAILURE ||
                    lastResult == FormUploadResult.PROCESSING_FAILURE);
        }
        return false;
    }

    private FormRecord quarantineRecord(FormRecord record, FormUploadResult uploadResult) {
        String reasonType =
                (uploadResult == FormUploadResult.RECORD_FAILURE) ?
                        FormRecord.QuarantineReason_RECORD_ERROR :
                        FormRecord.QuarantineReason_SERVER_PROCESSING_ERROR;
        record = processor.quarantineRecord(record, reasonType, uploadResult.getErrorMessage());
        logAndNotifyQuarantine(record);
        return record;
    }

    private FormRecord quarantineRecord(FormRecord record, String quarantineReasonType) {
        record = processor.quarantineRecord(record, quarantineReasonType);
        logAndNotifyQuarantine(record);
        return record;
    }

    private static void logAndNotifyQuarantine(FormRecord record) {
        Logger.log(LogTypes.TYPE_ERROR_STORAGE,
                String.format("Quarantining Form Record with id %s because: %s",
                        record.getInstanceID(),
                        QuarantineUtil.getQuarantineReasonDisplayString(record, true)));

        NotificationMessage m = QuarantineUtil.getQuarantineNotificationMessage(record);
        if (m != null) {
            CommCareApplication.notificationManager().reportNotificationMessage(m, true);
        }
    }

    private static void logSubmissionAttempt(FormRecord record) {
        String attemptMesssage = String.format(
                "Attempting to submit form with id %1$s and submission ordering number %2$s",
                record.getInstanceID(),
                record.getSubmissionOrderingNumber());
        Logger.log(LogTypes.TYPE_FORM_SUBMISSION, attemptMesssage);
    }

    private static void logSubmissionSuccess(FormRecord record) {
        String successMessage = String.format(
                "Successfully submitted form with id %1$s and submission ordering number %2$s",
                record.getInstanceID(),
                record.getSubmissionOrderingNumber());
        Logger.log(LogTypes.TYPE_FORM_SUBMISSION, successMessage);
    }

    public static int pending() {
        synchronized (processTasks) {
            return processTasks.size();
        }
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        if (values.length == 1 && values[0] == PROGRESS_ALL_PROCESSED) {
            this.transitionPhase(sendTaskId);
        }

        super.onProgressUpdate(values);

        if (values.length > 0) {
            if (values[0] == SUBMISSION_BEGIN) {
                dispatchBeginSubmissionProcessToListeners(values[1].intValue());
            } else if (values[0] == SUBMISSION_START) {
                int item = values[1].intValue();
                long size = values[2];
                dispatchStartSubmissionToListeners(item, size);
            } else if (values[0] == SUBMISSION_NOTIFY) {
                int item = values[1].intValue();
                long progress = values[2];
                dispatchNotifyProgressToListeners(item, progress);
            } else if (values[0] == SUBMISSION_DONE) {
                dispatchEndSubmissionProcessToListeners(values[1] == SUBMISSION_SUCCESS);
            }
        }
    }

    public void addProgressBarSubmissionListener(FormSubmissionProgressBarListener listener) {
        this.progressBarListener = listener;
        addSubmissionListener(listener);
    }

    public void addSubmissionListener(DataSubmissionListener submissionListener) {
        formSubmissionListeners.add(submissionListener);
    }

    private void dispatchBeginSubmissionProcessToListeners(int totalItems) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.beginSubmissionProcess(totalItems);
        }
    }

    private void dispatchStartSubmissionToListeners(int itemNumber, long length) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.startSubmission(itemNumber, length);
        }
    }

    private void dispatchNotifyProgressToListeners(int itemNumber, long progress) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.notifyProgress(itemNumber, progress);
        }
    }

    private void dispatchEndSubmissionProcessToListeners(boolean success) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.endSubmissionProcess(success);
        }
    }

    @Override
    protected void onPostExecute(FormUploadResult result) {
        super.onPostExecute(result);

        clearState();
    }

    private void clearState() {
        url = null;
        results = null;
    }

    protected int getSuccessfulSends() {
        int successes = 0;
        if (results != null) {
            for (FormUploadResult formResult : results) {
                if (formResult != null && FormUploadResult.FULL_SUCCESS == formResult) {
                    successes++;
                }
            }
        }
        return successes;
    }

    protected String getLabelForFormsSent() {
        int successfulSends = getSuccessfulSends();
        String label;
        switch (successfulSends) {
            case 0:
                label = Localization.get("sync.success.sent.none");
                break;
            case 1:
                label = Localization.get("sync.success.sent.singular");
                break;
            default:
                label = Localization.get("sync.success.sent",
                        new String[]{String.valueOf(successfulSends)});
        }
        return label;
    }


    //Wrappers for the internal stuff
    @Override
    public void beginSubmissionProcess(int totalItems) {
        this.publishProgress(SUBMISSION_BEGIN, (long)totalItems);
    }

    @Override
    public void startSubmission(int itemNumber, long sizeOfItem) {
        this.publishProgress(SUBMISSION_START, (long)itemNumber, sizeOfItem);
    }

    @Override
    public void notifyProgress(int itemNumber, long progress) {
        this.publishProgress(SUBMISSION_NOTIFY, (long)itemNumber, progress);
    }

    @Override
    public void endSubmissionProcess(boolean success) {
        if (success) {
            this.publishProgress(SUBMISSION_DONE, SUBMISSION_SUCCESS);
        } else {
            this.publishProgress(SUBMISSION_DONE, SUBMISSION_FAIL);
        }
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

        dispatchEndSubmissionProcessToListeners(false);

        // If cancellation happened due to logout, notify user
        try {
            CommCareApplication.instance().getSession().getLoggedInUser();
        } catch (SessionUnavailableException e) {
            CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
        }

        clearState();
    }

    @Override
    public void connect(CommCareTaskConnector<R> connector) {
        super.connect(connector);
        if (progressBarListener != null) {
            progressBarListener.attachToNewActivity(
                    (SyncCapableCommCareActivity)connector.getReceiver());
        }
    }

    private static class TaskCancelledException extends Exception {
    }
}

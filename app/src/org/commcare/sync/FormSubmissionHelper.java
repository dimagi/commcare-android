package org.commcare.sync;

import android.content.Context;
import android.content.SharedPreferences;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.R;
import org.commcare.models.FormRecordProcessor;
import org.commcare.preferences.ServerUrls;
import org.commcare.suite.model.Profile;
import org.commcare.tasks.DataSubmissionListener;
import org.commcare.tasks.FormRecordCleanupTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.QuarantineUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;
import org.javarosa.core.model.User;
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
import java.util.List;
import java.util.Queue;

import javax.crypto.spec.SecretKeySpec;

/**
 * Helper for uploading forms to the server and should be used by all upload Forms processes
 *
 * Currently utilised by ProcessAndSendTask and FormSubmissionWorker
 */
public class FormSubmissionHelper implements DataSubmissionListener {

    private static final String TAG = FormSubmissionHelper.class.getSimpleName();
    private static final String FORM_SUBMISSION_REQUEST_NAME = "form_submission_request";

    private final FormRecordProcessor mProcessor;
    private final CancellationChecker mCancellationChecker;
    private final FormSubmissionProgressListener mFormSubmissionProgressListener;
    private String mUrl;
    private FormUploadResult[] mResults;
    private static final Queue<FormSubmissionHelper> processTasks = new LinkedList<>();


    static final long PROGRESS_ALL_PROCESSED = 8;

    static final long SUBMISSION_BEGIN = 16;
    static final long SUBMISSION_START = 32;
    static final long SUBMISSION_NOTIFY = 64;
    static final long SUBMISSION_DONE = 128;

    static final long SUBMISSION_SUCCESS = 1;
    static final long SUBMISSION_FAIL = 0;

    private static final int SUBMISSION_ATTEMPTS = 2;

    /**
     * @param c                              Context
     * @param cancellationChecker            Interface to check whether the process to upload forms was cancelled
     * @param formSubmissionProgressListener Listener to be called to communicate form upload progress
     */
    FormSubmissionHelper(Context c,
                         CancellationChecker cancellationChecker,
                         FormSubmissionProgressListener formSubmissionProgressListener) {
        mProcessor = new FormRecordProcessor(c);
        mCancellationChecker = cancellationChecker;
        mFormSubmissionProgressListener = formSubmissionProgressListener;
        mUrl = getFormPostURL(c);
    }


    /**
     * Process and Uploads all unsent forms
     * This method serves as the main api for this class and should be called
     * by any instances of FormSubmissionHelper to initiate the upload for forms
     *
     * @return The result of the upload form process
     */
    FormUploadResult uploadForms() {

        FormRecord[] records = StorageUtils.getUnsentRecordsForCurrentApp(
                CommCareApplication.instance().getUserStorage(FormRecord.class));

        if (records.length == 0) {
            return FormUploadResult.FULL_SUCCESS;
        }

        boolean wroteErrorToLogs = false;
        try {
            mResults = new FormUploadResult[records.length];
            for (int i = 0; i < records.length; ++i) {
                //Assume failure
                mResults[i] = FormUploadResult.FAILURE;
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


            publishProgress(PROGRESS_ALL_PROCESSED);

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
                    records[i] = mProcessor.getRecord(dbId);
                }
            }

            // Ok, all forms are now processed. Time to focus on sending
            beginSubmissionProcess(records.length);

            try {
                sendForms(records);
            } catch (TaskCancelledException e) {
                return FormUploadResult.FAILURE;
            }

            return FormUploadResult.getWorstResult(mResults);
        } catch (SessionUnavailableException sue) {
            return FormUploadResult.PROGRESS_LOGGED_OUT;
        } finally {
            boolean success =
                    FormUploadResult.FULL_SUCCESS.equals(FormUploadResult.getWorstResult(mResults));
            endSubmissionProcess(success);

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


    private void publishProgress(Long... progress) {
        mFormSubmissionProgressListener.publishUpdateProgress(progress);
    }

    private boolean checkFormRecordStatus(FormRecord[] records)
            throws FileNotFoundException, TaskCancelledException {
        boolean wroteErrorToLogs = false;
        mProcessor.beginBulkSubmit();
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
                        records[i] = mProcessor.process(record);
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
        mProcessor.closeBulkSubmit();
        return wroteErrorToLogs;
    }

    private boolean isCancelled() {
        return mCancellationChecker.wasProcessCancelled();
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
                FormSubmissionHelper head = processTasks.peek();
                if (head == this) {
                    break;
                }
                //Otherwise, is the head of the queue busted?
                if (head.isCancelled()) {
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

            if (previousFailurePredictsFutureFailures(mResults, i)) {
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

                            if (isCancelled()) {
                                Logger.log(LogTypes.TYPE_USER, "Cancelling submission due to a manual stop. " + (i - 1) + " forms succesfully sent.");
                                throw new TaskCancelledException();
                            }

                            mResults[i] = FormUploadUtil.sendInstance(i, folder,
                                    new SecretKeySpec(record.getAesKey(), "AES"), mUrl, this, user);
                            if (mResults[i] == FormUploadResult.FULL_SUCCESS) {
                                logSubmissionSuccess(record);
                                break;
                            } else if (mResults[i] == FormUploadResult.PROCESSING_FAILURE) {
                                // A processing failure indicates that there there is no point in
                                // trying that submission again immediately
                                break;
                            } else if (mResults[i] == FormUploadResult.RATE_LIMITED) {
                                // Don't keep retrying, the server is rate limiting submissions
                                // Should we just stop submitting any form and display an error?
                                Logger.log(LogTypes.TYPE_FORM_SUBMISSION, "Failed to submit forms due to rate limit exception from server");
                                return;
                            } else if (mResults[i] == FormUploadResult.CAPTIVE_PORTAL) {
                                // User is behind a captive portal, no need to re-try.
                                break;
                            } else {
                                attemptsMade++;
                            }
                        }
                        if (mResults[i] == FormUploadResult.RECORD_FAILURE ||
                                mResults[i] == FormUploadResult.PROCESSING_FAILURE) {
                            quarantineRecord(record, mResults[i]);
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
                            mResults[i] = FormUploadResult.RECORD_FAILURE;
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
                    if (mResults[i] == FormUploadResult.FULL_SUCCESS) {
                        // Only delete if this device isn't set up to review.
                        if (p == null || !p.isFeatureActive(Profile.FEATURE_REVIEW)) {
                            FormRecordCleanupTask.wipeRecord(record);
                        } else {
                            // Otherwise save and move appropriately
                            mProcessor.updateRecordStatus(record, FormRecord.STATUS_SAVED);
                        }
                    }
                } else if (FormRecord.STATUS_QUARANTINED.equals(record.getStatus()) ||
                        FormRecord.STATUS_JUST_DELETED.equals(record.getStatus())) {
                    // This record was either quarantined or deleted due to an error during the
                    // pre-processing phase
                    mResults[i] = FormUploadResult.RECORD_FAILURE;
                } else {
                    mResults[i] = FormUploadResult.FULL_SUCCESS;
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
     * @param mResults     the array of submission mResults
     * @param currentIndex - the index of the submission we are about to attempt
     * @return true if there was a failure in submitting the previous form that indicates future
     * submission attempts will also fail. (We permit proceeding if there was a local problem with
     * a specific record, or a processing error with a specific record, since that is unrelated to
     * how future submissions will fair).
     */
    private boolean previousFailurePredictsFutureFailures(FormUploadResult[] mResults, int currentIndex) {
        if (currentIndex > 0) {
            FormUploadResult lastResult = mResults[currentIndex - 1];
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
        record = mProcessor.quarantineRecord(record, reasonType, uploadResult.getErrorMessage());
        logAndNotifyQuarantine(record);
        return record;
    }

    private FormRecord quarantineRecord(FormRecord record, String quarantineReasonType) {
        record = mProcessor.quarantineRecord(record, quarantineReasonType);
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


    //Wrappers for the internal stuff
    @Override
    public void beginSubmissionProcess(int totalItems) {
        publishProgress(SUBMISSION_BEGIN, (long)totalItems);
    }

    @Override
    public void startSubmission(int itemNumber, long sizeOfItem) {
        publishProgress(SUBMISSION_START, (long)itemNumber, sizeOfItem);
    }

    @Override
    public void notifyProgress(int itemNumber, long progress) {
        publishProgress(SUBMISSION_NOTIFY, (long)itemNumber, progress);
    }

    @Override
    public void endSubmissionProcess(boolean success) {
        if (success) {
            publishProgress(SUBMISSION_DONE, SUBMISSION_SUCCESS);
        } else {
            publishProgress(SUBMISSION_DONE, SUBMISSION_FAIL);
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

    public static int pending() {
        synchronized (processTasks) {
            return processTasks.size();
        }
    }

    int getSuccessfulSends() {
        int successes = 0;
        if (mResults != null) {
            for (FormUploadResult formResult : mResults) {
                if (FormUploadResult.FULL_SUCCESS == formResult) {
                    successes++;
                }
            }
        }
        return successes;
    }

    void cleanUp() {
        mUrl = null;
        mResults = null;
    }

    private static String getFormPostURL(final Context context) {
        SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return settings.getString(ServerUrls.PREFS_SUBMISSION_URL_KEY,
                context.getString(R.string.PostURL));
    }

    public static String getFormSubmissionRequestName() {
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        String currentUserId = CommCareApplication.instance().getCurrentUserId();
        return FORM_SUBMISSION_REQUEST_NAME + "_" + appId + "_" + currentUserId;
    }

    // parses prpgress and calls the approproate methods for form submission listeners
    void dispatchProgress(List<DataSubmissionListener> formSubmissionListeners, Long... values) {
        if (values.length > 0) {
            if (values[0] == SUBMISSION_BEGIN) {
                dispatchBeginSubmissionProcessToListeners(formSubmissionListeners, values[1].intValue());
            } else if (values[0] == SUBMISSION_START) {
                int item = values[1].intValue();
                long size = values[2];
                dispatchStartSubmissionToListeners(formSubmissionListeners, item, size);
            } else if (values[0] == SUBMISSION_NOTIFY) {
                int item = values[1].intValue();
                long progress = values[2];
                dispatchNotifyProgressToListeners(formSubmissionListeners, item, progress);
            } else if (values[0] == SUBMISSION_DONE) {
                dispatchEndSubmissionProcessToListeners(formSubmissionListeners, values[1] == SUBMISSION_SUCCESS);
            }
        }
    }

    private void dispatchBeginSubmissionProcessToListeners(List<DataSubmissionListener> formSubmissionListeners, int totalItems) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.beginSubmissionProcess(totalItems);
        }
    }

    private void dispatchStartSubmissionToListeners(List<DataSubmissionListener> formSubmissionListeners, int itemNumber, long length) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.startSubmission(itemNumber, length);
        }
    }

    private void dispatchNotifyProgressToListeners(List<DataSubmissionListener> formSubmissionListeners, int itemNumber, long progress) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.notifyProgress(itemNumber, progress);
        }
    }

    private void dispatchEndSubmissionProcessToListeners(List<DataSubmissionListener> formSubmissionListeners, boolean success) {
        for (DataSubmissionListener listener : formSubmissionListeners) {
            listener.endSubmissionProcess(success);
        }
    }


    private static class TaskCancelledException extends Exception {
    }
}

package org.commcare.android.database.app.models;

import android.database.SQLException;
import android.support.annotation.IntDef;
import android.support.annotation.StringDef;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.FormRecordProcessor;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;
import org.commcare.provider.InstanceProviderAPI;
import org.commcare.provider.ProviderUtils;
import org.commcare.tasks.FormRecordCleanupTask;
import org.commcare.util.LogTypes;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;
import javax.crypto.SecretKey;

import static org.commcare.utils.FileUtil.deleteFileOrDir;

@Table(InstanceRecord.STORAGE_KEY)
public class InstanceRecord extends Persisted {

    public static final String STORAGE_KEY = "instances";

    // These are the only things needed for an insert
    public static final String META_DISPLAY_NAME = "displayName";
    public static final String META_SUBMISSION_URI = "submissionUri";
    public static final String META_INSTANCE_FILE_PATH = "instanceFilePath";
    public static final String META_JR_FORM_ID = "jrFormId";

    // these are generated for you (but you can insert something else if you want)
    public static final String META_STATUS = "status";
    public static final String META_CAN_EDIT_WHEN_COMPLETE = "canEditWhenComplete";
    public static final String META_LAST_STATUS_CHANGE_DATE = "date";
    public static final String META_DISPLAY_SUBTEXT = "displaySubtext";

    private static final String TAG = InstanceRecord.class.getName();

    // status for instances
    public static final String STATUS_INCOMPLETE = "incomplete";
    public static final String STATUS_COMPLETE = "complete";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_SUBMISSION_FAILED = "submissionFailed";

    @StringDef({STATUS_INCOMPLETE, STATUS_COMPLETE, STATUS_SUBMITTED, STATUS_SUBMISSION_FAILED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface InstanceStatus {
    }

    // Insertion types
    public static final int INSERTION_TYPE_SESSION_LINKED = 0;
    public static final int INSERTION_TYPE_UNINDEXED_IMPORT = 1;
    public static final int INSERTION_TYPE_SANDBOX_MIGRATED = 2;

    @IntDef({INSERTION_TYPE_SESSION_LINKED, INSERTION_TYPE_UNINDEXED_IMPORT, INSERTION_TYPE_SANDBOX_MIGRATED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface InstanceInsertType {
    }

    @Persisting(1)
    @MetaField(META_DISPLAY_NAME)
    private String mDisplayName;

    @Persisting(2)
    @MetaField(META_SUBMISSION_URI)
    private String mSubmissionUri;

    @Persisting(3)
    @MetaField(META_INSTANCE_FILE_PATH)
    private String mInstanceFilePath;

    @Persisting(4)
    @MetaField(META_JR_FORM_ID)
    private String mJrFormId;

    @Persisting(5)
    @MetaField(META_STATUS)
    private String mStatus;

    @Persisting(6)
    @MetaField(META_CAN_EDIT_WHEN_COMPLETE)
    private String mCanEditWhenComplete;

    @Persisting(7)
    @MetaField(META_LAST_STATUS_CHANGE_DATE)
    private long mLastStatusChangeDate = -1;

    @Persisting(8)
    @MetaField(META_DISPLAY_SUBTEXT)
    private String mDisplaySubtext;


    public InstanceRecord(String displayName, String instancePath, @InstanceStatus String status, String canEditWhenComplete, String jrFormId, String submissionUri) {
        mDisplayName = displayName;
        mInstanceFilePath = instancePath;
        mStatus = status;
        mCanEditWhenComplete = canEditWhenComplete;
        mJrFormId = jrFormId;
        mSubmissionUri = submissionUri;
        mLastStatusChangeDate = System.currentTimeMillis();
        mDisplaySubtext = getDisplaySubtext(status);
    }

    public InstanceRecord(InstanceRecord oldInstanceRecord) {
        mDisplayName = oldInstanceRecord.mDisplayName;
        mSubmissionUri = oldInstanceRecord.mSubmissionUri;
        mInstanceFilePath = oldInstanceRecord.mInstanceFilePath;
        mJrFormId = oldInstanceRecord.mJrFormId;
        mStatus = oldInstanceRecord.mStatus;
        mCanEditWhenComplete = oldInstanceRecord.mCanEditWhenComplete;
        mLastStatusChangeDate = oldInstanceRecord.mLastStatusChangeDate;
        mDisplaySubtext = oldInstanceRecord.mDisplaySubtext;
    }

    public static InstanceRecord getInstance(int instanceId) {
        return getInstanceRecordStorage().read(instanceId);
    }

    private static SqlStorage<InstanceRecord> getInstanceRecordStorage() {
        return CommCareApplication.instance().getAppStorage(InstanceRecord.class);
    }

    public static void updateFilePath(int instanceId, String newPath) {
        SqlStorage<InstanceRecord> instanceRecordStorage = getInstanceRecordStorage();
        InstanceRecord instanceRecord = instanceRecordStorage.read(instanceId);
        instanceRecord.mInstanceFilePath = newPath;
        instanceRecordStorage.update(instanceRecord.getID(), instanceRecord);
    }

    public static boolean isInstanceComplete(String instancePath) {
        InstanceRecord instanceRecord = getInstance(instancePath);
        if (instanceRecord != null) {
            return instanceRecord.mStatus.contentEquals(STATUS_COMPLETE);
        }
        return false;
    }

    public static void updateCanEditWhenComplete(int instanceId, String canEditWhenComplete) {
        SqlStorage<InstanceRecord> instanceRecordStorage = getInstanceRecordStorage();
        InstanceRecord instanceRecord = instanceRecordStorage.read(instanceId);
        instanceRecord.mCanEditWhenComplete = canEditWhenComplete;
        instanceRecordStorage.update(instanceRecord.getID(), instanceRecord);
    }

    @Nullable
    public static InstanceRecord getInstance(String instancePath) {
        try {
            return getInstanceRecordStorage().getRecordForValue(META_INSTANCE_FILE_PATH, instancePath);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public static int getCount() {
        return getInstanceRecordStorage().getNumRecords();
    }

    public void updateStatus(@InstanceStatus String instanceStatus, String displayName, String canEditWhenComplete) {
        mDisplayName = displayName;
        mStatus = instanceStatus;
        mCanEditWhenComplete = canEditWhenComplete;

        // set the display subtext from the status value
        mDisplaySubtext = getDisplaySubtext(mStatus);
        mLastStatusChangeDate = System.currentTimeMillis();
        getInstanceRecordStorage().update(getID(), this);
        finalizeSessionLinkedInsertion();
    }

    /**
     * Register an instance with the session's form record.
     */
    private void linkToSessionFormRecord() {
        AndroidSessionWrapper currentState = CommCareApplication.instance().getCurrentSessionWrapper();
        if (getID() == -1) {
            raiseFormEntryError("Form Entry did not return a form", currentState);
            return;
        }

        boolean complete = STATUS_COMPLETE.equals(mStatus);

        FormRecord current;
        try {
            current = syncRecordToInstance(currentState.getFormRecord());
        } catch (Exception e) {
            // Something went wrong with all of the connections which should exist.
            if (currentState.getFormRecord() != null) {
                currentState.getFormRecord().logPendingDeletion(TAG,
                        "something went wrong trying to sync the record for the current session " +
                                "with the form instance");
            } else {
                Logger.log(LogTypes.TYPE_FORM_DELETION, "The current session was missing " +
                        "its form record when trying to sync with the form instance; " +
                        "attempting to delete it if it still exists in the db");
            }
            FormRecordCleanupTask.wipeRecord(currentState);

            // Notify the server of this problem (since we aren't going to crash)
            ForceCloseLogger.reportExceptionInBg(e);

            raiseFormEntryError("An error occurred: " + e.getMessage() +
                    " and your data could not be saved.", currentState);
            return;
        }

        // The form is either ready for processing, or not, depending on how it was saved
        if (complete) {
            // Form record should now be up to date now and stored correctly.

            // ctsims - App stack workflows require us to have processed _this_
            // specific form before we can move on, and that needs to be
            // synchronous. We'll go ahead and try to process just this form
            // before moving on. We'll catch any errors here and just eat them
            // (since the task will also try the process and fail if it does).
            if (FormRecord.STATUS_COMPLETE.equals(current.getStatus())) {
                SQLiteDatabase userDb = CommCareApplication.instance().getUserDbHandle();
                userDb.beginTransaction();
                try {
                    new FormRecordProcessor(CommCareApplication.instance()).process(current);
                    userDb.setTransactionSuccessful();
                } catch (InvalidStructureException e) {
                    // Record will be wiped when form entry is exited
                    Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, e.getMessage());
                    throw new IllegalStateException(e.getMessage());
                } catch (Exception e) {
                    NotificationMessage message =
                            NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.FormEntry_Save_Error,
                                    new String[]{null, null, e.getMessage()});
                    CommCareApplication.notificationManager().reportNotificationMessage(message);
                    Logger.log(LogTypes.TYPE_ERROR_WORKFLOW,
                            "Error processing form. Should be recaptured during async processing: " + e.getMessage());
                    throw new RuntimeException(e);
                } finally {
                    userDb.endTransaction();
                }
            }
        }
    }

    /**
     * Register a record with a form instance, syncing the record's details
     * with those of the instance and writing it to storage.
     *
     * @param record Attach this record with a form instance, syncing
     *               details and writing it to storage.
     * @return The updated form record, which has been written to storage.
     */
    private FormRecord syncRecordToInstance(FormRecord record)
            throws InvalidStateException {

        if (record == null) {
            throw new InvalidStateException("No form record found when trying to save form.");
        }

        // update the form record to mirror the sessions instance uri and status.
        if (InstanceProviderAPI.STATUS_COMPLETE.equals(mStatus)) {
            record = record.updateInstanceAndStatus(getID(), FormRecord.STATUS_COMPLETE);
        } else {
            record = record.updateInstanceAndStatus(getID(), FormRecord.STATUS_INCOMPLETE);
        }

        // save the updated form record
        try {
            return FormRecordCleanupTask.updateAndWriteRecord(CommCareApplication.instance(),
                    record, CommCareApplication.instance().getUserStorage(FormRecord.class));
        } catch (InvalidStructureException e1) {
            e1.printStackTrace();
            throw new InvalidStateException("Invalid data structure found while parsing form. There's something wrong with the application structure, please contact your supervisor.");
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            throw new InvalidStateException("There was a problem with the local storage and the form could not be read.");
        } catch (UnfullfilledRequirementsException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Throw and Log FormEntry-related errors
     *
     * @param loggerText   String sent to javarosa logger
     * @param currentState session to be cleared
     */
    private void raiseFormEntryError(String loggerText, AndroidSessionWrapper currentState) {
        Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, loggerText);
        currentState.reset();
        throw new RuntimeException(loggerText);
    }

    /**
     * Create display subtext for current date and time
     *
     * @param status is the status column of an instance entry
     */
    private static String getDisplaySubtext(@InstanceStatus String status) {
        String ts = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' HH:mm").format(new Date());

        if (status == null) {
            return "Added on " + ts;
        } else if (InstanceProviderAPI.STATUS_INCOMPLETE.equalsIgnoreCase(status)) {
            return "Saved on " + ts;
        } else if (InstanceProviderAPI.STATUS_COMPLETE.equalsIgnoreCase(status)) {
            return "Finalized on " + ts;
        } else if (InstanceProviderAPI.STATUS_SUBMITTED.equalsIgnoreCase(status)) {
            return "Sent on " + ts;
        } else if (InstanceProviderAPI.STATUS_SUBMISSION_FAILED.equalsIgnoreCase(status)) {
            return "Sending failed on " + ts;
        } else {
            return "Added on " + ts;
        }
    }

    public void delete() {
        String instanceDir = (new File(mInstanceFilePath)).getParent();
        deleteFileOrDir(instanceDir);
        getInstanceRecordStorage().remove(getID());
    }

    public void save(@InstanceInsertType int insertType) {
        getInstanceRecordStorage().write(this);

        switch (insertType) {
            case INSERTION_TYPE_SESSION_LINKED:
                finalizeSessionLinkedInsertion();
                break;
            case INSERTION_TYPE_UNINDEXED_IMPORT:
                finalizeUnindexedInsertion();
                break;
            case INSERTION_TYPE_SANDBOX_MIGRATED:
                break;
        }
    }

    private void finalizeSessionLinkedInsertion() {
        // If we've changed a particular form instance's status, we need to mirror the
        // change in that form's record.
        try {
            linkToSessionFormRecord();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            Logger.exception(e);
            throw new SQLException("Failed to update Instance row " + getID());
        }
    }

    private void finalizeUnindexedInsertion() {
        SecretKey key = CommCareApplication.instance().createNewSymmetricKey();
        FormRecord r = new FormRecord(getID(), FormRecord.STATUS_UNINDEXED,
                mJrFormId, key.getEncoded(), null, new Date(0), ProviderUtils.getSandboxedAppId());
        IStorageUtilityIndexed<FormRecord> formRecordstorage =
                CommCareApplication.instance().getUserStorage(FormRecord.class);
        formRecordstorage.write(r);
    }


    private static class InvalidStateException extends Exception {
        public InvalidStateException(String message) {
            super(message);
        }
    }

    public String getFilePath() {
        return mInstanceFilePath;
    }

    public void setFilePath(String filePath) {
        this.mInstanceFilePath = filePath;
    }

    public String getJrFormId() {
        return mJrFormId;
    }

    public String getStatus() {
        return mStatus;
    }

    public String getCanEditWhenComplete() {
        return mCanEditWhenComplete;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getSubmissionUri() {
        return mSubmissionUri;
    }

    public long getLastStatusChangeDate() {
        return mLastStatusChangeDate;
    }

    public String getDisplaySubText() {
        return mDisplaySubtext;
    }
}

package org.commcare.android.database.user.models;

import android.database.SQLException;

import androidx.annotation.StringDef;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.FormRecordProcessor;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.modern.models.MetaField;
import org.commcare.tasks.FormRecordCleanupTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.StringUtils;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.InvalidCasePropertyLengthException;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;
import java.util.NoSuchElementException;

/**
 * @author ctsims
 */
@Table(FormRecord.STORAGE_KEY)
public class FormRecord extends Persisted implements EncryptedModel {

    public static final String STORAGE_KEY = "FORMRECORDS";

    public static final String META_STATUS = "STATUS";
    public static final String META_UUID = "UUID";
    public static final String META_XMLNS = "XMLNS";
    public static final String META_LAST_MODIFIED = "DATE_MODIFIED";
    public static final String META_APP_ID = "APP_ID";
    public static final String META_SUBMISSION_ORDERING_NUMBER = "SUBMISSION_ORDERING_NUMBER";
    public static final String META_DISPLAY_NAME = "displayName";
    public static final String META_FILE_PATH = "instanceFilePath";


    /**
     * This form record is a stub that hasn't actually had data saved for it yet
     */
    public static final String STATUS_UNSTARTED = "unstarted";

    /**
     * This form has been saved, but has not yet been marked as completed and ready for processing
     */
    public static final String STATUS_INCOMPLETE = "incomplete";

    /**
     * User entry on this form has finished, but the form has not been processed yet
     */
    public static final String STATUS_COMPLETE = "complete";

    /**
     * The form has been processed and is ready to be sent to the server *
     */
    public static final String STATUS_UNSENT = "unsent";

    /**
     * This form has been fully processed and is being retained for viewing in the future
     */
    public static final String STATUS_SAVED = "saved";

    /**
     * This form was complete, but something blocked it from processing and it's in a damaged
     * state (a.k.a. "quarantined")
     */
    public static final String STATUS_QUARANTINED = "limbo";

    /**
     * This form has been downloaded, but not processed for metadata
     */
    public static final String STATUS_UNINDEXED = "unindexed";

    /**
     * Represents a form record that was just deleted from the db, but which we still need an
     * object representation of to reference in the short-term
     */
    public static final String STATUS_JUST_DELETED = "just-deleted";
    private static final String TAG = FormRecord.class.getName();

    @StringDef({STATUS_UNSTARTED, STATUS_INCOMPLETE, STATUS_COMPLETE, STATUS_UNSENT, STATUS_SAVED, STATUS_QUARANTINED, STATUS_UNINDEXED, STATUS_JUST_DELETED})
    @Retention(RetentionPolicy.SOURCE)
    private @interface FormRecordStatus {
    }

    public static final String QuarantineReason_LOCAL_PROCESSING_ERROR = "local-processing-error";
    public static final String QuarantineReason_SERVER_PROCESSING_ERROR = "server-processing-error";
    public static final String QuarantineReason_RECORD_ERROR = "record-error";
    public static final String QuarantineReason_MANUAL = "manual-quarantine";
    public static final String QuarantineReason_FILE_NOT_FOUND = "file-not-found";

    private static final String QUARANTINE_REASON_AND_DETAIL_SEPARATOR = "@@SEP@@";

    @Persisting(1)
    @MetaField(META_XMLNS)
    private String xmlns;

    @Persisting(2)
    @MetaField(META_STATUS)
    private String status;

    @Persisting(3)
    private byte[] aesKey;

    @Persisting(value = 4, nullable = true)
    @MetaField(META_UUID)
    private String uuid;

    @Persisting(5)
    @MetaField(META_LAST_MODIFIED)
    private Date lastModified;

    @Persisting(6)
    @MetaField(META_APP_ID)
    private String appId;

    @Persisting(value = 7, nullable = true)
    @MetaField(META_SUBMISSION_ORDERING_NUMBER)
    private String submissionOrderingNumber;

    @Persisting(value = 8, nullable = true)
    private String quarantineReason;

    // Fields added from the Instance Provider merge

    @Persisting(value = 9, nullable = true)
    @MetaField(META_DISPLAY_NAME)
    private String displayName;

    @Persisting(value = 10, nullable = true)
    @MetaField(META_FILE_PATH)
    private String filePath;

    public FormRecord() {
    }

    /**
     * Creates a record of a form entry with the provided data. Note that none
     * of the parameters can be null...
     */
    public FormRecord(@FormRecordStatus String status, String xmlns, byte[] aesKey, String uuid,
                      Date lastModified, String appId) {
        this.status = status;
        this.xmlns = xmlns;
        this.aesKey = aesKey;

        this.uuid = uuid;
        this.lastModified = lastModified;
        if (lastModified == null) {
            this.lastModified = new Date();
        }
        this.appId = appId;
    }

    public FormRecord(FormRecord oldRecord) {
        status = oldRecord.status;
        xmlns = oldRecord.xmlns;
        aesKey = oldRecord.aesKey;
        uuid = oldRecord.uuid;
        lastModified = oldRecord.lastModified;
        appId = oldRecord.appId;
        submissionOrderingNumber = oldRecord.submissionOrderingNumber;
        quarantineReason = oldRecord.quarantineReason;
        displayName = oldRecord.displayName;
        filePath = oldRecord.filePath;
        recordId = oldRecord.recordId;
    }

    /**
     * Create a copy of the current form record, with an updated status.
     */
    public FormRecord updateStatus(@FormRecordStatus String newStatus) {
        FormRecord fr = new FormRecord(this);
        fr.status = newStatus;
        return fr;
    }

    public static FormRecord StandInForDeletedRecord() {
        FormRecord r = new FormRecord();
        r.status = STATUS_JUST_DELETED;
        return r;
    }

    public byte[] getAesKey() {
        return aesKey;
    }

    public String getStatus() {
        return status;
    }

    public Date lastModified() {
        return lastModified;
    }

    public String getFormNamespace() {
        return xmlns;
    }

    @Override
    public boolean isEncrypted(String data) {
        return false;
    }

    @Override
    public boolean isBlobEncrypted() {
        return true;
    }

    public String getAppId() {
        return this.appId;
    }

    public String getInstanceID() {
        return uuid;
    }

    public int getSubmissionOrderingNumber() {
        if (submissionOrderingNumber == null) {
            return -1;
        }
        return Integer.parseInt(submissionOrderingNumber);
    }

    public void setQuarantineReason(String reasonType, String reasonDetail) {
        this.quarantineReason = reasonType;
        if (reasonDetail != null) {
            this.quarantineReason += (QUARANTINE_REASON_AND_DETAIL_SEPARATOR + reasonDetail);
        }
    }

    public String getQuarantineReasonType() {
        return (quarantineReason == null) ?
                null :
                quarantineReason.split(QUARANTINE_REASON_AND_DETAIL_SEPARATOR)[0];
    }

    public String getQuarantineReasonDetail() {
        if (quarantineReason == null) {
            return null;
        }
        String[] typeAndDetail = this.quarantineReason.split(QUARANTINE_REASON_AND_DETAIL_SEPARATOR);
        if (typeAndDetail.length == 2) {
            return typeAndDetail[1];
        } else {
            return null;
        }
    }

    /**
     * Get the file system path to the encrypted XML submission file.
     *
     * @return A string containing the location of the encrypted XML instance for this form
     */
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public String toString() {
        return String.format("Form Record[%s][InstanceId: %s]\n[Status: %s]\n[Form: %s]\n[Last Modified: %s]", this.recordId, this.getInstanceID(), this.status, this.xmlns, this.lastModified.toString());
    }

    public void setFormNumberForSubmissionOrdering(int num) {
        this.submissionOrderingNumber = "" + num;
    }

    public void logPendingDeletion(String classTag, String reason) {
        String logMessage = String.format(
                "Wiping form record with id %s and submission ordering number %s " +
                        "in class %s because %s",
                getInstanceID(),
                getSubmissionOrderingNumber(),
                classTag, reason);
        Logger.log(LogTypes.TYPE_FORM_DELETION, logMessage);
    }

    public static FormRecord getFormRecord(SqlStorage<FormRecord> formRecordStorage, int formRecordId) {
        return formRecordStorage.read(formRecordId);
    }

    public static FormRecord getFormRecord(SqlStorage<FormRecord> formRecordStorage, String formRecordPath) {
        try {
            return formRecordStorage.getRecordForValue(META_FILE_PATH, formRecordPath);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public static boolean isComplete(SqlStorage<FormRecord> formRecordStorage, String formRecordPath) {
        FormRecord formRecord = getFormRecord(formRecordStorage, formRecordPath);
        return formRecord != null && formRecord.status.contentEquals(STATUS_COMPLETE);
    }

    public void updateStatus(SqlStorage<FormRecord> formRecordStorage,
                             @FormRecordStatus String status) {
        if (!this.status.equals(FormRecord.STATUS_COMPLETE) && status.equals(FormRecord.STATUS_COMPLETE)) {
            setFormNumberForSubmissionOrdering(StorageUtils.getNextFormSubmissionNumber());
        }
        this.status = status;
        lastModified = new Date();
        formRecordStorage.update(getID(), this);
        finalizeRecord();
    }

    private void finalizeRecord() {
        try {
            updateAndProcessRecord();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            Logger.exception("Failed to update Instance row " + getID(), e);
            throw new SQLException("Failed to update Instance row " + getID());
        }
    }

    /**
     * Register an instance with the session's form record.
     */
    private void updateAndProcessRecord() {
        FormRecord current;
        try {
            current = updateAndWriteRecord();
        } catch (Exception e) {
            // Notify the server of this problem (since we aren't going to crash)
            ForceCloseLogger.reportExceptionInBg(e);
            CrashUtil.reportException(e);
            Logger.log(LogTypes.TYPE_FORM_ENTRY, e.getMessage());

            // Record will be wiped when form entry is exited
            throw new IllegalStateException(e.getMessage());
        }

        boolean complete = STATUS_COMPLETE.equals(status);

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
                } catch (InvalidCasePropertyLengthException e) {
                    Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, e.getMessage());
                    throw new IllegalStateException(
                            StringUtils.getStringRobust(
                                    CommCareApplication.instance(),
                                    R.string.invalid_case_property_length,
                                    e.getCaseProperty()));
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
     * Update the form record
     *
     * @return The updated form record, which has been written to storage.
     */
    private FormRecord updateAndWriteRecord()
            throws InvalidStateException {
        try {
            return FormRecordCleanupTask.updateAndWriteRecord(CommCareApplication.instance(),
                    this, CommCareApplication.instance().getUserStorage(FormRecord.class));
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

    private static class InvalidStateException extends Exception {
        public InvalidStateException(String message) {
            super(message);
        }
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setStatus(@FormRecordStatus String status) {
        this.status = status;
    }

    public String getXmlns() {
        return xmlns;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }
}

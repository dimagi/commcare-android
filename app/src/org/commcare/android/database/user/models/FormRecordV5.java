package org.commcare.android.database.user.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.modern.models.MetaField;

import java.util.Date;

import static org.commcare.android.database.user.models.FormRecord.META_APP_ID;
import static org.commcare.android.database.user.models.FormRecord.META_DISPLAY_NAME;
import static org.commcare.android.database.user.models.FormRecord.META_FILE_PATH;
import static org.commcare.android.database.user.models.FormRecord.META_LAST_MODIFIED;
import static org.commcare.android.database.user.models.FormRecord.META_STATUS;
import static org.commcare.android.database.user.models.FormRecord.META_SUBMISSION_ORDERING_NUMBER;
import static org.commcare.android.database.user.models.FormRecord.META_UUID;
import static org.commcare.android.database.user.models.FormRecord.META_XMLNS;

/**
 * @author ctsims
 */
@Table(FormRecord.STORAGE_KEY)
public class FormRecordV5 extends Persisted implements EncryptedModel {

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

    public FormRecordV5() {
    }

    /**
     * Creates a record of a form entry with the provided data. Note that none
     * of the parameters can be null...
     */
    public FormRecordV5(@FormRecord.FormRecordStatus String status, String xmlns, byte[] aesKey, String uuid,
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

    public FormRecordV5(FormRecordV5 oldRecord) {
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getXmlns() {
        return xmlns;
    }
}

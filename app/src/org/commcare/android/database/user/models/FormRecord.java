package org.commcare.android.database.user.models;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.modern.models.MetaField;
import org.commcare.provider.InstanceProviderAPI.InstanceColumns;

import java.io.FileNotFoundException;
import java.util.Date;

/**
 * @author ctsims
 */
@Table(FormRecord.STORAGE_KEY)
public class FormRecord extends Persisted implements EncryptedModel {

    public static final String STORAGE_KEY = "FORMRECORDS";

    public static final String META_INSTANCE_URI = "INSTANCE_URI";
    public static final String META_STATUS = "STATUS";
    public static final String META_UUID = "UUID";
    public static final String META_XMLNS = "XMLNS";
    public static final String META_LAST_MODIFIED = "DATE_MODIFIED";
    public static final String META_APP_ID = "APP_ID";

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
     * This form was complete, but something blocked it from processing and it's in a damaged state
     */
    public static final String STATUS_LIMBO = "limbo";

    /**
     * This form has been downloaded, but not processed for metadata
     */
    public static final String STATUS_UNINDEXED = "unindexed";

    @Persisting(1)
    @MetaField(META_XMLNS)
    private String xmlns;
    @Persisting(2)
    @MetaField(META_INSTANCE_URI)
    private String instanceURI;
    @Persisting(3)
    @MetaField(META_STATUS)
    private String status;
    @Persisting(4)
    private byte[] aesKey;
    @Persisting(value = 5, nullable = true)
    @MetaField(META_UUID)
    private String uuid;
    @Persisting(6)
    @MetaField(META_LAST_MODIFIED)
    private Date lastModified;
    @Persisting(7)
    @MetaField(META_APP_ID)
    private String appId;

    public FormRecord() {
    }

    /**
     * Creates a record of a form entry with the provided data. Note that none
     * of the parameters can be null...
     */
    public FormRecord(String instanceURI, String status, String xmlns, byte[] aesKey, String uuid,
                      Date lastModified, String appId) {
        this.instanceURI = instanceURI;
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

    /**
     * Create a copy of the current form record, with an updated instance uri
     * and status.
     */
    public FormRecord updateInstanceAndStatus(String instanceURI, String newStatus) {
        FormRecord fr = new FormRecord(instanceURI, newStatus, xmlns, aesKey, uuid,
                lastModified, appId);
        fr.recordId = this.recordId;
        return fr;
    }

    public Uri getInstanceURI() {
        if ("".equals(instanceURI)) {
            return null;
        }
        return Uri.parse(instanceURI);
    }

    public byte[] getAesKey() {
        return aesKey;
    }

    public String getStatus() {
        return status;
    }

    public String getInstanceID() {
        return uuid;
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

    /**
     * Get the file system path to the encrypted XML submission file.
     *
     * @param context Android context
     * @return A string containing the location of the encrypted XML instance for this form
     * @throws FileNotFoundException If there isn't a record available defining a path for this form
     */
    public String getPath(Context context) throws FileNotFoundException {
        Uri uri = getInstanceURI();
        if (uri == null) {
            throw new FileNotFoundException("No form instance URI exists for formrecord " + recordId);
        }

        Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, new String[]{InstanceColumns.INSTANCE_FILE_PATH}, null, null, null);
            if (c == null || !c.moveToFirst()) {
                throw new FileNotFoundException("No Instances were found at for formrecord " + recordId + " at isntance URI " + uri.toString());
            }

            return c.getString(c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Override
    public String toString() {
        return String.format("Form Record[%s][Status: %s]\n[Form: %s]\n[Last Modified: %s]", this.recordId, this.status, this.xmlns, this.lastModified.toString());
    }

    public void setArchivedFormToUnsent() {
        if(STATUS_SAVED.equals(this.getStatus())) {
            this.status = STATUS_UNSENT;
        }
    }
}

package org.commcare.android.database.user.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

/**
 * This class represents the version of a FormRecord that exists on any devices running versions
 * 2.39 through 2.41 of CommCare, which was deprecated in user db version 23. This class is used
 * to read a form record that exists in such a database, in order to run a db upgrade.
 */

@Table("FORMRECORDS")
public class FormRecordV4 extends Persisted {

    public static final String META_INSTANCE_URI = "INSTANCE_URI";

    @Persisting(1)
    @MetaField(FormRecord.META_XMLNS)
    private String xmlns;

    @Persisting(2)
    @MetaField(META_INSTANCE_URI)
    private String instanceURI;

    @Persisting(3)
    @MetaField(FormRecord.META_STATUS)
    private String status;

    @Persisting(4)
    private byte[] aesKey;

    @Persisting(value = 5, nullable = true)
    @MetaField(FormRecord.META_UUID)
    private String uuid;

    @Persisting(6)
    @MetaField(FormRecord.META_LAST_MODIFIED)
    private Date lastModified;

    @Persisting(7)
    @MetaField(FormRecord.META_APP_ID)
    private String appId;

    @Persisting(value = 8, nullable = true)
    @MetaField(FormRecord.META_SUBMISSION_ORDERING_NUMBER)
    private String submissionOrderingNumber;

    @Persisting(value = 9, nullable = true)
    private String quarantineReason;

    //   Deserialization only
    public FormRecordV4() {
    }

    public FormRecordV4(String instanceURI, String status, String xmlns, byte[] aesKey, String uuid,
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

    public String getInstanceURIString() {
        return instanceURI;
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

    public String getAppId() {
        return this.appId;
    }

    public void setFormNumberForSubmissionOrdering(int num) {
        this.submissionOrderingNumber = "" + num;
    }
}

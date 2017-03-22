package org.commcare.android.database.user.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.models.framework.Table;
import org.commcare.modern.models.MetaField;

import java.util.Date;

/**
 * This class represents the version of a FormRecord that exists on any devices running versions
 * 2.26 though 2.34 of CommCare, which was deprecated in user db version 17. This class is used
 * to read a form record that exists in such a database, in order to run a db upgrade.
 *
 * @author Aliza Stone
 */
@Table("FORMRECORDS")
public class FormRecordV2 extends Persisted {

    @Persisting(1)
    @MetaField(FormRecord.META_XMLNS)
    private String xmlns;
    @Persisting(2)
    @MetaField(FormRecord.META_INSTANCE_URI)
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

    /*
     * Deserialization only
     */
    public FormRecordV2() {
    }

    public FormRecordV2(String instanceURI, String status, String xmlns, byte[] aesKey, String uuid,
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

}

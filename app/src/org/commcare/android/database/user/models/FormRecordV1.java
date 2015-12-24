package org.commcare.android.database.user.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.android.storage.framework.Persisting;
import org.commcare.android.storage.framework.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.modern.models.MetaField;

import java.util.Date;

/**
 * This class represents the version of a FormRecord that exists on any devices running a pre-2.26
 * version of CommCare, which was deprecated in user db version 9. This class is used to read a
 * FormRecord that exists in such a database, in order to run a db upgrade.
 *
 * @author amstone
 */
@Table(FormRecord.STORAGE_KEY)
public class FormRecordV1 extends Persisted implements EncryptedModel {

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

    /*
     * Deserialization only
     */
    public FormRecordV1() {
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

    @Override
    public boolean isEncrypted(String data) {
        return false;
    }

    @Override
    public boolean isBlobEncrypted() {
        return true;
    }
}

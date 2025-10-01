package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

/**
 * DB model for storing the encrypted/encoded Connect DB passphrase
 *
 * @author dviggiano
 */
@Table(ConnectKeyRecord.STORAGE_KEY)
public class ConnectKeyRecord extends Persisted {
    public static final String STORAGE_KEY = "connect_key";
    public static final String IS_LOCAL = "is_local";
    @Persisting(1)
    String encryptedPassphrase;

    @Deprecated
    @Persisting(2)
    @MetaField(IS_LOCAL)
    boolean isLocal = true;

    public ConnectKeyRecord() {
    }

    public ConnectKeyRecord(String encryptedPassphrase) {
        this.encryptedPassphrase = encryptedPassphrase;
    }

    public String getEncryptedPassphrase() {
        return encryptedPassphrase;
    }
    public void setEncryptedPassphrase(String passphrase) {
        encryptedPassphrase = passphrase;
    }

    public static ConnectKeyRecord fromV6(ConnectKeyRecordV6 oldVersion) {
        return new ConnectKeyRecord(oldVersion.getEncryptedPassphrase());
    }
}

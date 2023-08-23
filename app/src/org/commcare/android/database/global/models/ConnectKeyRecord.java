package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

/**
 * @author dviggiano
 * DB model for storing the encrypted/encoded Connect DB passphrase
 */
@Table(ConnectKeyRecord.STORAGE_KEY)
public class ConnectKeyRecord extends Persisted {
    public static final String STORAGE_KEY = "connect_key";
    @Persisting(1)
    String encryptedPassphrase;

    public ConnectKeyRecord() {
    }

    public ConnectKeyRecord(String encryptedPassphrase) {
        this.encryptedPassphrase = encryptedPassphrase;
    }

    public String getEncryptedPassphrase() {
        return encryptedPassphrase;
    }
}

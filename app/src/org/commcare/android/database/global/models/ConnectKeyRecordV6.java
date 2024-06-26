package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

/**
 * DB model for storing the encrypted/encoded Connect DB passphrase
 * Used up to global DB V6
 *
 * @author dviggiano
 */
@Table(ConnectKeyRecordV6.STORAGE_KEY)
public class ConnectKeyRecordV6 extends Persisted {
    public static final String STORAGE_KEY = "connect_key";
    @Persisting(1)
    String encryptedPassphrase;

    public ConnectKeyRecordV6() {
    }

    public ConnectKeyRecordV6(String encryptedPassphrase) {
        this.encryptedPassphrase = encryptedPassphrase;
    }

    public String getEncryptedPassphrase() {
        return encryptedPassphrase;
    }
}

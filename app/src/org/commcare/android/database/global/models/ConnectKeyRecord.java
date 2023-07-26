package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;

@Table(ConnectKeyRecord.STORAGE_KEY)
public class ConnectKeyRecord extends Persisted {
    public static final String STORAGE_KEY = "connect_key";
    @Persisting(2)
    String encryptedPassphrase;

    public ConnectKeyRecord() { }

    public ConnectKeyRecord(String encryptedPassphrase) {
        this.encryptedPassphrase = encryptedPassphrase;
    }

    public String getEncryptedPassphrase() { return encryptedPassphrase; }
}

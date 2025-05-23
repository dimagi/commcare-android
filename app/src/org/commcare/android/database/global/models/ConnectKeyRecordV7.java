package org.commcare.android.database.global.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.MetaField;

/**
 * DB model for storing the encrypted/encoded Connect DB passphrase
 * Used up to global DB V6
 *
 * @author dviggiano
 */
@Table(ConnectKeyRecordV7.STORAGE_KEY)
public class ConnectKeyRecordV7 extends Persisted {
    public static final String STORAGE_KEY = "connect_key";
    public static final String IS_LOCAL = "is_local";
    @Persisting(1)
    String encryptedPassphrase;

    @Persisting(2)
    @MetaField(IS_LOCAL)
    boolean isLocal;

    public ConnectKeyRecordV7() {
    }

    public ConnectKeyRecordV7(String encryptedPassphrase, boolean isLocal) {
        this.encryptedPassphrase = encryptedPassphrase;
        this.isLocal = isLocal;
    }

    public String getEncryptedPassphrase() {
        return encryptedPassphrase;
    }

    public boolean getIsLocal() {
        return isLocal;
    }

    public static ConnectKeyRecordV7 fromV6(ConnectKeyRecordV6 oldVersion) {
        return new ConnectKeyRecordV7(oldVersion.getEncryptedPassphrase(), true);
    }
}

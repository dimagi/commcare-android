package org.commcare.utils;

import android.content.Context;
import android.os.Build;

import org.commcare.android.security.AesKeyStoreHandler;
import org.commcare.android.security.KeyStoreHandler;
import org.commcare.android.security.RsaKeyStoreHandler;

/**
 * Class for providing encryption keys backed by Android Keystore
 *
 * @author dviggiano
 */
public class EncryptionKeyProvider {
    /**
     *  Key name to get the secret value from key store
     *  the value of the key should not be renamed due to backward compatibility
     */
    private static final String SECRET_NAME = "secret";

    private final Context context;


    public EncryptionKeyProvider(Context context) {
        this.context = context;
    }

    public EncryptionKeyAndTransform getKeyForEncryption() {
        return getHandler(true).getKeyOrGenerate();
    }

    public EncryptionKeyAndTransform getKeyForDecryption() {
        return getHandler(false).getKeyOrGenerate();
    }

    /**
     * If RSA key exists, use it. Otherwise only use RSA for pre Android M devices
     */
    private KeyStoreHandler getHandler(boolean isEncryptMode) {
        RsaKeyStoreHandler rsaKeystoreHandler = new RsaKeyStoreHandler(context, SECRET_NAME, isEncryptMode);
        if (rsaKeystoreHandler.doesKeyExist()) {
            return rsaKeystoreHandler;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new AesKeyStoreHandler(SECRET_NAME, false); // change false to true if you need user auth
        } else {
            return rsaKeystoreHandler;
        }
    }
}

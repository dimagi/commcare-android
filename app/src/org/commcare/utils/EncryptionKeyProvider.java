package org.commcare.utils;

import android.content.Context;
import android.os.Build;

import org.commcare.android.security.AesKeystoreHandler;
import org.commcare.android.security.KeystoreHandler;
import org.commcare.android.security.RsaKeystoreHandler;

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
        this.context = context.getApplicationContext();
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
    private KeystoreHandler getHandler(boolean isEncryptMode) {
        RsaKeystoreHandler rsaKeystoreHandler = new RsaKeystoreHandler(context, SECRET_NAME, isEncryptMode);
        if (rsaKeystoreHandler.doesKeyExists()) {
            return rsaKeystoreHandler;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new AesKeystoreHandler(SECRET_NAME, false); // change false to true if you need user auth
        } else {
            return rsaKeystoreHandler;
        }
    }

    public EncryptionKeyAndTransform getKeyForEncryption(Context context) {
        return getHandler(true).getKeyOrGenerate();
    }

    public EncryptionKeyAndTransform getKeyForDecryption(Context context) {
        return getHandler(false).getKeyOrGenerate();
    }
}

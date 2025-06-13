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

    private final Context context;
    private final boolean needsUserAuth;
    private final String keyAlias;

    public EncryptionKeyProvider(Context context, boolean needsUserAuth, String keyAlias) {
        this.context = context;
        this.needsUserAuth = needsUserAuth;
        this.keyAlias = keyAlias;
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
        RsaKeyStoreHandler rsaKeystoreHandler = new RsaKeyStoreHandler(context, keyAlias, isEncryptMode);
        if (rsaKeystoreHandler.doesKeyExist()) {
            return rsaKeystoreHandler;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new AesKeyStoreHandler(keyAlias, needsUserAuth);
        } else {
            return rsaKeystoreHandler;
        }
    }

    public boolean isKeyValid() {
        return getHandler(false).isKeyValid();
    }

    public void deleteKey() {
        getHandler(false).deleteKey();
    }
}

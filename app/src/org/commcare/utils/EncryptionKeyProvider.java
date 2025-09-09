package org.commcare.utils;

import org.commcare.android.security.AesKeyStoreHandler;
import org.commcare.android.security.KeyStoreHandler;

/**
 * Class for providing encryption keys backed by Android Keystore
 *
 * @author dviggiano
 */
public class EncryptionKeyProvider {
    private final boolean needsUserAuth;
    private final String keyAlias;

    public EncryptionKeyProvider(boolean needsUserAuth, String keyAlias) {
        this.needsUserAuth = needsUserAuth;
        this.keyAlias = keyAlias;
    }

    public EncryptionKeyAndTransform getCryptographicKey() {
        return getHandler().getKeyOrGenerate();
    }

    private KeyStoreHandler getHandler() {
        return new AesKeyStoreHandler(keyAlias, needsUserAuth);
    }

    public boolean isKeyValid() {
        return getHandler().isKeyValid();
    }

    public void deleteKey() {
        getHandler().deleteKey();
    }
}

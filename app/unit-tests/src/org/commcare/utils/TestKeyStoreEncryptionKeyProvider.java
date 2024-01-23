package org.commcare.utils;

import android.os.Build;
import android.security.keystore.KeyProperties;

import org.commcare.util.EncryptionHelper;
import org.commcare.util.EncryptionKeyHelper;
import org.commcare.util.IKeyStoreEncryptionKeyProvider;

import java.security.Key;

import androidx.annotation.RequiresApi;

/**
 * Class for providing encryption keys backed by Android Keystore for Unit testing
 *
 * @author avazirna
 */
public class TestKeyStoreEncryptionKeyProvider implements IKeyStoreEncryptionKeyProvider {

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM;
    @RequiresApi(api = Build.VERSION_CODES.M)
    private static final String PADDING = KeyProperties.ENCRYPTION_PADDING_NONE;

    // Generates a cryptrographic key and adds it to the Android KeyStore
    @Override
    public Key generateCryptographicKeyInKeyStore(String keyAlias,
                                                  EncryptionHelper.CryptographicOperation cryptographicOperation)
            throws EncryptionKeyHelper.EncryptionKeyException {
        throw new EncryptionKeyHelper.EncryptionKeyException("KeyStore encryption key generator provider for testing only");
    }

    @Override
    public String getTransformationString() {
        return String.format("%s/%s/%s", ALGORITHM, BLOCK_MODE, PADDING);
    }

    @Override
    public String getKeyStoreName() {
        return "AndroidKeyStore";
    }
}

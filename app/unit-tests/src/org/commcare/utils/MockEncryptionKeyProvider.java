package org.commcare.utils;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.InvalidAlgorithmParameterException;

import javax.crypto.SecretKey;

/**
 * Mock key provider, creates an AES secret key but doesn't store it for future usage
 * Security considerations:
 * - Reuses the same key across multiple calls
 * - Keeps secret key in memory
 * - For testing purposes only, not suitable for production use
 * @author dviggiano
 */
public class MockEncryptionKeyProvider extends EncryptionKeyProvider {
    private SecretKey secretKey = null;
    private static final String TEST_SECRET = "test-secret";

    public MockEncryptionKeyProvider(Context context) {
        super(context, false, TEST_SECRET);
    }

    @Override
    public EncryptionKeyAndTransform getKeyForEncryption() {
        try {
            return getKey();
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public EncryptionKeyAndTransform getKeyForDecryption() {
        try {
            return getKey();
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    private EncryptionKeyAndTransform getKey() throws InvalidAlgorithmParameterException {
        if (secretKey == null) {
            MockKeyGenerator keyGenerator =  new MockKeyGenerator();
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(TEST_SECRET,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);

            keyGenerator.init(builder.build());
            secretKey = keyGenerator.generateKey();
        }

        return new EncryptionKeyAndTransform(secretKey, "AES/CBC/PKCS7Padding");
    }
}

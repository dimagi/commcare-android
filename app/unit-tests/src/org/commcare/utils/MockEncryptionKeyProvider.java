package org.commcare.utils;

import android.content.Context;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Mock key provider, creates an RSA KeyPair but doesn't store it for future usage
 * Security considerations:
 * - Reuses the same key pair across multiple calls
 * - Keeps private key in memory
 * - For testing purposes only, not suitable for production use
 * @author dviggiano
 */
public class MockEncryptionKeyProvider extends EncryptionKeyProvider {
    private KeyPair keyPair = null;

    public MockEncryptionKeyProvider(Context context) {
        super(context);
    }

    @Override
    public EncryptionKeyAndTransform getKeyForEncryption() {
        try {
            return getKey(true);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public EncryptionKeyAndTransform getKeyForDecryption() {
        try {
            return getKey(false);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private EncryptionKeyAndTransform getKey(boolean trueForEncrypt)
            throws NoSuchAlgorithmException {
        if (keyPair == null) {
            //Create an RSA keypair that we can use to encrypt and decrypt
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048); // Standard key size for RSA
            keyPair = keyGen.generateKeyPair();
        }

        return new EncryptionKeyAndTransform(trueForEncrypt ? keyPair.getPublic() : keyPair.getPrivate(),
                "RSA/ECB/PKCS1Padding");
    }
}

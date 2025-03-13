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

    @Override
    public EncryptionKeyAndTransform getKey(Context context, boolean trueForEncrypt)
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

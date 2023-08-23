package org.commcare.utils;

import android.content.Context;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * @author dviggiano
 */
public class MockEncryptionKeyProvider extends EncryptionKeyProvider {
    private KeyPair keyPair = null;

    @Override
    public EncryptionKeyAndTransform getKey(Context context, boolean trueForEncrypt)
            throws NoSuchAlgorithmException {
        if (keyPair == null) {
            //Create an RSA keypair that we can use to encrypt and decrypt
            keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        }
        String transformation = EncryptionKeyProvider.getTransformationString(true);

        return new EncryptionKeyAndTransform(trueForEncrypt ? keyPair.getPrivate() : keyPair.getPublic(),
                transformation);
    }
}

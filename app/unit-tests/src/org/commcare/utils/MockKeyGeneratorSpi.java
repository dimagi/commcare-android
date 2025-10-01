package org.commcare.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.commcare.android.security.AndroidKeyStore;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.KeyGenerator;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

/**
 * Mock KeyGeneratorSpi for testing purposes
 *
 * @author avazirna
 */
public class MockKeyGeneratorSpi extends KeyGeneratorSpi {
    private KeyGenerator wrappedKeyGenerator;
    private KeyStore keyStore;
    private KeyGenParameterSpec spec = null;

    {
        try {
            wrappedKeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES);
            keyStore = KeyStore.getInstance(AndroidKeyStore.ANDROID_KEY_STORE_NAME);
            keyStore.load(null);
        } catch (CertificateException | IOException | NoSuchAlgorithmException |
                 KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (!(params instanceof KeyGenParameterSpec)) {
            throw new InvalidAlgorithmParameterException(
                    String.format("Cannot initialize without a %s parameter", KeyGenParameterSpec.class.getName()));
        }
        spec = (KeyGenParameterSpec)params;
    }

    @Override
    protected void engineInit(int keysize, SecureRandom random) {
        throw new UnsupportedOperationException(
                "This is a mock implementation of a Service Provider Interface for KeyGenerator");
    }

    @Override
    protected void engineInit(SecureRandom random) {
        throw new UnsupportedOperationException(
                "This is a mock implementation of a Service Provider Interface for KeyGenerator");
    }

    @Override
    protected SecretKey engineGenerateKey() {
        SecretKey secretKey = wrappedKeyGenerator.generateKey();
        try {
            keyStore.setKeyEntry(spec.getKeystoreAlias(), secretKey, null, null);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
        return secretKey;
    }
}

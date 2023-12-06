package org.commcare.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

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

public class MockKeyGeneratorSpi extends KeyGeneratorSpi {
    private KeyGenerator wrappedKeyGenerator;
    private KeyStore keyStore;
    private KeyGenParameterSpec spec = null;

    {
        try {
            wrappedKeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES);
            keyStore = KeyStore.getInstance(GlobalConstants.KEYSTORE_NAME);
            keyStore.load(null);
        } catch (CertificateException | IOException | NoSuchAlgorithmException |
                 KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (params == null || !(params instanceof KeyGenParameterSpec)) {
            throw new InvalidAlgorithmParameterException(
                    String.format("Cannot initialize without a %s parameter", KeyGenParameterSpec.class.getName()));
        }
        spec = (KeyGenParameterSpec)params;
    }

    @Override
    protected void engineInit(int keysize, SecureRandom random) {
        // Do nothing, this is a mock key generator
    }

    @Override
    protected void engineInit(SecureRandom random) {
        // Do nothing, this is a mock key generator
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

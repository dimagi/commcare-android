package org.commcare.utils;

import android.security.keystore.KeyProperties;

import org.commcare.android.security.AndroidKeyStore;

import java.security.Security;

import javax.crypto.KeyGenerator;

/**
 * Mock KeyGenerator for testing purposes
 *
 * @author avazirna
 */
public class MockKeyGenerator extends KeyGenerator {

    public MockKeyGenerator() {
        super(new MockKeyGeneratorSpi() , Security.getProvider(AndroidKeyStore.ANDROID_KEY_STORE_NAME),
                KeyProperties.KEY_ALGORITHM_AES);
    }
}

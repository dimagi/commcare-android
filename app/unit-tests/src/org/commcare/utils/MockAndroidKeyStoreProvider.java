package org.commcare.utils;

import org.commcare.android.security.AndroidKeyStore;

import java.security.Provider;
import java.security.Security;

/**
 * Mock AndroidKeyStore provider for testing purposes
 *
 * @author avazirna
 */
public class MockAndroidKeyStoreProvider extends Provider {

    {
        put("KeyStore.AndroidKeyStore", MockKeyStore.class.getName());
        put("KeyGenerator.AES", MockKeyGeneratorSpi.class.getName());
    }

    protected MockAndroidKeyStoreProvider() {
        super(AndroidKeyStore.ANDROID_KEY_STORE_NAME, 1.0, "Mock implementation of AndroidKeyStore provider");
    }

    public static void registerProvider() {
        Security.addProvider(new MockAndroidKeyStoreProvider());
    }

    public static void deregisterProvider() {
        if (Security.getProvider(AndroidKeyStore.ANDROID_KEY_STORE_NAME) != null) {
            Security.removeProvider(AndroidKeyStore.ANDROID_KEY_STORE_NAME);
        }
    }
}

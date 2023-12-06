package org.commcare.utils;

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;

public class MockAndroidKeyStoreProvider extends Provider {

    {
        put("KeyStore.AndroidKeyStore", MockKeyStore.class.getName());
    }

    protected MockAndroidKeyStoreProvider() {
        super(GlobalConstants.KEYSTORE_NAME, 1.0, "Mock AndroidKeyStore provider");
    }

    public static void registerProvider() throws NoSuchAlgorithmException {
        Security.addProvider(new MockAndroidKeyStoreProvider());
    }

    public static void deregisterProvider() {
        if (Security.getProvider(GlobalConstants.KEYSTORE_NAME) != null) {
            Security.removeProvider(GlobalConstants.KEYSTORE_NAME);
        }
    }
}

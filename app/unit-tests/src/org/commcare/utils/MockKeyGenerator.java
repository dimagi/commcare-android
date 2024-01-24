package org.commcare.utils;

import java.security.Security;

import javax.crypto.KeyGenerator;

public class MockKeyGenerator extends KeyGenerator {

    public MockKeyGenerator() {
        super(new MockKeyGeneratorSpi() , Security.getProvider(GlobalConstants.KEYSTORE_NAME), "AES");
    }
}

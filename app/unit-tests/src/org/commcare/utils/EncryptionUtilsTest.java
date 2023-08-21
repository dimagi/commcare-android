package org.commcare.utils;

import junit.framework.Assert;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

public class EncryptionUtilsTest {
    @Test
    public void testEncryption() {
        try {
            String testData = "This is a test string";
            byte[] testBytes = testData.getBytes(StandardCharsets.UTF_8);
            //Create an RSA keypair that we can use to encrypt and decrypt the test string
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String transformation = EncryptionUtils.getTransformationString(true);

            byte[] encrypted = EncryptionUtils.encrypt(testBytes, new EncryptionUtils.KeyAndTransform(keyPair.getPrivate(), transformation));
            String encryptedString = new String(encrypted);
            Assert.assertFalse(testData.equals(encryptedString));

            byte[] decrypted = EncryptionUtils.decrypt(encrypted, new EncryptionUtils.KeyAndTransform(keyPair.getPublic(), transformation));
            String decryptedString = new String(decrypted);
            Assert.assertEquals(testData, decryptedString);
        }
        catch(Exception e) {
            Assert.fail("Exception: " + e);
        }
    }
}

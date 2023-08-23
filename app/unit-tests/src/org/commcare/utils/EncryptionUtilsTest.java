package org.commcare.utils;

import junit.framework.Assert;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * @author dviggiano
 * Unit test for the encryption and decryption of a string
 */
public class EncryptionUtilsTest {
    @Test
    public void testEncryption() {
        try {
            String testData = "This is a test string";
            byte[] testBytes = testData.getBytes(StandardCharsets.UTF_8);

            EncryptionKeyProvider provider = new MockEncryptionKeyProvider();

            byte[] encrypted = EncryptionUtils.encrypt(testBytes, provider.getKey(null, true));
            String encryptedString = new String(encrypted);
            Assert.assertFalse(testData.equals(encryptedString));

            byte[] decrypted = EncryptionUtils.decrypt(encrypted, provider.getKey(null, false));
            String decryptedString = new String(decrypted);
            Assert.assertEquals(testData, decryptedString);
        } catch (Exception e) {
            Assert.fail("Exception: " + e);
        }
    }
}

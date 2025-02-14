package org.commcare.utils;

import junit.framework.Assert;

import org.javarosa.core.storage.Shoe;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Vector;

import static org.junit.Assert.assertThrows;

/**
 * Unit test for the encryption and decryption of a string
 *
 * @author dviggiano
 */
public class EncryptionUtilsTest {
    private final EncryptionKeyProvider provider = new MockEncryptionKeyProvider();
    private static final String TEST_DATA = "This is a test string";

    @Test
    public void testEncryption() throws Exception {
        byte[] testBytes = TEST_DATA.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = EncryptionUtils.encrypt(testBytes, provider.getKey(null, true));
        Assert.assertNotNull("Encrypted data should not be null", encrypted);
        Assert.assertFalse("Encrypted data should differ from input",
                TEST_DATA.equals(new String(encrypted)));
    }

    @Test
    public void testDecryption() throws Exception {
        byte[] testBytes = TEST_DATA.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = EncryptionUtils.encrypt(testBytes, provider.getKey(null, true));
        byte[] decrypted = EncryptionUtils.decrypt(encrypted, provider.getKey(null, false));
        String decryptedString = new String(decrypted, StandardCharsets.UTF_8);
        Assert.assertEquals("Decrypted data should match original", TEST_DATA, decryptedString);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncryptionWithNullInput() throws Exception {
        EncryptionUtils.encrypt(null, provider.getKey(null, true));
    }

    @Test(expected = NullPointerException.class)
    public void testEncryptionWithNullKey() throws Exception {
        EncryptionUtils.encrypt(TEST_DATA.getBytes(StandardCharsets.UTF_8), null);
    }
}

package org.commcare.utils;

import android.util.Base64;

import junit.framework.Assert;

import org.commcare.CommCareTestApplication;
import org.commcare.util.EncryptionUtils;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Unit test for the encryption and decryption of a string
 *
 * @author dviggiano
 */
public class EncryptionUtilsTest {
    private final EncryptionKeyProvider provider = new MockEncryptionKeyProvider(CommCareTestApplication.instance());
    private static final String TEST_DATA = "This is a test string";

    @Test
    public void testEncryption() throws Exception {
        byte[] testBytes = TEST_DATA.getBytes(StandardCharsets.UTF_8);
        EncryptionKeyAndTransform kat = provider.getKeyForEncryption();
        String encryptedString = EncryptionUtils.encrypt(testBytes, kat.getKey(), kat.getTransformation(), true);
        byte[] encrypted = encryptedString.getBytes(Charset.forName("UTF-8"));
        Assert.assertNotNull("Encrypted data should not be null", encrypted);
        Assert.assertFalse("Encrypted data should differ from input",
                TEST_DATA.equals(new String(encrypted)));
    }

    @Test
    public void testDecryption() throws Exception {
        byte[] testBytes = TEST_DATA.getBytes(StandardCharsets.UTF_8);

        EncryptionKeyAndTransform kat = provider.getKeyForDecryption();
        String encryptedString = EncryptionUtils.encrypt(testBytes, kat.getKey(), kat.getTransformation(), true);
        byte[] encrypted = org.commcare.util.Base64.decode(encryptedString);

        kat = provider.getKeyForDecryption();
        byte[] decrypted = EncryptionUtils.decrypt(encrypted, kat.getKey(), kat.getTransformation(), true);
        String decryptedString = new String(decrypted, Charset.forName("UTF-8"));

        Assert.assertEquals("Decrypted data should match original", TEST_DATA, decryptedString);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEncryptionWithNullInput() throws Exception {
        EncryptionKeyAndTransform kat = provider.getKeyForEncryption();
        EncryptionUtils.encrypt(null, kat.getKey(), kat.getTransformation(), true);
    }

    @Test(expected = NullPointerException.class)
    public void testEncryptionWithNullKey() throws Exception {
        EncryptionKeyAndTransform kat = provider.getKeyForEncryption();
        EncryptionUtils.encrypt(TEST_DATA.getBytes(StandardCharsets.UTF_8), null, kat.getTransformation(), true);
    }

    @Test(expected = NullPointerException.class)
    public void testEncryptionWithNullTransformation() throws Exception {
        EncryptionKeyAndTransform kat = provider.getKeyForEncryption();
        EncryptionUtils.encrypt(TEST_DATA.getBytes(StandardCharsets.UTF_8), kat.getKey(), null, true);
    }
}

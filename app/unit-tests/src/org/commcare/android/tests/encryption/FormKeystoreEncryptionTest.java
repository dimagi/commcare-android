package org.commcare.android.tests.encryption;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.services.CommCareKeyManager;
import org.commcare.utils.EncryptionKeyAndTransform;
import org.commcare.utils.MockAndroidKeyStoreProvider;
import org.commcare.utils.MockEncryptionKeyProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.spec.SecretKeySpec;

@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FormKeystoreEncryptionTest {

    private static final String TEST_XML = "<data><name>test form</name></data>";
    private MockEncryptionKeyProvider keyProvider;

    @Before
    public void setUp() {
        MockAndroidKeyStoreProvider.registerProvider();
        keyProvider = new MockEncryptionKeyProvider(CommCareApplication.instance());
    }

    @After
    public void tearDown() {
        MockAndroidKeyStoreProvider.deregisterProvider();
    }

    @Test
    public void testFormRecordUsesKeystoreEncryption_withEmptyKey() {
        FormRecord record = new FormRecord(FormRecord.STATUS_UNSTARTED,
                "http://test.xmlns", new byte[0], null, new Date(), "test-app-id");
        Assert.assertTrue("Empty aesKey should indicate Keystore encryption",
                record.usesKeystoreEncryption());
    }

    @Test
    public void testFormRecordUsesKeystoreEncryption_withNullKey() {
        FormRecord record = new FormRecord(FormRecord.STATUS_UNSTARTED,
                "http://test.xmlns", null, null, new Date(), "test-app-id");
        Assert.assertTrue("Null aesKey should indicate Keystore encryption",
                record.usesKeystoreEncryption());
    }

    @Test
    public void testFormRecordUsesLegacyEncryption_withPopulatedKey() {
        byte[] legacyKey = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        FormRecord record = new FormRecord(FormRecord.STATUS_UNSTARTED,
                "http://test.xmlns", legacyKey, null, new Date(), "test-app-id");
        Assert.assertFalse("Non-empty aesKey should indicate legacy encryption",
                record.usesKeystoreEncryption());
    }

    @Test
    public void testKeystoreEncryptionDecryptionRoundTrip() throws IOException {
        EncryptionKeyAndTransform kat = keyProvider.getKeyForEncryption();
        File tempFile = File.createTempFile("form_test", ".xml");
        tempFile.deleteOnExit();

        // Encrypt
        try (OutputStream os = EncryptionIO.createFileOutputStreamWithKeystore(
                tempFile.getAbsolutePath(), kat)) {
            os.write(TEST_XML.getBytes(StandardCharsets.UTF_8));
        }

        // Decrypt
        kat = keyProvider.getKeyForDecryption();
        try (InputStream is = EncryptionIO.getFileInputStreamWithKeystore(
                tempFile.getAbsolutePath(), kat)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            Assert.assertEquals("Decrypted content should match original",
                    TEST_XML, result.toString());
        }
    }

    @Test
    public void testLegacyEncryptionStillWorks() throws IOException {
        byte[] keyBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKeySpec legacyKey = new SecretKeySpec(keyBytes, "AES");
        File tempFile = File.createTempFile("form_legacy_test", ".xml");
        tempFile.deleteOnExit();

        // Encrypt with legacy path
        try (OutputStream os = EncryptionIO.createFileOutputStream(
                tempFile.getAbsolutePath(), legacyKey)) {
            os.write(TEST_XML.getBytes(StandardCharsets.UTF_8));
        }

        // Decrypt with legacy path
        try (InputStream is = EncryptionIO.getFileInputStream(
                tempFile.getAbsolutePath(), legacyKey)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            Assert.assertEquals("Legacy decrypted content should match original",
                    TEST_XML, result.toString());
        }
    }

    @Test
    public void testGenerateLegacyKeyOrEmpty_withKeystoreAvailable() {
        byte[] key = CommCareKeyManager.generateLegacyKeyOrEmpty();
        Assert.assertEquals("Key should be empty when Keystore is available",
                0, key.length);
    }

    @Test
    public void testKeystoreEncryptedFileCannotBeReadWithLegacyPath() throws IOException {
        EncryptionKeyAndTransform kat = keyProvider.getKeyForEncryption();
        File tempFile = File.createTempFile("form_mismatch_test", ".xml");
        tempFile.deleteOnExit();

        // Encrypt with Keystore
        try (OutputStream os = EncryptionIO.createFileOutputStreamWithKeystore(
                tempFile.getAbsolutePath(), kat)) {
            os.write(TEST_XML.getBytes(StandardCharsets.UTF_8));
        }

        // Attempting to decrypt with a legacy key should fail
        byte[] keyBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKeySpec wrongKey = new SecretKeySpec(keyBytes, "AES");
        try (InputStream is = EncryptionIO.getFileInputStream(
                tempFile.getAbsolutePath(), wrongKey)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            Assert.assertNotEquals("Decrypting Keystore file with legacy key should not produce original content",
                    TEST_XML, result.toString());
        }
    }
}
package org.commcare.android.nfc;

import org.commcare.CommCareTestApplication;
import org.commcare.util.EncryptionHelper;
import org.junit.Test;

import static org.commcare.android.nfc.NfcManager.NFC_ENCRYPTION_SCHEME;
import static org.commcare.android.nfc.NfcManager.PAYLOAD_DELIMITER;

public class NfcManagerTest {

    private static final String ENCRYPTION_KEY = "DZzLZlbtJ4jqs/N+aM87h1mv32WcluGNI5OfMFEAecs=";
    private static final String ENTITY_ID = "972fd0b2174543e8a60b9fc811968e55";
    private static final String PAYLOAD = "dummy_payload";

    @Test
    public void payloadEncryptionTest() throws EncryptionHelper.EncryptionException {
        NfcManager nfcManager = new NfcManager(CommCareTestApplication.instance(), ENCRYPTION_KEY, ENTITY_ID, false);

        // Empty payload should not have any tag attached
        assert nfcManager.tagAndEncryptPayload("").contentEquals("");

        // Check for right tag
        String encryptedMessage = nfcManager.tagAndEncryptPayload(PAYLOAD);
        assert encryptedMessage.startsWith(NFC_ENCRYPTION_SCHEME + PAYLOAD_DELIMITER + ENTITY_ID + PAYLOAD_DELIMITER);

        // decrypt message and ensure we get the same payload
        assert nfcManager.decryptValue(encryptedMessage).contentEquals(PAYLOAD);
    }

    @Test
    public void emptyEncryptionKeyTest() throws EncryptionHelper.EncryptionException {
        NfcManager nfcManager = new NfcManager(CommCareTestApplication.instance(), "", ENTITY_ID, false);
        String encryptedMessage = nfcManager.tagAndEncryptPayload(PAYLOAD);
        assert encryptedMessage.startsWith(PAYLOAD_DELIMITER + ENTITY_ID + PAYLOAD_DELIMITER);
        assert nfcManager.decryptValue(encryptedMessage).contentEquals(PAYLOAD);
    }

    @Test
    public void emptyEntityIdTest() throws EncryptionHelper.EncryptionException {
        NfcManager nfcManager = new NfcManager(CommCareTestApplication.instance(), ENCRYPTION_KEY, "", false);
        String encryptedMessage = nfcManager.tagAndEncryptPayload(PAYLOAD);
        assert encryptedMessage.startsWith(NFC_ENCRYPTION_SCHEME + PAYLOAD_DELIMITER + PAYLOAD_DELIMITER);
        assert nfcManager.decryptValue(encryptedMessage).contentEquals(PAYLOAD);
    }

    @Test
    public void emptyEncryptionKeyAndEntityIdTest() throws EncryptionHelper.EncryptionException {
        NfcManager nfcManager = new NfcManager(CommCareTestApplication.instance(), "", "", false);
        String encryptedMessage = nfcManager.tagAndEncryptPayload(PAYLOAD);
        assert encryptedMessage.startsWith(PAYLOAD_DELIMITER + PAYLOAD_DELIMITER);
        assert nfcManager.decryptValue(encryptedMessage).contentEquals(PAYLOAD);
    }

    @Test(expected = NfcManager.InvalidPayloadTagException.class)
    public void readingPayloadWithDifferentTag_shouldFail() throws EncryptionHelper.EncryptionException {
        NfcManager nfcManager = new NfcManager(CommCareTestApplication.instance(), ENCRYPTION_KEY, "some_other_id", false);
        String encryptedMessage = nfcManager.tagAndEncryptPayload(PAYLOAD);
        new NfcManager(CommCareTestApplication.instance(), ENCRYPTION_KEY, ENTITY_ID, false)
                .decryptValue(encryptedMessage).contentEquals(PAYLOAD);
    }

    @Test
    public void payloadWithoutTagTest() throws EncryptionHelper.EncryptionException {
        // Decrypt an old payload without specifying encryptionKey and entityId
        assert new NfcManager(CommCareTestApplication.instance(), "", "", false)
                .decryptValue(PAYLOAD).contentEquals(PAYLOAD);

        // Decrypt an old payload with some encryptionKey and entityId by ignoring tag
        assert new NfcManager(CommCareTestApplication.instance(), ENCRYPTION_KEY, ENTITY_ID, true)
        .decryptValue(PAYLOAD).contentEquals(PAYLOAD);
    }

}
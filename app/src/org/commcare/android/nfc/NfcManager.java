package org.commcare.android.nfc;

import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;

import org.apache.commons.lang3.StringUtils;
import org.commcare.util.EncryptionHelper;

import javax.annotation.Nullable;

import androidx.appcompat.app.AppCompatActivity;

public class NfcManager {

    public static final String NFC_ENCRYPTION_SCHEME = "encryption_aes_v1";
    public static final String PAYLOAD_DELIMITER = "^⸘^";
    private final boolean allowUntaggedRead;

    private Context context;
    private NfcAdapter nfcAdapter;

    @Nullable
    protected String encryptionKey;

    @Nullable
    protected String entityId;

    public NfcManager(Context c, @Nullable String encryptionKey, @Nullable String entityId, boolean allowUntaggedRead) {
        this.context = c;
        this.encryptionKey = encryptionKey;
        this.entityId = entityId;
        this.allowUntaggedRead = allowUntaggedRead;
    }

    public void checkForNFCSupport() throws NfcNotSupportedException, NfcNotEnabledException {
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this.context);
        if (nfcAdapter == null)
            throw new NfcNotSupportedException();
        if (!nfcAdapter.isEnabled())
            throw new NfcNotEnabledException();
    }

    public void enableForegroundDispatch(AppCompatActivity activity, PendingIntent intent,
                                         IntentFilter[] filters, String[][] techLists) {
        this.nfcAdapter.enableForegroundDispatch(activity, intent, filters, techLists);
    }

    public void disableForegroundDispatch(AppCompatActivity activity) {
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(activity);
        }
    }

    public String decryptValue(String message) throws EncryptionHelper.EncryptionException {
        String payloadTag = getPayloadTag();
        if (message.startsWith(payloadTag)) {
            message = message.replace(payloadTag, "");
            if (!StringUtils.isEmpty(encryptionKey)) {
                    message = (new EncryptionHelper()).decryptWithBase64EncodedKey(EncryptionHelper.CC_KEY_ALGORITHM_AES, message, encryptionKey);
            }
        } else if (!allowUntaggedRead && !isEmptyPayloadTag(payloadTag)) {
            throw new InvalidPayloadTagException();
        }
        return message;
    }

    // Used in tagging the payload before writes and establishing payload identity before reads
    protected String getPayloadTag() {
        StringBuilder sb = new StringBuilder();
        if (!StringUtils.isEmpty(encryptionKey)) {
            sb.append(NFC_ENCRYPTION_SCHEME);
        }
        sb.append(PAYLOAD_DELIMITER);
        if (!StringUtils.isEmpty(entityId)) {
            sb.append(entityId);
        }
        sb.append(PAYLOAD_DELIMITER);
        return sb.toString();
    }

    // Returns an empty payload tag with no encryptionKey and entityId
    private CharSequence getEmptyPayloadTag() {
        return PAYLOAD_DELIMITER + PAYLOAD_DELIMITER;
    }

    private boolean isEmptyPayloadTag(String payloadTag) {
        return payloadTag.contentEquals(getEmptyPayloadTag());
    }

    public String tagAndEncryptPayload(String message) throws EncryptionHelper.EncryptionException {
        if (StringUtils.isEmpty(message)) {
            return message;
        }
        String payload = message;
        if (!StringUtils.isEmpty(encryptionKey)) {
            payload = (new EncryptionHelper()).encryptWithBase64EncodedKey(EncryptionHelper.CC_KEY_ALGORITHM_AES, payload, encryptionKey);
        }
        if (payload.contains(PAYLOAD_DELIMITER)) {
            throw new InvalidPayloadException();
        }
        return getPayloadTag() + payload;
    }

    public class NfcNotSupportedException extends Exception {

    }

    public class NfcNotEnabledException extends Exception {

    }

    public class InvalidPayloadException extends RuntimeException {
    }

    public class InvalidPayloadTagException extends RuntimeException {
    }
}

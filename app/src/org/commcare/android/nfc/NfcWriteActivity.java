package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Bundle;

import org.commcare.android.javarosa.IntentCallout;
import org.commcare.util.EncryptionUtils;

import java.io.IOException;

/**
 * When this activity is in the foreground, any NFC tag scanned by the device that is of a format
 * CommCare recognizes (as determined by the filters in NfcActivity::setReadyToHandleTag) will be
 * consumed by this activity. CommCare will then decide what type of NdefRecord to write to the tag
 * based upon the user-provided 'type' argument:
 *
 * - If type is 'text', CommCare will write a record of type NdefRecord.RTD_TEXT
 * - If the type is anything else, CommCare will assume the user is attempting to write a
 * custom/external record type (which will be qualified by the domain argument)
 *
 * If an error occurs during the write action, an appropriate error toast will be shown.
 *
 * @author Aliza Stone
 */
public class NfcWriteActivity extends NfcActivity {

    public static final String NFC_PAYLOAD_TO_WRITE = "payload";

    private String payloadToWrite;
    private String typeForPayload;

    @Override
    protected void initFields() {
        super.initFields();
        typeForPayload = getIntent().getStringExtra(NFC_PAYLOAD_SINGLE_TYPE_ARG);
        try {
            payloadToWrite = nfcManager.tagAndEncryptPayload(getIntent().getStringExtra(NFC_PAYLOAD_TO_WRITE));
        } catch (EncryptionUtils.EncryptionException e) {
            finishWithErrorToast("nfc.write.encryption.error", e);
        } catch (NfcManager.InvalidPayloadException e) {
            finishWithErrorToast("nfc.write.payload.error", e);
        }
    }

    @Override
    protected boolean requiredFieldsMissing() {
        if (this.payloadToWrite == null || this.payloadToWrite.equals("")) {
            finishWithErrorToast("nfc.write.no.payload");
            return true;
        } else if (this.typeForPayload == null || this.typeForPayload.equals("")) {
            finishWithErrorToast("nfc.write.no.type");
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void dispatchActionOnTag(Tag tag) {
        writeMessageToNfcTag(tag);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void writeMessageToNfcTag(Tag tag) {
        Ndef ndefObject = Ndef.get(tag);
        try {
            NdefRecord record = NdefRecordUtil.createNdefRecord(this.typeForPayload,
                    this.domainForType, this.payloadToWrite);
            NdefMessage msg = new NdefMessage(new NdefRecord[]{record});
            ndefObject.connect();
            ndefObject.writeNdefMessage(msg);
            ndefObject.close();
            finishWithToast("nfc.write.success", true);
        } catch (IOException e) {
            finishWithErrorToast("nfc.write.io.error", e);
        } catch (FormatException e) {
            finishWithErrorToast("nfc.write.msg.malformed", e);
        } catch (IllegalArgumentException e) {
            finishWithErrorToast("nfc.write.type.not.supported", e);
        }
    }

    @Override
    protected void setResultExtrasBundle(Intent i, boolean success) {
        Bundle responses = new Bundle();
        responses.putString("nfc_write_result", success ? "success" : "failure");
        i.putExtra(IntentCallout.INTENT_RESULT_EXTRAS_BUNDLE, responses);
    }

    @Override
    protected void setResultValue(Intent i, boolean success) {
        if (success) {
            i.putExtra(IntentCallout.INTENT_RESULT_VALUE, getIntent().getStringExtra(NFC_PAYLOAD_TO_WRITE));
        }
    }

    @Override
    protected String getInstructionsTextKey() {
        return "nfc.instructions.write";
    }
}

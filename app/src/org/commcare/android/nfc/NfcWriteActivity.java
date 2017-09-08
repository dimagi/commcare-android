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

import java.io.IOException;

/**
 * Created by amstone326 on 9/5/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NfcWriteActivity extends NfcActivity {

    public static final String NFC_PAYLOAD_TO_WRITE = "payload";

    private String payloadToWrite;

    @Override
    protected void initFields() {
        super.initFields();
        this.payloadToWrite = getIntent().getStringExtra(NFC_PAYLOAD_TO_WRITE);
    }

    @Override
    protected boolean requiredFieldsMissing() {
        if (this.payloadToWrite == null || this.payloadToWrite.equals("")) {
            finishWithErrorToast("nfc.write.no.payload");
            return true;
        } else {
            return super.requiredFieldsMissing();
        }
    }

    @Override
    protected void dispatchActionOnTag(Tag tag) {
        writeMessageToNfcTag(tag);
    }

    private void writeMessageToNfcTag(Tag tag) {
        Ndef ndefObject = Ndef.get(tag);
        try {
            NdefRecord record = NdefRecordUtil.createNdefRecord(this.userSpecifiedType,
                    this.userSpecifiedDomain, this.payloadToWrite);
            NdefMessage msg = new NdefMessage(new NdefRecord[]{record});
            ndefObject.connect();
            ndefObject.writeNdefMessage(msg);
            ndefObject.close();
            finishWithToast("nfc.write.success", true);
        } catch (IOException e) {
            finishWithErrorToast("nfc.write.io.error");
        } catch (FormatException e) {
            finishWithErrorToast("nfc.write.msg.malformed");
        }
    }

    @Override
    protected void setResultExtrasBundle(Intent i, boolean success) {
        Bundle responses = new Bundle();
        responses.putString("nfc_write_result", success ? "success" : "failure");
        i.putExtra(IntentCallout.INTENT_RESULT_EXTRAS_BUNDLE, responses);
    }

    @Override
    protected void setResultValue(Intent i) {
        // nothing for write action
    }

    @Override
    protected String getInstructionsTextKey() {
        return "nfc.instructions.write";
    }


}

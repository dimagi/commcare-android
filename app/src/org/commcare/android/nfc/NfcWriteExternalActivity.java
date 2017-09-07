package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.content.Context;
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
public class NfcWriteExternalActivity extends NfcActivity {

    public static final String NFC_PAYLOAD_TO_WRITE = "payload";

    private String payloadToWrite;
    private String typeTagForPayload;

    @Override
    protected void initFields() {
        this.payloadToWrite = getIntent().getStringExtra(NFC_PAYLOAD_TO_WRITE);
        this.typeTagForPayload = getIntent().getStringExtra(NfcManager.NFC_PAYLOAD_TYPE);
    }

    @Override
    protected boolean requiredFieldsMissing() {
        if (this.payloadToWrite == null || this.payloadToWrite.equals("")) {
            finishWithErrorToast("nfc.write.no.payload");
            return true;
        }
        if (this.typeTagForPayload == null || this.typeTagForPayload.equals("")) {
            finishWithErrorToast("nfc.write.no.type");
            return true;
        }
        return false;
    }

    @Override
    protected void dispatchActionOnTag(Tag tag) {
        writeMessageToNfcTag(tag);
    }

    private void writeMessageToNfcTag(Tag tag) {
        System.out.println("Attempting to write nfc message " + payloadToWrite + " with type " + typeTagForPayload);
        NdefRecord record = createNdefRecord(this, this.typeTagForPayload, this.payloadToWrite);
        NdefMessage msg = new NdefMessage(new NdefRecord[]{record});

        Ndef ndefObject = Ndef.get(tag);
        try {
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

    private static NdefRecord createNdefRecord(Context context, String type, String payloadToWrite) {
        return NdefRecord.createExternal(context.getPackageName(), type, payloadToWrite.getBytes());
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

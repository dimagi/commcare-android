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
public class NfcReadActivity extends NfcActivity {

    public static final String READ_RESULT_VALUE = "nfc_read_result_value";

    private String valueRead;

    @Override
    protected void initFields() {
        super.initFields();
        this.dataTypeForFilter = getDataTypeForFilter();
    }

    private String getDataTypeForFilter() {
        if (userSpecifiedType.equals("text")) {
            return "text/plain";
        } else {
            return userSpecifiedDomain + ":" + userSpecifiedType;
        }
    }

    @Override
    protected void dispatchActionOnTag(Tag tag) {
        readFromNfcTag(tag);
    }

    private void readFromNfcTag(Tag tag) {
        Ndef ndefObject = Ndef.get(tag);
        try {
            ndefObject.connect();
            NdefMessage msg = ndefObject.getNdefMessage();
            NdefRecord firstRecord = msg.getRecords()[0];
            this.valueRead = NdefRecordUtil.readValueFromRecord(firstRecord);
            ndefObject.close();
            if (valueRead == null) {
                finishWithErrorToast("nfc.read.unexpected.format");
            } else {
                finishWithToast("nfc.read.success", true);
            }
        } catch (IOException e) {
            finishWithErrorToast("nfc.read.io.error");
        } catch (FormatException e) {
            finishWithErrorToast("nfc.read.msg.malformed");
        }
    }

    @Override
    protected void setResultExtrasBundle(Intent i, boolean success) {
        Bundle responses = new Bundle();
        responses.putString("nfc_read_result", success ? "success" : "failure");
        i.putExtra(IntentCallout.INTENT_RESULT_EXTRAS_BUNDLE, responses);
    }

    @Override
    protected void setResultValue(Intent i) {
        i.putExtra(IntentCallout.INTENT_RESULT_VALUE, this.valueRead);
    }

    @Override
    protected String getInstructionsTextKey() {
        return "nfc.instructions.read";
    }
}

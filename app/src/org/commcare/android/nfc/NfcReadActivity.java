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
import android.util.Pair;

import org.commcare.android.javarosa.IntentCallout;

import java.io.IOException;

/**
 * Created by amstone326 on 9/5/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NfcReadActivity extends NfcActivity {

    private String valueRead;

    @Override
    protected void initFields() {
        super.initFields();
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
            if (msg == null) {
                finishWithErrorToast("nfc.read.no.data");
                return;
            }
            NdefRecord firstRecord = msg.getRecords()[0];
            Pair<String, Boolean> resultAndSuccess =
                    NdefRecordUtil.readValueFromRecord(firstRecord, this.userSpecifiedType,
                            this.userSpecifiedDomain);
            if (resultAndSuccess.second) {
                this.valueRead = resultAndSuccess.first;
                finishWithToast("nfc.read.success", true);
            } else {
                finishWithErrorToast(resultAndSuccess.first);
            }
        } catch (IOException e) {
            finishWithErrorToast("nfc.read.io.error");
        } catch (FormatException e) {
            finishWithErrorToast("nfc.read.msg.malformed");
        } finally {
            try {
                ndefObject.close();
            } catch (IOException e) {
                // nothing we can do
            }
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

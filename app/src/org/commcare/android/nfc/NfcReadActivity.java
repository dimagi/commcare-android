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
 * When this activity is in the foreground, any NFC tag scanned by the device that is of a format
 * CommCare recognizes (as determined by the filters in NfcActivity::setReadyToHandleTag) will be
 * consumed by this activity. If the tag is of the specified/expected type(s), then this activity
 * will attempt to read the tag. If the read is successful, the value read will be stored as an
 * extra in the activity's result intent under the key "odk_intent_data", where it can be accessed
 * by other activities. Otherwise, an appropriate error toast will be shown.
 *
 * @author Aliza Stone
 */
public class NfcReadActivity extends NfcActivity {

    private String valueRead;
    private String[] acceptableTypes;

    @Override
    protected void initFields() {
        super.initFields();
        this.acceptableTypes = parseTypes(getIntent());
    }

    protected static String[] parseTypes(Intent i) {
        String typesString = i.getStringExtra(NFC_PAYLOAD_MULT_TYPES_ARG);
        if (typesString == null || typesString.equals("")) {
            String singleType = i.getStringExtra(NFC_PAYLOAD_SINGLE_TYPE_ARG);
            if (singleType == null || singleType.equals("")) {
                return null;
            } else {
                return new String[]{ singleType };
            }
        } else {
            return typesString.split(" ");
        }
    }

    @Override
    protected boolean requiredFieldsMissing() {
        if (this.acceptableTypes == null || this.acceptableTypes.length == 0) {
            finishWithErrorToast("nfc.read.no.type");
            return true;
        }
        return false;
    }

    @Override
    protected void dispatchActionOnTag(Tag tag) {
        readFromNfcTag(tag);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
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
                    NdefRecordUtil.readValueFromRecord(firstRecord, this.acceptableTypes,
                            this.domainForType);
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
    protected void setResultValue(Intent i, boolean success) {
        if (success) {
            i.putExtra(IntentCallout.INTENT_RESULT_VALUE, this.valueRead);
        }
    }

    @Override
    protected String getInstructionsTextKey() {
        return "nfc.instructions.read";
    }

}

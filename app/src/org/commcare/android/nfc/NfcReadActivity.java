package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;

import java.io.IOException;

/**
 * Created by amstone326 on 9/5/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NfcReadActivity extends NfcActivity {

    public static final String READ_RESULT_VALUE = "nfc_read_result_value";

    private String valueRead;

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
            this.valueRead = new String(firstRecord.getPayload(), CHARSET_ENCODING);
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

    }

    @Override
    protected void setResultValue(Intent i) {

    }

    @Override
    protected String getInstructionsTextKey() {
        return "nfc.instructions.read";
    }
}

package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.content.Intent;
import android.nfc.Tag;
import android.os.Build;

/**
 * Created by amstone326 on 9/5/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NfcReadActivity extends NfcActivity {

    public static final String VALUE_READ = "value-read";

    private String expectedPayloadType;

    @Override
    protected void initFields() {
        this.expectedPayloadType = getIntent().getStringExtra(NfcManager.NFC_PAYLOAD_TYPE);
    }

    @Override
    protected boolean requiredFieldsMissing() {
        if (this.expectedPayloadType == null || this.expectedPayloadType.equals("")) {
            finishWithErrorToast("nfc.read.no.type");
            return true;
        }
        return false;
    }

    @Override
    protected void dispatchActionOnTag(Tag tag) {

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

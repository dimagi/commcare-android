package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import org.javarosa.core.services.locale.Localization;

import java.io.IOException;

/**
 * Created by amstone326 on 9/5/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NfcWriteExternalActivity extends Activity {

    public static final String NFC_PAYLOAD_TO_WRITE = "nfc-payload-to-write";
    public static final String NFC_PAYLOAD_TYPE = "nfc-payload-type";

    private NfcManager nfcManager;
    private PendingIntent pendingNfcIntent;
    private String payloadToWrite;
    private String customPayloadType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.nfcManager = new NfcManager(this);
        this.payloadToWrite = getIntent().getStringExtra(NFC_PAYLOAD_TO_WRITE);
        this.customPayloadType = getIntent().getStringExtra(NFC_PAYLOAD_TYPE);
        createPendingRestartIntent();
        //TODO: set a simple layout that instructs the user to hold the NFC-enabled hardware up to the phone/tablet
    }

    /**
     * Create an intent for restarting this activity, which will be passed to enableForegroundDispatch(),
     * thus instructing Android to start the intent when the device detects a new NFC tag. Adding
     * FLAG_ACTIVITY_SINGLE_TOP makes it so that onNewIntent() can be called in this activity when
     * the intent is started.
     **/
    private void createPendingRestartIntent() {
        Intent i = new Intent(this, getClass());
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        this.pendingNfcIntent = PendingIntent.getActivity(this, 0, i, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            nfcManager.verifyNFC();
            if (requiredFieldsMissing()) {
                return;
            }
            setReadyToHandleTag();
        } catch (NfcManager.NfcNotEnabledException e) {
            finishWithErrorToast("nfc.not.enabled");
        } catch (NfcManager.NfcNotSupportedException e) {
            finishWithErrorToast("nfc.not.supported");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.nfcManager.disableForegroundDispatch(this);
    }

    private boolean requiredFieldsMissing() {
        if (this.payloadToWrite == null || this.payloadToWrite.equals("")) {
            finishWithErrorToast("nfc.write.no.payload");
            return true;
        }
        if (this.customPayloadType == null || this.customPayloadType.equals("")) {
            finishWithErrorToast("nfc.write.no.type");
            return true;
        }
        return false;
    }

    /**
     * Make it so that this activity will be the default to handle a new tag when it is discovered
     */
    private void setReadyToHandleTag() {
        IntentFilter ndefDiscoveredFilter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        IntentFilter tagDiscoveredFilter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);

        IntentFilter[] intentFilters = new IntentFilter[]{ ndefDiscoveredFilter, tagDiscoveredFilter };
        this.nfcManager.enableForegroundDispatch(this, this.pendingNfcIntent, intentFilters, null);
    }

    /**
     * Once setReadyToHandleTag() has been called in this activity, Android will pass any
     * discovered tags to this activity through this method
     * @param intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        writeMessageToNfcTag(tag);
    }

    private void writeMessageToNfcTag(Tag tag) {
        NdefRecord record = createNdefRecord(this, this.customPayloadType, this.payloadToWrite);
        NdefMessage msg = new NdefMessage(new NdefRecord[]{record});

        Ndef ndefObject = Ndef.get(tag);
        try {
            ndefObject.connect();
            ndefObject.writeNdefMessage(msg);
            ndefObject.close();
        } catch (IOException e) {
            finishWithErrorToast("nfc.write.io.error");
        } catch (FormatException e) {
            finishWithErrorToast("nfc.write.msg.malformed");
        }
    }

    private static NdefRecord createNdefRecord(Context context, String type, String payloadToWrite) {
        return NdefRecord.createExternal(context.getPackageName(), type, payloadToWrite.getBytes());
    }

    private void finishWithErrorToast(String errorMessageKey) {
        Toast.makeText(this, Localization.get(errorMessageKey), Toast.LENGTH_SHORT);
        finish();
    }

}

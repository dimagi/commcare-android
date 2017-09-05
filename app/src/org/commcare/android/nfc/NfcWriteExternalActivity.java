package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 9/5/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NfcWriteExternalActivity extends Activity {

    private NfcManager nfcManager;
    private PendingIntent pendingNfcIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.nfcManager = new NfcManager(this);
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
            setReadyToReceiveTag();
        } catch (NfcManager.NfcNotEnabledException e) {
            Toast.makeText(this, Localization.get("nfc.not.enabled"), Toast.LENGTH_SHORT);
            finish();
        } catch (NfcManager.NfcNotSupportedException e) {
            Toast.makeText(this, Localization.get("nfc.not.supported"), Toast.LENGTH_SHORT);
            finish();
        }
    }

    /**
     * Make it so that this activity will be the default to handle a new tag when it is discovered
     */
    private void setReadyToReceiveTag() {
        IntentFilter[] intentFilters = new IntentFilter[]{}; // TODO: figure out what should go here
        this.nfcManager.enableForegroundDispatch(this, this.pendingNfcIntent, intentFilters, null);
    }

    /**
     * Once setReadyToReceiveTag() has been called in this activity, Android will pass any
     * discovered tags to this activity through this method
     * @param intent
     */
    @Override
    protected void onNewIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
    }

}

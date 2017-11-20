package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.support.v7.app.AppCompatActivity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NfcManager {

    private Context context;
    private NfcAdapter nfcAdapter;

    public NfcManager(Context c) {
        this.context = c;
    }

    public void checkForNFCSupport() throws NfcNotSupportedException, NfcNotEnabledException {
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(this.context);
        if (nfcAdapter == null)
            throw new NfcNotSupportedException();
        if (!nfcAdapter.isEnabled())
            throw new NfcNotEnabledException();
    }

    public void enableForegroundDispatch(AppCompatActivity activity, PendingIntent intent,
                                                    IntentFilter[] filters, String[][] techLists) {
        this.nfcAdapter.enableForegroundDispatch(activity,intent, filters, techLists);
    }

    public void disableForegroundDispatch(AppCompatActivity activity) {
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(activity);
        }
    }

    public class NfcNotSupportedException extends Exception {

    }

    public class NfcNotEnabledException extends Exception {

    }
}
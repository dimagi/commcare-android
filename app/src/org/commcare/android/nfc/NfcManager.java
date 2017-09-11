package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentFilter;
import android.nfc.NdefRecord;
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

    public void enableForegroundDispatch(Activity activity, PendingIntent intent,
                                                    IntentFilter[] filters, String[][] techLists) {
        this.nfcAdapter.enableForegroundDispatch(activity,intent, filters, techLists);
    }

    public void disableForegroundDispatch(Activity activity) {
        nfcAdapter.disableForegroundDispatch(activity);
    }

    protected static boolean isCommCareSupportedWellKnownType(String type) {
        // For now, the only "well known type" we're supporting is NdefRecord.RTD_TEXT, which
        // users should encode in their configuration by specifying type "text"
        return "text".equals(type);
    }

    public class NfcNotSupportedException extends Exception {

    }

    public class NfcNotEnabledException extends Exception {

    }
}
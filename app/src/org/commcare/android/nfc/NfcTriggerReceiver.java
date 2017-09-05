package org.commcare.android.nfc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 9/5/17.
 */

public class NfcTriggerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            String action = intent.getAction();
            if (action.endsWith("WRITE")) {
                startWriteActivity(context);
            } else if (action.endsWith("READ")) {
                startReadActivity(context);
            }
        } else {
            Toast.makeText(context, Localization.get("nfc.min.version.message"), Toast.LENGTH_LONG);
        }
    }

    private void startWriteActivity(Context context) {
        context.startActivity(new Intent(context, NfcWriteExternalActivity.class));
    }

    private void startReadActivity(Context context) {
        context.startActivity(new Intent(context, NfcReadActivity.class));
    }
}

package org.commcare.connect;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import androidx.activity.result.ActivityResultLauncher;

/**
 * BroadcastReceiver to wait for SMS messages using the SMS User Consent API.
 * Should be registered dynamically, not in the manifest.
 */
public class SMSBroadcastReceiver extends BroadcastReceiver {

    private final ActivityResultLauncher<Intent> smsConsentLauncher;

    public SMSBroadcastReceiver(ActivityResultLauncher<Intent> smsConsentLauncher) {
        this.smsConsentLauncher = smsConsentLauncher;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
            return;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        Status status = (Status)extras.get(SmsRetriever.EXTRA_STATUS);
        if (status == null) {
            return;
        }

        switch (status.getStatusCode()) {
            case CommonStatusCodes.SUCCESS:
                Intent consentIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
                try {
                    smsConsentLauncher.launch(consentIntent);
                } catch (ActivityNotFoundException e) {
                    Logger.log(LogTypes.TYPE_EXCEPTION, "No app to handle SMS: " + e.getMessage());
                }
                break;

            case CommonStatusCodes.TIMEOUT:
            case CommonStatusCodes.ERROR:
                Logger.log(LogTypes.TYPE_EXCEPTION, "SMS retrieval failed with status: " + status);
                break;
        }
    }
}

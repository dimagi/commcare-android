package org.commcare.connect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;


/**
 * BroadcastReceiver to wait for SMS messages. This can be registered either
 * in the AndroidManifest or at runtime.  Should filter Intents on
 * SmsRetriever.SMS_RETRIEVED_ACTION.
 */
public class SMSBroadcastReceiver extends BroadcastReceiver {
    private volatile SMSListener smsListener;

    public void setSmsListener(SMSListener listener) {
        this.smsListener = listener;
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            Status status = null;
            if (extras != null) {
                status = (Status)extras.get(SmsRetriever.EXTRA_STATUS);
            }

            if (status != null) {
                if (status.getStatusCode() == CommonStatusCodes.SUCCESS) {// Get SMS message contents
                    Intent messageIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
                    // Extract one-time code from the message and complete verification
                    // by sending the code back to your server.
                    if (messageIntent != null && smsListener != null)
                        smsListener.onSuccess(messageIntent);
                }
            }
        }

    }

}

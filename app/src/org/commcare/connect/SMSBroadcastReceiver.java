package org.commcare.connect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

/**
 * BroadcastReceiver to wait for SMS messages using the SMS User Consent API.
 * Should be registered dynamically, not in the manifest.
 */
public class SMSBroadcastReceiver extends BroadcastReceiver {

    public interface SMSListener {
        void onSuccess(Intent consentIntent);
        void onFailure(int statusCode);
    }

    private SMSListener smsListener;

    public SMSBroadcastReceiver() {
    }

    public void setSmsListener(SMSListener listener) {
       this.smsListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) return;

        Bundle extras = intent.getExtras();
        if (extras == null) return;

        Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
        if (status == null) return;

        switch (status.getStatusCode()) {
            case CommonStatusCodes.SUCCESS:
                Intent consentIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
                if (consentIntent != null && smsListener != null) {
                    smsListener.onSuccess(consentIntent);
                }
                break;

            case CommonStatusCodes.TIMEOUT:
            case CommonStatusCodes.ERROR:
                if (smsListener != null) {
                    smsListener.onFailure(status.getStatusCode());
                }
                break;
        }
    }
}

package org.commcare.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class PaymentAcknowledgeNoReceiver extends BroadcastReceiver {

    String paymentId = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        paymentId = intent.getStringExtra(CommCareFirebaseMessagingService.PAYMENT_ID);
        CommCareFirebaseMessagingService.clearNotification(context);
        Toast.makeText(context, "No clicked", Toast.LENGTH_SHORT).show();
    }
}

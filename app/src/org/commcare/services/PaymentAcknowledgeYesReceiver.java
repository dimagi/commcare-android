package org.commcare.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class PaymentAcknowledgeYesReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Handle Yes button click here, call API function
        // For example:
        Toast.makeText(context, "Yes clicked", Toast.LENGTH_SHORT).show();
        // Call your API function here
    }
}

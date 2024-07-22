package org.commcare.services;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.dalvik.R;

public class PaymentAcknowledgeNoReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Handle No button click here
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(R.string.fcm_notification);
        }
        Toast.makeText(context, "No clicked, notification removed", Toast.LENGTH_SHORT).show();
    }
}

package org.commcare.services;

import static org.commcare.sync.FirebaseMessagingDataSyncer.FCM_MESSAGE_DATA;
import static org.commcare.sync.FirebaseMessagingDataSyncer.FCM_MESSAGE_DATA_KEY;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.utils.FirebaseMessagingUtil;

public class PendingSyncAlertBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.hasExtra(FCM_MESSAGE_DATA_KEY)) {
            Bundle b = intent.getBundleExtra(FCM_MESSAGE_DATA_KEY);
            FCMMessageData fcmMessageData = FirebaseMessagingUtil.deserializeFCMMessageData(
                    (String) b.getSerializable(FCM_MESSAGE_DATA));

            ((CommCareActivity) context).alertPendingSync(fcmMessageData);
        }

    }
}

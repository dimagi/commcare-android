package org.commcare.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.sync.FirebaseMessagingDataSyncer;
import org.commcare.util.LogTypes;
import org.commcare.utils.FirebaseMessagingUtil;
import org.javarosa.core.services.Logger;

import java.util.Map;

/**
 * This service responds to any events/messages from Firebase Cloud Messaging. The intention is to
 * offer an entry point for any message from FCM and trigger the necessary steps based on the action
 * key.
 */
public class CommCareFirebaseMessagingService extends FirebaseMessagingService {

    private final static int FCM_NOTIFICATION = R.string.fcm_notification;

    enum ActionTypes {
        SYNC,
        INVALID
    }

    private FirebaseMessagingDataSyncer dataSyncer;

    {
        dataSyncer = new FirebaseMessagingDataSyncer(this);
    }

    /**
     * Upon receiving a new message from FCM, CommCare needs to:
     * 1) Trigger the notification if the message contains a Notification object. Note that the
     * presence of a Notification object causes the onMessageReceived to not be called when the
     * app is in the background, which means that the data object won't be processed from here
     * 2) Verify if the message contains a data object and trigger the necessary steps according
     * to the action it carries
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Logger.log(LogTypes.TYPE_FCM, "CommCareFirebaseMessagingService Message received: " + remoteMessage.getData());
        Map<String, String> payloadData = remoteMessage.getData();

        // Check if the message contains a data object, there is no further action if not
        if (payloadData.isEmpty()) {
            return;
        }

        showNotification(payloadData);
        FCMMessageData fcmMessageData;
        if (!hasCccAction(payloadData)) {
            fcmMessageData = new FCMMessageData(payloadData);

            switch (fcmMessageData.getAction()) {
                case SYNC -> dataSyncer.syncData(fcmMessageData);
                default -> Logger.log(LogTypes.TYPE_FCM, "Invalid FCM action");
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        // TODO: Remove the token from the log
        Logger.log(LogTypes.TYPE_FCM, "New registration token was generated: " + token);
        FirebaseMessagingUtil.updateFCMToken(token);
    }


    /**
     * This method purpose is to show notifications to the user when the app is in the foreground.
     * When the app is in the background, FCM is responsible for notifying the user
     */
    private void showNotification(Map<String, String> payloadData) {
        String notificationTitle = payloadData.get("title");
        String notificationText = payloadData.get("body");
        NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent intent;

        if (hasCccAction(payloadData)) {
            FirebaseAnalyticsUtil.reportNotificationType(payloadData.get("action"));
            intent = new Intent(getApplicationContext(), ConnectActivity.class);
            intent.putExtra("action", payloadData.get("action"));
            intent.putExtra("opportunity_id", payloadData.get("opportunity_id"));
        } else {
            intent = new Intent(this, DispatchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT
                : PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT;

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);

        // Yes button intent
        Intent yesIntent = new Intent(this, PaymentAcknowledgeYesReceiver.class);
        PendingIntent yesPendingIntent = PendingIntent.getBroadcast(this, 0, yesIntent, flags);

        // No button intent
        Intent noIntent = new Intent(this, PaymentAcknowledgeNoReceiver.class);
        PendingIntent noPendingIntent = PendingIntent.getBroadcast(this, 0, noIntent, flags);


        NotificationCompat.Builder fcmNotification = new NotificationCompat.Builder(this,
                CommCareNoficationManager.NOTIFICATION_CHANNEL_PUSH_NOTIFICATIONS_ID)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis())
                .addAction(0, "Yes", yesPendingIntent)
                .addAction(0, "No", noPendingIntent);

        mNM.notify(FCM_NOTIFICATION, fcmNotification.build());
    }

    private boolean hasCccAction(Map<String, String> payloadData) {
        String action = payloadData.get("action");
        return action != null && action.contains("ccc_");
    }
}

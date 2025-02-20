package org.commcare.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.connect.ConnectMessagingActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.MessageManager;
import org.commcare.connect.database.ConnectMessageUtils;
import org.commcare.dalvik.R;
import org.commcare.fragments.connectMessaging.ConnectMessageChannelListFragment;
import org.commcare.fragments.connectMessaging.ConnectMessageFragment;
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
    public static final String OPPORTUNITY_ID = "opportunity_id";
    private final static int FCM_NOTIFICATION = R.string.fcm_notification;
    public static final String MESSAGING_UPDATE_BROADCAST = "com.dimagi.messaging.update";
    public static final String PAYMENT_ID = "payment_id";
    public static final String PAYMENT_STATUS = "payment_status";
    private static final String CCC_ACTION_PREFIX = "ccc_";

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
        Map<String, String> payloadData = remoteMessage.getData();

        // Check if the message contains a data object, there is no further action if not
        if (payloadData.isEmpty()) {
            return;
        }

        showNotification(payloadData);

        if (!hasCccAction(payloadData.get("action"))) {
            FCMMessageData fcmMessageData = new FCMMessageData(payloadData);

            switch (fcmMessageData.getAction()) {
                case SYNC -> dataSyncer.syncData(fcmMessageData);
                default -> Logger.log(LogTypes.TYPE_FCM, "Invalid FCM action");
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        FirebaseMessagingUtil.updateFCMToken(token);
    }


    /**
     * This method purpose is to show notifications to the user when the app is in the foreground.
     * When the app is in the background, FCM is responsible for notifying the user
     */
    private void showNotification(Map<String, String> payloadData) {
        String notificationTitle = payloadData.get("title");
        String notificationText = payloadData.get("body");
        Intent intent = null;
        String action = payloadData.get("action");
        if (hasCccAction(action)) {
            FirebaseAnalyticsUtil.reportNotificationType(action);
            if (action.equals(ConnectMessagingActivity.CCC_MESSAGE)) {
                // Instead of handling the message notification inline,
                // delegate to a helper method for clarity.
                handleMessageNotification(payloadData, action);
                return;
            } else {
                //Intent for ConnectActivity
//                intent = new Intent(getApplicationContext(), ConnectActivity.class);
//                intent.putExtra("action", action);
//                if(payloadData.containsKey(OPPORTUNITY_ID)) {
//                    intent.putExtra(OPPORTUNITY_ID, payloadData.get(OPPORTUNITY_ID));
//                }
            }
        } else {
            intent = new Intent(this, DispatchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK);
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);
            NotificationCompat.Builder fcmNotification = new NotificationCompat.Builder(this,
                    CommCareNoficationManager.NOTIFICATION_CHANNEL_PUSH_NOTIFICATIONS_ID)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationText)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setSmallIcon(R.drawable.commcare_actionbar_logo)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setWhen(System.currentTimeMillis());
            // Check if the payload action is CCC_PAYMENTS
//            if (action.equals(ConnectConstants.CCC_DEST_PAYMENTS)) {
//                // Yes button intent with payment_id from payload
//                Intent yesIntent = new Intent(this, PaymentAcknowledgeReceiver.class);
//                yesIntent.putExtra(OPPORTUNITY_ID, payloadData.get(OPPORTUNITY_ID));
//                yesIntent.putExtra(PAYMENT_ID, payloadData.get(PAYMENT_ID));
//                yesIntent.putExtra(PAYMENT_STATUS, true);
//                PendingIntent yesPendingIntent = PendingIntent.getBroadcast(this, 1,
//                        yesIntent, flags);
//
//                // No button intent with payment_id from payload
//                Intent noIntent = new Intent(this, PaymentAcknowledgeReceiver.class);
//                noIntent.putExtra(OPPORTUNITY_ID, payloadData.get(OPPORTUNITY_ID));
//                noIntent.putExtra(PAYMENT_ID, payloadData.get(PAYMENT_ID));
//                noIntent.putExtra(PAYMENT_STATUS, false);
//                PendingIntent noPendingIntent = PendingIntent.getBroadcast(this, 2,
//                        noIntent, flags);
//
//                // Add Yes & No action button to the notification
//                fcmNotification.addAction(0, getString(R.string.connect_payment_acknowledge_notification_yes), yesPendingIntent);
//                fcmNotification.addAction(0, getString(R.string.connect_payment_acknowledge_notification_no), noPendingIntent);
//            }
            NotificationManager mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            mNM.notify(FCM_NOTIFICATION, fcmNotification.build());
        }
    }

    // New helper method to create the base notification.
    private NotificationCompat.Builder createBaseNotification(String title, String text, PendingIntent intent) {
        return new NotificationCompat.Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_PUSH_NOTIFICATIONS_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(intent)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.commcare_actionbar_logo)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(System.currentTimeMillis());
    }

    // New helper method to handle message-related notifications.
    private void handleMessageNotification(Map<String, String> payloadData, String action) {
        boolean isMessage = payloadData.containsKey(ConnectMessagingMessageRecord.META_MESSAGE_ID);
        String channelId;
        String notificationTitle;
        String notificationMessage;
        if (isMessage) {
            ConnectMessagingMessageRecord message = MessageManager.handleReceivedMessage(this, payloadData);
            if (message == null) {
                Logger.log(LogTypes.TYPE_FCM, "Ignoring message without known consented channel: " +
                        payloadData.get(ConnectMessagingMessageRecord.META_MESSAGE_ID));
                return;
            }
            ConnectMessagingChannelRecord channel = ConnectMessageUtils.getMessagingChannel(this, message.getChannelId());
            notificationTitle = getString(R.string.connect_messaging_message_notification_title);
            notificationMessage = getString(R.string.connect_messaging_message_notification_message, channel.getChannelName());
            channelId = message.getChannelId();
        } else {
            ConnectMessagingChannelRecord channel = MessageManager.handleReceivedChannel(this, payloadData);
            notificationTitle = getString(R.string.connect_messaging_channel_notification_title);
            notificationMessage = getString(R.string.connect_messaging_channel_notification_message, channel.getChannelName());
            channelId = channel.getChannelId();
        }
        Intent broadcastIntent = new Intent(MESSAGING_UPDATE_BROADCAST);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
        if (!ConnectMessageChannelListFragment.isActive &&
                !channelId.equals(ConnectMessageFragment.activeChannel)) {
            Intent intent = new Intent(getApplicationContext(), ConnectMessagingActivity.class);
            intent.putExtra("action", action);
            intent.putExtra(ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID, channelId);
            showNotificationWithIntent(intent, notificationTitle, notificationMessage);
        }
    }

    // New helper method to show the notification using a given intent.
    private void showNotificationWithIntent(Intent intent, String title, String text) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);
        NotificationCompat.Builder builder = createBaseNotification(title, text, contentIntent);
        NotificationManager mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mNM.notify(FCM_NOTIFICATION, builder.build());
    }

    private boolean hasCccAction(String action) {
        return action != null && action.contains(CCC_ACTION_PREFIX);
    }

    public static void clearNotification(Context context) {
        NotificationManager notificationManager = (NotificationManager)context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(R.string.fcm_notification);
        }
    }
}

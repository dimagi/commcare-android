package org.commcare.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.activities.connect.ConnectMessagingActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.MessageManager;
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

    private final static int FCM_NOTIFICATION = R.string.fcm_notification;
    public static final String MESSAGING_UPDATE_BROADCAST = "com.dimagi.messaging.update";
    public static final String OPPORTUNITY_ID = "opportunity_id";
    public static final String PAYMENT_ID = "payment_id";
    public static final String PAYMENT_STATUS = "payment_status";

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
        Logger.log(LogTypes.TYPE_FCM, "CommCareFirebaseMessagingService Message received: " +
                remoteMessage.getData());
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

        Intent intent = null;
        String action = payloadData.get("action");
        String notificationChannel = CommCareNoficationManager.NOTIFICATION_CHANNEL_PUSH_NOTIFICATIONS_ID;
        int priority = NotificationCompat.PRIORITY_HIGH;
        Bitmap largeIcon = null;

        if (hasCccAction(action)) {
            FirebaseAnalyticsUtil.reportNotificationType(action);

            if(action.equals(ConnectMessagingActivity.CCC_MESSAGE)) {
                notificationChannel = CommCareNoficationManager.NOTIFICATION_CHANNEL_MESSAGING_ID;
                priority = NotificationCompat.PRIORITY_MAX;
                largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.commcare_actionbar_logo);

                boolean isMessage = payloadData.containsKey(ConnectMessagingMessageRecord.META_MESSAGE_ID);

                //Don't show a notification in some cases:
                //Can't decrypt message (no key)
                //On channels page (just update the page)
                //On message page for the active channel

                int notificationTitleId;
                String notificationMessage;
                String channelId;
                if(isMessage) {
                    ConnectMessagingMessageRecord message = MessageManager.handleReceivedMessage(this,
                            payloadData);

                    if(message == null) {
                        Logger.log(LogTypes.TYPE_FCM, "Ignoring message without known consented channel: " +
                                payloadData.get(ConnectMessagingMessageRecord.META_MESSAGE_ID));
                        //End now to avoid showing a notification
                        return;
                    }

                    ConnectMessagingChannelRecord channel = ConnectDatabaseHelper.getMessagingChannel(this,
                            message.getChannelId());

                    notificationTitleId = R.string.connect_messaging_message_notification_title;
                    notificationMessage = getString(R.string.connect_messaging_message_notification_message,
                            channel.getChannelName());

                    channelId = message.getChannelId();
                } else {
                    //Channel
                    ConnectMessagingChannelRecord channel = MessageManager.handleReceivedChannel(this,
                            payloadData);

                    notificationTitleId = R.string.connect_messaging_channel_notification_title;
                    notificationMessage = getString(R.string.connect_messaging_channel_notification_message,
                            channel.getChannelName());

                    channelId = channel.getChannelId();
                }

                //Send broadcast so any interested pages can update their UI
                Intent broadcastIntent = new Intent(MESSAGING_UPDATE_BROADCAST);
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);

                if(!ConnectMessageChannelListFragment.isActive &&
                        !channelId.equals(ConnectMessageFragment.activeChannel)) {
                    //Show push notification
                    notificationTitle = getString(notificationTitleId);
                    notificationText = notificationMessage;

                    intent = new Intent(getApplicationContext(), ConnectMessagingActivity.class);
                    intent.putExtra("action", action);
                    intent.putExtra(ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID, channelId);
                }
            } else {
                //Intent for ConnectActivity
                intent = new Intent(getApplicationContext(), ConnectActivity.class);
                intent.putExtra("action", action);
                if(payloadData.containsKey(OPPORTUNITY_ID)) {
                    intent.putExtra(OPPORTUNITY_ID, payloadData.get(OPPORTUNITY_ID));
                }
            }
        } else {
            intent = new Intent(this, DispatchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }

        if(intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK);

            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT;

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);

            NotificationCompat.Builder fcmNotification = new NotificationCompat.Builder(this,
                    notificationChannel)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationText)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setSmallIcon(R.drawable.commcare_actionbar_logo)
                    .setPriority(priority)
                    .setWhen(System.currentTimeMillis());

            if(largeIcon != null) {
                fcmNotification.setLargeIcon(largeIcon);
            }

            // Check if the payload action is CCC_PAYMENTS
            if (action.equals(ConnectConstants.CCC_DEST_PAYMENTS)) {
                // Yes button intent with payment_id from payload
                Intent yesIntent = new Intent(this, PaymentAcknowledgeReceiver.class);
                yesIntent.putExtra(OPPORTUNITY_ID, payloadData.get(OPPORTUNITY_ID));
                yesIntent.putExtra(PAYMENT_ID, payloadData.get(PAYMENT_ID));
                yesIntent.putExtra(PAYMENT_STATUS, true);
                PendingIntent yesPendingIntent = PendingIntent.getBroadcast(this, 1,
                        yesIntent, flags);

                // No button intent with payment_id from payload
                Intent noIntent = new Intent(this, PaymentAcknowledgeReceiver.class);
                noIntent.putExtra(OPPORTUNITY_ID, payloadData.get(OPPORTUNITY_ID));
                noIntent.putExtra(PAYMENT_ID, payloadData.get(PAYMENT_ID));
                noIntent.putExtra(PAYMENT_STATUS, false);
                PendingIntent noPendingIntent = PendingIntent.getBroadcast(this, 2,
                        noIntent, flags);

                // Add Yes & No action button to the notification
                fcmNotification.addAction(0, getString(R.string.connect_payment_acknowledge_notification_yes), yesPendingIntent);
                fcmNotification.addAction(0, getString(R.string.connect_payment_acknowledge_notification_no), noPendingIntent);
            }

            NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            mNM.notify(FCM_NOTIFICATION, fcmNotification.build());
        }
    }

    private boolean hasCccAction(String action) {
        return action != null && action.contains("ccc_");
    }

    public static void clearNotification(Context context){
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(R.string.fcm_notification);
        }
    }
}

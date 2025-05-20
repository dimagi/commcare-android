package org.commcare.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.util.Strings;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.activities.connect.ConnectMessagingActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectConstants;
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

import static org.commcare.connect.ConnectConstants.CCC_MESSAGE;

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

        FCMMessageData fcmMessageData = new FCMMessageData(remoteMessage.getData());
        if (fcmMessageData.getPayloadData() == null || fcmMessageData.getPayloadData().isEmpty()) {
            sendGeneralApplicationPushNotification(fcmMessageData);
        }else if (hasCccAction(fcmMessageData.getAction())){
            sendCCCActionPushNotification(fcmMessageData);
        }else if (!hasCccAction(fcmMessageData.getAction()) && fcmMessageData.getActionType() == ActionTypes.SYNC){
            dataSyncer.syncData(fcmMessageData);
        }else{
            sendGeneralApplicationPushNotification(fcmMessageData);
        }
    }

    @Override
    public void onNewToken(String token) {
        // TODO: Remove the token from the log
        Logger.log(LogTypes.TYPE_FCM, "New registration token was generated: " + token);
        FirebaseMessagingUtil.updateFCMToken(token);
    }

    private void sendCCCActionPushNotification(FCMMessageData fcmMessageData){

        FirebaseAnalyticsUtil.reportNotificationType(fcmMessageData.getAction());

        if(fcmMessageData.getAction().equals(CCC_MESSAGE)){
            sendCCCMessageChannelPushNotification(fcmMessageData);
        }else if(fcmMessageData.getAction().equals(ConnectConstants.CCC_DEST_PAYMENTS)){
            sendCCCPaymentPushNotification(fcmMessageData);
        }else{  // All other notifications for connect
            sendCCCConnectPushNotification(fcmMessageData);
        }

    }

    private void sendCCCConnectPushNotification(FCMMessageData fcmMessageData){
        Intent intent = getConnectActivityNotification(fcmMessageData);
        showNotification(buildNotification(intent, fcmMessageData));
    }

    private void sendCCCPaymentPushNotification(FCMMessageData fcmMessageData){
        Intent intent = getConnectActivityNotification(fcmMessageData);

        NotificationCompat.Builder fcmNotification = buildNotification(intent, fcmMessageData);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        // Yes button intent with payment_id from payload
        Intent yesIntent = new Intent(this, PaymentAcknowledgeReceiver.class);
        yesIntent.putExtra(OPPORTUNITY_ID, fcmMessageData.getPayloadData().get(OPPORTUNITY_ID));
        yesIntent.putExtra(PAYMENT_ID, fcmMessageData.getPayloadData().get(PAYMENT_ID));
        yesIntent.putExtra(PAYMENT_STATUS, true);
        PendingIntent yesPendingIntent = PendingIntent.getBroadcast(this, 1,
                yesIntent, flags);

        // No button intent with payment_id from payload
        Intent noIntent = new Intent(this, PaymentAcknowledgeReceiver.class);
        noIntent.putExtra(OPPORTUNITY_ID, fcmMessageData.getPayloadData().get(OPPORTUNITY_ID));
        noIntent.putExtra(PAYMENT_ID, fcmMessageData.getPayloadData().get(PAYMENT_ID));
        noIntent.putExtra(PAYMENT_STATUS, false);
        PendingIntent noPendingIntent = PendingIntent.getBroadcast(this, 2,
                noIntent, flags);

        // Add Yes & No action button to the notification
        fcmNotification.addAction(0, getString(R.string.connect_payment_acknowledge_notification_yes), yesPendingIntent);
        fcmNotification.addAction(0, getString(R.string.connect_payment_acknowledge_notification_no), noPendingIntent);

        showNotification(fcmNotification);
    }

    private void sendGeneralApplicationPushNotification(FCMMessageData fcmMessageData){
        Intent intent = new Intent(this, DispatchActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        buildNotification(intent,fcmMessageData);
    }

    private void sendCCCMessageChannelPushNotification(FCMMessageData fcmMessageData){
        Intent intent = null;
        fcmMessageData.setNotificationChannel(CommCareNoficationManager.NOTIFICATION_CHANNEL_MESSAGING_ID);
        fcmMessageData.setPriority(NotificationCompat.PRIORITY_MAX);
        fcmMessageData.setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.ic_connect_message_large));

        boolean isMessage = fcmMessageData.getPayloadData().containsKey(ConnectMessagingMessageRecord.META_MESSAGE_ID);

        int notificationTitleId;
        String notificationMessage;
        String channelId;
        if(isMessage) {
            notificationTitleId = R.string.connect_messaging_message_notification_title;
            notificationMessage = getString(R.string.connect_messaging_message_notification_message,
                    fcmMessageData.getPayloadData().get("channel_name"));
            channelId = fcmMessageData.getPayloadData().get("channel");
        } else {
            //Channel
            // TODO to be removed when backend starts sending channel name in new channel push notification
            ConnectMessagingChannelRecord channel = MessageManager.handleReceivedChannel(this,
                    fcmMessageData.getPayloadData());

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
            fcmMessageData.setNotificationTitle(getString(notificationTitleId));
            fcmMessageData.setNotificationText(notificationMessage);

            intent = new Intent(getApplicationContext(), ConnectMessagingActivity.class);
            intent.putExtra(fcmMessageData.getAction(), fcmMessageData.getAction());
            intent.putExtra(ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID, channelId);
            intent.putExtra("action", fcmMessageData.getAction());
        }

        if(intent!=null){
            showNotification(buildNotification(intent, fcmMessageData));
        }

    }

    private Intent getConnectActivityNotification(FCMMessageData fcmMessageData){
        Intent intent = new Intent(getApplicationContext(), ConnectActivity.class);
        intent.putExtra("action", fcmMessageData.getAction());
        if(fcmMessageData.getPayloadData().containsKey(OPPORTUNITY_ID)) {
            intent.putExtra(OPPORTUNITY_ID, fcmMessageData.getPayloadData().get(OPPORTUNITY_ID));
        }
        return intent;
    }

    private NotificationCompat.Builder buildNotification(Intent intent, FCMMessageData fcmMessageData){
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);

        if (Strings.isEmptyOrWhitespace(fcmMessageData.getNotificationTitle()) && Strings.isEmptyOrWhitespace(fcmMessageData.getNotificationText())) {
            Logger.exception("Empty push notification",
                    new Throwable(String.format("Empty notification for action '%s'", fcmMessageData.getAction())));
        }

        NotificationCompat.Builder fcmNotification = new NotificationCompat.Builder(this,
                fcmMessageData.getNotificationChannel())
                .setContentTitle(fcmMessageData.getNotificationTitle())
                .setContentText(fcmMessageData.getNotificationText())
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.commcare_actionbar_logo)
                .setPriority(fcmMessageData.getPriority())
                .setWhen(System.currentTimeMillis());

        if (fcmMessageData.getLargeIcon() != null) {
            fcmNotification.setLargeIcon(fcmMessageData.getLargeIcon());
        }
        return fcmNotification;
    }

    private void showNotification(NotificationCompat.Builder notificationBuilder){
        NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNM.notify(FCM_NOTIFICATION, notificationBuilder.build());
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

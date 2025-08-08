package org.commcare.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.android.gms.common.util.Strings;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.activities.connect.ConnectMessagingActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.MessageManager;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.fragments.connectMessaging.ConnectMessageChannelListFragment;
import org.commcare.fragments.connectMessaging.ConnectMessageFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.services.FCMMessageData;
import org.commcare.services.PaymentAcknowledgeReceiver;
import org.commcare.sync.FirebaseMessagingDataSyncer;
import org.javarosa.core.services.Logger;

import java.util.HashMap;
import java.util.Map;

import static android.content.Context.NOTIFICATION_SERVICE;
import static org.commcare.connect.ConnectConstants.CCC_DEST_DELIVERY_PROGRESS;
import static org.commcare.connect.ConnectConstants.CCC_DEST_LEARN_PROGRESS;
import static org.commcare.connect.ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE;
import static org.commcare.connect.ConnectConstants.CCC_DEST_PAYMENTS;
import static org.commcare.connect.ConnectConstants.CCC_MESSAGE;
import static org.commcare.connect.ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION;
import static org.commcare.connect.ConnectConstants.OPPORTUNITY_ID;
import static org.commcare.connect.ConnectConstants.PAYMENT_ID;
import static org.commcare.connect.ConnectConstants.PAYMENT_STATUS;
import static org.commcare.connect.ConnectConstants.REDIRECT_ACTION;
import static org.commcare.services.FCMMessageData.NOTIFICATION_BODY;
import static org.commcare.services.FCMMessageData.NOTIFICATION_TITLE;

/**
 * This class will be used to handle notification whenever
 * 1. App receives notification when app is in foreground
 * 2. App receives notification when app is in background/killed and user clicks on such notification. Launcher activity will call
 * this class to handle the notification
 */
public class FirebaseMessagingUtil {
    public static final String FCM_TOKEN = "fcm_token";
    public static final String FCM_TOKEN_TIME = "fcm_token_time";
    private final static int FCM_NOTIFICATION = R.string.fcm_notification;
    public static final String MESSAGING_UPDATE_BROADCAST = "com.dimagi.messaging.update";


    public static String getFCMToken() {
        return PreferenceManager
                .getDefaultSharedPreferences(CommCareApplication.instance())
                .getString(FCM_TOKEN, null);
    }

    public static long getFCMTokenTime() {
        return PreferenceManager
                .getDefaultSharedPreferences(CommCareApplication.instance())
                .getLong(FCM_TOKEN_TIME, 0);
    }

    public static void updateFCMToken(String newToken) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(CommCareApplication.instance());
        sharedPreferences.edit().putString(FCM_TOKEN, newToken).apply();
        sharedPreferences.edit().putLong(FCM_TOKEN_TIME, System.currentTimeMillis()).apply();
    }

    public static void verifyToken() {
        // TODO: Enable FCM in debug mode
        if (!BuildConfig.DEBUG) {
            // Retrieve the current Firebase Cloud Messaging (FCM) registration token
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(handleFCMTokenRetrieval());
        }
    }

    private static OnCompleteListener handleFCMTokenRetrieval() {
        return (OnCompleteListener<String>)task -> {
            if (task.isSuccessful()) {
                updateFCMToken(task.getResult());
            } else {
                Logger.exception("Fetching FCM registration token failed", task.getException());
            }
        };
    }

    public static String serializeFCMMessageData(FCMMessageData fcmMessageData) {
        byte[] serializedMessageData = SerializationUtil.serialize(fcmMessageData);
        String base64EncodedMessageData = Base64.encodeToString(serializedMessageData, Base64.DEFAULT);
        return base64EncodedMessageData;
    }

    public static FCMMessageData deserializeFCMMessageData(String base64EncodedSerializedFCMMessageData) {
        if (base64EncodedSerializedFCMMessageData != null) {
            byte[] serializedMessageData = Base64.decode(base64EncodedSerializedFCMMessageData, Base64.DEFAULT);
            return SerializationUtil.deserialize(serializedMessageData, FCMMessageData.class);
        }
        return null;
    }


    /**
     * DataSyncer singleton class
     */
    private static FirebaseMessagingDataSyncer dataSyncer;

    private static FirebaseMessagingDataSyncer getDataSyncer(Context context) {
        if (dataSyncer == null) {
            synchronized (FirebaseMessagingUtil.class) {
                dataSyncer = new FirebaseMessagingDataSyncer(context);
            }
        }
        return dataSyncer;
    }


    /**
     * This is generic function used by both CommCareFirebaseMessagingService and launcher activity.
     *
     * @param context             - application context
     * @param dataPayload         - This will be data payload when calling from CommCareFirebaseMessagingService or will
     *                            be intent data payload when calling from launcher activity
     * @param notificationPayload - This will be notification payload when calling from CommCareFirebaseMessagingService and null when calling from launcher activity
     * @return Intent - if need for launcher activity to start the activity
     */
    public static Intent handleNotification(Context context, Map<String, String> dataPayload, RemoteMessage.Notification notificationPayload) {

        if (dataPayload == null || dataPayload.isEmpty()) {
            if (!showNotificationFromNotificationPayload(context, notificationPayload)) {
                Logger.exception("Empty push notification", new Throwable(String.format("Empty notification without payload")));
            }
            return null;
        }
        FCMMessageData fcmMessageData = new FCMMessageData(dataPayload);
        if (hasCccAction(fcmMessageData.getAction())) {
            return handleCCCActionPushNotification(context, fcmMessageData);
        } else if (fcmMessageData.getActionType() == FCMMessageData.ActionTypes.SYNC) {
            getDataSyncer(context).syncData(fcmMessageData);
            return null;
        } else {
            return handleGeneralApplicationPushNotification(context, fcmMessageData);
        }
    }

    /**
     * Show notification from notification payload
     *
     * @param context
     * @param payloadNotification
     * @return
     */
    private static boolean showNotificationFromNotificationPayload(Context context, RemoteMessage.Notification payloadNotification) {
        if (payloadNotification != null &&
                !Strings.isEmptyOrWhitespace(payloadNotification.getTitle()) && !Strings.isEmptyOrWhitespace(payloadNotification.getBody())) {
            Map<String, String> notificationPayload = new HashMap<>();
            notificationPayload.put(NOTIFICATION_TITLE, payloadNotification.getTitle());
            notificationPayload.put(NOTIFICATION_BODY, payloadNotification.getBody());
            handleGeneralApplicationPushNotification(context, new FCMMessageData(notificationPayload));
            return true;
        }
        return false;
    }

    /**
     * handle CCC action push notification
     *
     * @param context
     * @param fcmMessageData
     * @return Intent
     */
    private static Intent handleCCCActionPushNotification(Context context, FCMMessageData fcmMessageData) {
        FirebaseAnalyticsUtil.reportNotificationType(fcmMessageData.getAction());
        return switch (fcmMessageData.getAction()) {
            case CCC_MESSAGE -> handleCCCMessageChannelPushNotification(context, fcmMessageData);
            case CCC_DEST_PAYMENTS -> handleCCCPaymentPushNotification(context, fcmMessageData);
            case CCC_PAYMENT_INFO_CONFIRMATION ->
                    handleCCCPaymentInfoConfirmationPushNotification(context, fcmMessageData);
            case CCC_DEST_OPPORTUNITY_SUMMARY_PAGE ->
                    handleOpportunitySummaryPagePushNotification(context, fcmMessageData);
            case CCC_DEST_LEARN_PROGRESS ->
                    handleResumeLearningOrDeliveryJobPushNotification(true, context, fcmMessageData);
            case CCC_DEST_DELIVERY_PROGRESS ->
                    handleResumeLearningOrDeliveryJobPushNotification(false, context, fcmMessageData);
            default -> null;
        };
    }

    /**
     * Handle CCC payment push notification
     *
     * @param context
     * @param fcmMessageData
     * @return Intent
     */

    private static Intent handleCCCPaymentPushNotification(Context context, FCMMessageData fcmMessageData) {
        Intent intent = getConnectActivityNotification(context, fcmMessageData);

        NotificationCompat.Builder fcmNotification = buildNotification(context, intent, fcmMessageData);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        // Yes button intent with payment_id from payload
        Intent yesIntent = new Intent(context, PaymentAcknowledgeReceiver.class);
        yesIntent.putExtra(OPPORTUNITY_ID, fcmMessageData.getPayloadData().get(OPPORTUNITY_ID));
        yesIntent.putExtra(PAYMENT_ID, fcmMessageData.getPayloadData().get(PAYMENT_ID));
        yesIntent.putExtra(PAYMENT_STATUS, true);
        PendingIntent yesPendingIntent = PendingIntent.getBroadcast(context, 1,
                yesIntent, flags);

        // No button intent with payment_id from payload
        Intent noIntent = new Intent(context, PaymentAcknowledgeReceiver.class);
        noIntent.putExtra(OPPORTUNITY_ID, fcmMessageData.getPayloadData().get(OPPORTUNITY_ID));
        noIntent.putExtra(PAYMENT_ID, fcmMessageData.getPayloadData().get(PAYMENT_ID));
        noIntent.putExtra(PAYMENT_STATUS, false);
        PendingIntent noPendingIntent = PendingIntent.getBroadcast(context, 2,
                noIntent, flags);

        // Add Yes & No action button to the notification
        fcmNotification.addAction(0, context.getString(R.string.connect_payment_acknowledge_notification_yes), yesPendingIntent);
        fcmNotification.addAction(0, context.getString(R.string.connect_payment_acknowledge_notification_no), noPendingIntent);

        showNotification(context, fcmNotification);
        return intent;
    }


    /**
     * Handle CCC resume learning or delivery job push notification
     *
     * @param isLearning
     * @param context
     * @param fcmMessageData
     * @return
     */
    private static Intent handleResumeLearningOrDeliveryJobPushNotification(Boolean isLearning, Context context, FCMMessageData fcmMessageData) {
        if (fcmMessageData.getPayloadData().containsKey(OPPORTUNITY_ID)) {
            Intent intent = getConnectActivityNotification(context, fcmMessageData);
            intent.putExtra(OPPORTUNITY_ID, fcmMessageData.getPayloadData().get(OPPORTUNITY_ID));
            showNotification(context, buildNotification(context, intent, fcmMessageData));
            return intent;
        }
        String ccc_action = isLearning ? CCC_DEST_LEARN_PROGRESS : CCC_DEST_DELIVERY_PROGRESS;
        Logger.exception("Empty push notification for action '" + ccc_action + "'", new Throwable(String.format("Empty notification without 'opportunity_id'")));
        return null;
    }


    /**
     * Handle CCC opportunity summary page push notification
     *
     * @param context
     * @param fcmMessageData
     * @return
     */
    private static Intent handleOpportunitySummaryPagePushNotification(Context context, FCMMessageData fcmMessageData) {
        if (fcmMessageData.getPayloadData().containsKey(OPPORTUNITY_ID)) {
            Intent intent = getConnectActivityNotification(context, fcmMessageData);
            intent.putExtra(OPPORTUNITY_ID, fcmMessageData.getPayloadData().get(OPPORTUNITY_ID));
            showNotification(context, buildNotification(context, intent, fcmMessageData));
            return intent;
        }
        Logger.exception("Empty push notification for action 'ccc_opportunity_summary_page'", new Throwable(String.format("Empty notification without 'opportunity_id'")));
        return null;
    }


    /**
     * Handle CCC payment info confirmation push notification
     *
     * @param context
     * @param fcmMessageData
     * @return
     */
    private static Intent handleCCCPaymentInfoConfirmationPushNotification(Context context, FCMMessageData fcmMessageData) {
        if (fcmMessageData.getPayloadData().containsKey(ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION_STATUS)) {
            Intent intent = getConnectActivityNotification(context, fcmMessageData);
            intent.putExtra(ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION_STATUS, fcmMessageData.getPayloadData().get(ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION_STATUS));
            showNotification(context, buildNotification(context, intent, fcmMessageData));
            return intent;
        }
        Logger.exception("Empty push notification for action 'ccc_payment_info_confirmation'", new Throwable(String.format("Empty notification without 'confirmation_status'")));
        return null;
    }

    /**
     * This will handle general application push notification. No need to return intent as it will be already in launcher activity i.e. DispatchActivity
     *
     * @param context
     * @param fcmMessageData
     * @return Intent
     */
    private static Intent handleGeneralApplicationPushNotification(Context context, FCMMessageData fcmMessageData) {
        Intent intent = new Intent(context, DispatchActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        showNotification(context, buildNotification(context, intent, fcmMessageData));
        return null;    // This will always null as we are already in DispatchActivity and don't want to start again
    }


    /**
     * Handle CCC messaging/channel push notification
     *
     * @param context
     * @param fcmMessageData
     * @return Intent
     */

    private static Intent handleCCCMessageChannelPushNotification(Context context, FCMMessageData fcmMessageData) {
        Intent intent = null;
        fcmMessageData.setNotificationChannel(CommCareNoficationManager.NOTIFICATION_CHANNEL_MESSAGING_ID);
        fcmMessageData.setPriority(NotificationCompat.PRIORITY_MAX);
        fcmMessageData.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_connect_message_large));

        boolean isMessage = fcmMessageData.getPayloadData().containsKey(ConnectMessagingMessageRecord.META_MESSAGE_ID);

        int notificationTitleId;
        String notificationMessage;
        String channelId;
        if (isMessage) {
            notificationTitleId = R.string.connect_messaging_message_notification_title;
            notificationMessage = context.getString(R.string.connect_messaging_message_notification_message,
                    fcmMessageData.getPayloadData().get("channel_name"));
            channelId = fcmMessageData.getPayloadData().get("channel");
        } else {
            //Channel
            ConnectMessagingChannelRecord channel = MessageManager.handleReceivedChannel(context,
                    fcmMessageData.getPayloadData());

            notificationTitleId = R.string.connect_messaging_channel_notification_title;
            notificationMessage = context.getString(R.string.connect_messaging_channel_notification_message,
                    channel.getChannelName());

            channelId = channel.getChannelId();
        }

        //Send broadcast so any interested pages can update their UI
        Intent broadcastIntent = new Intent(MESSAGING_UPDATE_BROADCAST);
        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent);

        if (!ConnectMessageChannelListFragment.isActive &&
                !channelId.equals(ConnectMessageFragment.getActiveChannel())) {
            //Show push notification
            fcmMessageData.setNotificationTitle(context.getString(notificationTitleId));
            fcmMessageData.setNotificationText(notificationMessage);

            intent = new Intent(context, ConnectMessagingActivity.class);
            intent.putExtra(fcmMessageData.getAction(), fcmMessageData.getAction());
            intent.putExtra(ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID, channelId);
            intent.putExtra(REDIRECT_ACTION, fcmMessageData.getAction());
        }

        if (intent != null) {
            showNotification(context, buildNotification(context, intent, fcmMessageData));
        }
        return intent;

    }

    /**
     * Get connect activity notification intent
     *
     * @param context
     * @param fcmMessageData
     * @return Intent
     */
    private static Intent getConnectActivityNotification(Context context, FCMMessageData fcmMessageData) {
        Intent intent = new Intent(context, ConnectActivity.class);
        intent.putExtra(REDIRECT_ACTION, fcmMessageData.getAction());
        if (fcmMessageData.getPayloadData().containsKey(OPPORTUNITY_ID)) {
            intent.putExtra(OPPORTUNITY_ID, fcmMessageData.getPayloadData().get(OPPORTUNITY_ID));
        }
        return intent;
    }

    /**
     * Build notification
     *
     * @param context
     * @param intent
     * @param fcmMessageData
     * @return NotificationCompat.Builder
     */
    private static NotificationCompat.Builder buildNotification(Context context, Intent intent, FCMMessageData fcmMessageData) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, flags);

        if (Strings.isEmptyOrWhitespace(fcmMessageData.getNotificationTitle()) && Strings.isEmptyOrWhitespace(fcmMessageData.getNotificationText())) {
            Logger.exception("Empty push notification",
                    new Throwable(String.format("Empty notification for action '%s'", fcmMessageData.getAction())));
        }

        NotificationCompat.Builder fcmNotification = new NotificationCompat.Builder(context,
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


    /**
     * Show notification
     *
     * @param context
     * @param notificationBuilder
     */
    private static void showNotification(Context context, NotificationCompat.Builder notificationBuilder) {
        NotificationManager mNM = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
        mNM.notify(FCM_NOTIFICATION, notificationBuilder.build());
    }


    /**
     * Check if CCC action
     *
     * @param action
     * @return
     */
    private static boolean hasCccAction(String action) {
        return action != null && action.contains("ccc_");
    }


}

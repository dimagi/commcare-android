package org.commcare.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.android.gms.common.util.Strings;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.activities.connect.ConnectMessagingActivity;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.android.database.connect.models.PushNotificationRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.MessageManager;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.fragments.connectMessaging.ConnectMessageChannelListFragment;
import org.commcare.fragments.connectMessaging.ConnectMessageFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.services.FCMMessageData;
import org.commcare.sync.FirebaseMessagingDataSyncer;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.util.HashMap;
import java.util.Map;

import static android.content.Context.NOTIFICATION_SERVICE;
import static org.commcare.connect.ConnectConstants.CCC_DEST_DELIVERY_PROGRESS;
import static org.commcare.connect.ConnectConstants.CCC_DEST_LEARN_PROGRESS;
import static org.commcare.connect.ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE;
import static org.commcare.connect.ConnectConstants.CCC_DEST_PAYMENTS;
import static org.commcare.connect.ConnectConstants.CCC_GENERIC_OPPORTUNITY;
import static org.commcare.connect.ConnectConstants.CCC_MESSAGE;
import static org.commcare.connect.ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION;
import static org.commcare.connect.ConnectConstants.NOTIFICATION_BODY;
import static org.commcare.connect.ConnectConstants.NOTIFICATION_ID;
import static org.commcare.connect.ConnectConstants.NOTIFICATION_KEY;
import static org.commcare.connect.ConnectConstants.NOTIFICATION_TITLE;
import static org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS;
import static org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID;
import static org.commcare.connect.ConnectConstants.PAYMENT_UUID;
import static org.commcare.connect.ConnectConstants.REDIRECT_ACTION;
import static org.commcare.utils.NotificationIdentifiers.FCM_NOTIFICATION_ID;

/**
 * This class will be used to handle notification whenever
 * 1. App receives notification when app is in foreground
 * 2. App receives notification when app is in background/killed and user clicks on such notification. Launcher activity will call
 * this class to handle the notification
 */
public class FirebaseMessagingUtil {
    public static final String FCM_TOKEN = "fcm_token";
    public static final String FCM_TOKEN_TIME = "fcm_token_time";
    public static final String MESSAGING_UPDATE_BROADCAST = "com.dimagi.messaging.update";
    private static final long SERVICE_NOT_AVAILABLE_RETRY_DELAY_MS = 3000;
    static final int MAX_CAUSE_CHAIN_DEPTH = 5;


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
                String token = task.getResult();
                if (!StringUtils.isEmpty(token)) {
                    updateFCMToken(token);
                } else {
                    Logger.exception("Fetching FCM registration token failed with network status: " + ConnectivityStatus.getNetworkType(CommCareApplication.instance()) ,
                            new Throwable("FCM registration token is empty"));
                }
            } else {
                Throwable throwable = task.getException() != null ? task.getException() : new Throwable(
                        "Task to fetch FCM registration token failed");
                Logger.exception("Fetching FCM registration token failed with network status: " + ConnectivityStatus.getNetworkType(CommCareApplication.instance()), throwable);

                if (isFisAuthError(throwable)) {
                    Logger.log(LogTypes.TYPE_FCM,
                            "FIS_AUTH_ERROR detected, deleting Firebase installation"
                                    + " and retrying token fetch");
                    FirebaseInstallations.getInstance().delete().addOnCompleteListener(deleteTask -> {
                        if (deleteTask.isSuccessful()) {
                            retryFCMTokenFetch();
                        } else {
                            Logger.exception("Failed to delete Firebase installation for FIS_AUTH_ERROR recovery",
                                    deleteTask.getException() != null ? deleteTask.getException()
                                            : new Throwable("Firebase installation delete failed"));
                        }
                    });
                } else if (isServiceNotAvailable(throwable)) {
                    // SERVICE_NOT_AVAILABLE is transient (Google Play Services temporarily busy),
                    // so delay retry by 3s to give it time to recover before re-attempting.
                    Logger.log(LogTypes.TYPE_FCM,
                            "SERVICE_NOT_AVAILABLE detected, retrying token fetch after delay");
                    new Handler(Looper.getMainLooper()).postDelayed(
                            FirebaseMessagingUtil::retryFCMTokenFetch, SERVICE_NOT_AVAILABLE_RETRY_DELAY_MS);
                }
            }
        };
    }

    /**
     * Retries FCM token fetch once. This intentionally does not re-enter handleFCMTokenRetrieval()
     * to avoid recursive retry loops on repeated failures.
     */
    private static void retryFCMTokenFetch() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(retryTask -> {
            if (retryTask.isSuccessful() && !StringUtils.isEmpty(retryTask.getResult())) {
                Logger.log(LogTypes.TYPE_FCM, "FCM token retry succeeded");
                updateFCMToken(retryTask.getResult());
            } else {
                Logger.exception("FCM token retry also failed",
                        retryTask.getException() != null ? retryTask.getException()
                                : new Throwable("Retry returned empty token"));
            }
        });
    }

    private static boolean isFisAuthError(Throwable throwable) {
        return hasErrorInChain(throwable, "FIS_AUTH_ERROR");
    }

    private static boolean isServiceNotAvailable(Throwable throwable) {
        return hasErrorInChain(throwable, "SERVICE_NOT_AVAILABLE");
    }

    /**
     * Checks if the given error message exists in the throwable's cause chain,
     * up to 5 levels deep. Firebase errors like FIS_AUTH_ERROR originate as IOException
     * but arrive wrapped in ExecutionException, so the top-level throwable may not
     * contain the actual error string — we need to walk the cause chain to find it.
     */
    @VisibleForTesting
    static boolean hasErrorInChain(Throwable throwable, String errorMessage) {
        Throwable current = throwable;
        int maxDepth = MAX_CAUSE_CHAIN_DEPTH;
        while (current != null && maxDepth-- > 0) {
            String message = current.getMessage();
            if (message != null && message.contains(errorMessage)) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
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
    public static Intent handleNotification(Context context, Map<String, String> dataPayload, RemoteMessage.Notification notificationPayload, boolean showNotification) {

        if (dataPayload == null || dataPayload.isEmpty()) {
            if (!showNotificationFromNotificationPayload(context, notificationPayload,showNotification)) {
                Logger.exception("Empty push notification", new Throwable(String.format("Empty notification without payload")));
            }
            return null;
        }
        FCMMessageData fcmMessageData = new FCMMessageData(dataPayload);
        if (hasCccAction(fcmMessageData.getAction())){
            return handleCCCActionPushNotification(context, fcmMessageData,showNotification);
        } else if (fcmMessageData.getActionType() == FCMMessageData.ActionTypes.SYNC) {
            getDataSyncer(context).syncData(fcmMessageData);
            return null;
        } else {
            return handleGeneralApplicationPushNotification(context, fcmMessageData,showNotification);
        }
    }

    /**
     * Show notification from notification payload
     *
     * @param context
     * @param payloadNotification
     * @return
     */
    private static boolean showNotificationFromNotificationPayload(Context context, RemoteMessage.Notification payloadNotification,boolean showNotification) {
        if (payloadNotification != null &&
                !Strings.isEmptyOrWhitespace(payloadNotification.getTitle()) &&
                !Strings.isEmptyOrWhitespace(payloadNotification.getBody()) &&
                showNotification) {
            Map<String, String> notificationPayload = new HashMap<>();
            notificationPayload.put(NOTIFICATION_TITLE, payloadNotification.getTitle());
            notificationPayload.put(NOTIFICATION_BODY, payloadNotification.getBody());
            handleGeneralApplicationPushNotification(context, new FCMMessageData(notificationPayload),showNotification);
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
    private static Intent handleCCCActionPushNotification(Context context, FCMMessageData fcmMessageData, boolean showNotification) {

         if(!cccCheckPassed(context)) {    // app doesn't show notification related to CCC if user is not logged in
             Logger.log(LogTypes.TYPE_FCM,"CCC push notification sent while user is logout");
             return null;
         }

        ConnectUserDatabaseUtil.turnOnConnectAccess(context);
        return switch (fcmMessageData.getAction()) {
            case CCC_MESSAGE -> handleCCCMessageChannelPushNotification(context, fcmMessageData,showNotification);
            case CCC_DEST_PAYMENTS -> handleCCCPaymentPushNotification(context, fcmMessageData,showNotification);
            case CCC_PAYMENT_INFO_CONFIRMATION ->
                    handleCCCPaymentInfoConfirmationPushNotification(context, fcmMessageData,showNotification);
            case CCC_DEST_OPPORTUNITY_SUMMARY_PAGE ->
                    handleOpportunitySummaryPagePushNotification(context, fcmMessageData,showNotification);
            case CCC_DEST_LEARN_PROGRESS ->
                    handleResumeLearningOrDeliveryJobPushNotification(true, context, fcmMessageData,showNotification);
            case CCC_DEST_DELIVERY_PROGRESS ->
                    handleResumeLearningOrDeliveryJobPushNotification(false, context, fcmMessageData,showNotification);
            default ->
                    handleCccGenericOpportunityNotifcation( context, fcmMessageData,showNotification);
        };
    }

    /**
     * Handle CCC payment push notification
     *
     * @param context
     * @param fcmMessageData
     * @return Intent
     */

    private static Intent handleCCCPaymentPushNotification(Context context, FCMMessageData fcmMessageData, boolean showNotification) {
        Intent intent = getConnectActivityNotification(context, fcmMessageData);

        if (showNotification) {
            NotificationCompat.Builder fcmNotification = buildNotification(context, intent, fcmMessageData);
            showNotification(context, fcmNotification, fcmMessageData);
        }

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
    private static Intent handleResumeLearningOrDeliveryJobPushNotification(Boolean isLearning, Context context, FCMMessageData fcmMessageData, boolean showNotification) {
        if (fcmMessageData.getPayloadData().containsKey(OPPORTUNITY_UUID)) {
            Intent intent = getConnectActivityNotification(context, fcmMessageData);
            if (showNotification)
                showNotification(context, buildNotification(context, intent, fcmMessageData),
                        fcmMessageData);
            return intent;
        }
        String ccc_action = isLearning ? CCC_DEST_LEARN_PROGRESS : CCC_DEST_DELIVERY_PROGRESS;
        Logger.exception("Empty push notification for action '" + ccc_action + "'", new Throwable(String.format("Empty notification without 'opportunity' details")));
        return null;
    }

    private static Intent handleCccGenericOpportunityNotifcation(Context context, FCMMessageData fcmMessageData, boolean showNotification) {
        Intent intent = getConnectActivityNotification(context, fcmMessageData);
        if (showNotification)
            showNotification(context, buildNotification(context, intent, fcmMessageData),
                    fcmMessageData);
        return intent;
    }


    /**
     * Handle CCC opportunity summary page push notification
     *
     * @param context
     * @param fcmMessageData
     * @return
     */
    private static Intent handleOpportunitySummaryPagePushNotification(Context context, FCMMessageData fcmMessageData, boolean showNotification) {
        if (fcmMessageData.getPayloadData().containsKey(OPPORTUNITY_UUID)) {
            Intent intent = getConnectActivityNotification(context, fcmMessageData);
            if (showNotification)
                showNotification(context, buildNotification(context, intent, fcmMessageData),
                        fcmMessageData);
            return intent;
        }
        Logger.exception("Empty push notification for action 'ccc_opportunity_summary_page'", new Throwable(String.format("Empty notification without 'opportunity' details")));
        return null;
    }


    /**
     * Handle CCC payment info confirmation push notification
     *
     * @param context
     * @param fcmMessageData
     * @return
     */
    private static Intent handleCCCPaymentInfoConfirmationPushNotification(Context context, FCMMessageData fcmMessageData, boolean showNotification) {
        if (fcmMessageData.getPayloadData().containsKey(ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION_STATUS)) {
            Intent intent = getConnectActivityNotification(context, fcmMessageData);
            intent.putExtra(ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION_STATUS, fcmMessageData.getPayloadData().get(ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION_STATUS));
            if(showNotification) showNotification(context, buildNotification(context, intent, fcmMessageData),
                    fcmMessageData);
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
    private static Intent handleGeneralApplicationPushNotification(Context context, FCMMessageData fcmMessageData, boolean showNotification) {
        if(showNotification) {
            Intent intent = new Intent(context, DispatchActivity.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            showNotification(context, buildNotification(context, intent, fcmMessageData), fcmMessageData);
        }
        return null;    // This will always null as we are already in DispatchActivity and don't want to start again
    }

    /**
     * Handle CCC messaging/channel push notification
     *
     * @param context
     * @param fcmMessageData
     * @return Intent
     */

    private static Intent handleCCCMessageChannelPushNotification(Context context, FCMMessageData fcmMessageData, boolean showNotification) {
        Intent intent = null;
        fcmMessageData.setNotificationChannel(CommCareNoficationManager.NOTIFICATION_CHANNEL_MESSAGING_ID);
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

        if (intent != null && showNotification) {
           showNotification(context, buildNotification(context, intent, fcmMessageData), fcmMessageData);
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
        if (fcmMessageData.getPayloadData().containsKey(OPPORTUNITY_UUID)) {
            intent.putExtra(OPPORTUNITY_UUID, fcmMessageData.getPayloadData().get(OPPORTUNITY_UUID));
        }
        if (fcmMessageData.getPayloadData().containsKey(PAYMENT_UUID)) {
            intent.putExtra(PAYMENT_UUID, fcmMessageData.getPayloadData().get(PAYMENT_UUID));
        }
        if (fcmMessageData.getPayloadData().containsKey(NOTIFICATION_KEY)) {
            intent.putExtra(NOTIFICATION_KEY, fcmMessageData.getPayloadData().get(NOTIFICATION_KEY));
        }
        if (fcmMessageData.getPayloadData().containsKey(OPPORTUNITY_STATUS)) {
            intent.putExtra(OPPORTUNITY_STATUS, fcmMessageData.getPayloadData().get(OPPORTUNITY_STATUS));
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

        Bundle bundleExtras = new Bundle();
        intent.putExtra(NOTIFICATION_ID, fcmMessageData.getPayloadData().get(NOTIFICATION_ID));

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
                .setWhen(System.currentTimeMillis())
                .setExtras(bundleExtras);

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
     * @param fcmMessageData
     */
    private static void showNotification(Context context, NotificationCompat.Builder notificationBuilder,
            FCMMessageData fcmMessageData) {
        NotificationManager mNM = (NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
        String notificationId = fcmMessageData.getPayloadData().get(NOTIFICATION_ID);
        int notifId = !TextUtils.isEmpty(notificationId)
                ? NotificationIdentifiers.generateNotificationIdFromString(notificationId)
                : FCM_NOTIFICATION_ID; // fallback to constant
        mNM.notify(notifId, notificationBuilder.build());
        FirebaseAnalyticsUtil.reportNotificationEvent(
                AnalyticsParamValue.NOTIFICATION_EVENT_TYPE_SHOW,
                AnalyticsParamValue.REPORT_NOTIFICATION_METHOD_FIREBASE,
                getNotificationActionFromPayload(fcmMessageData.getPayloadData()),
                notificationId
        );
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

    public static Intent getIntentForPNIfAny(Context context, Intent intent) {
        //  Here we are handling only push notification-related intent, and
        //  It has only strings as keys and values. But now, this function gets executed whenever
        //  DispatchActivity is created; e.g., for showing the CC app, it might have a valid boolean intent.
        //  e.g., is_launched_from_connect, but this function raises an exception. So in order to not break
        //  things which are not even related to this getIntentForPNIfAny function, a try-catch has been put.
        //  This also gives reason to not log any exception in catch.
        try {
            if (intent != null && intent.getExtras() != null) {
                Map<String, String> dataPayload = new HashMap<>();
                for (String key : intent.getExtras().keySet()) {
                    String value = intent.getExtras().getString(key);
                    dataPayload.put(key, value);
                }
                Intent pnIntent = handleNotification(context, dataPayload, null, false);
                if (pnIntent != null) {
                    intent.replaceExtras(new Bundle()); // clear intents if push notification intents are present else app keeps reloading same PN intents
                }
                return pnIntent;
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static Intent getIntentForPNClick(Context context, PushNotificationRecord pushNotificationRecord){
        return cccCheckPassed(context) ? handleNotification(context,PushNotificationApiHelper.INSTANCE.convertPNRecordToPayload(pushNotificationRecord),null ,false): null;
    }

    public static boolean cccCheckPassed(Context context){
        PersonalIdManager personalIdManager = PersonalIdManager.getInstance();
        personalIdManager.init(context);
        return personalIdManager.isloggedIn();
    }

    public static String getNotificationActionFromPayload(Map<String, String> payload) {
        return (CCC_GENERIC_OPPORTUNITY.equals(payload.get(REDIRECT_ACTION)) &&
                payload.containsKey(NOTIFICATION_KEY)) ? payload.get(NOTIFICATION_KEY) : payload.get(REDIRECT_ACTION);
    }

    public static String getNotificationActionFromIntent(Intent intent) {
        return (CCC_GENERIC_OPPORTUNITY.equals(intent.getStringExtra(REDIRECT_ACTION)) &&
                !TextUtils.isEmpty(intent.getStringExtra(NOTIFICATION_KEY))) ? intent.getStringExtra(NOTIFICATION_KEY) : intent.getStringExtra(REDIRECT_ACTION);
    }
}

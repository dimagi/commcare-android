package org.commcare.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.dalvik.R;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.commcare.utils.FirebaseMessagingUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.Logger;

import java.util.Map;

/**
 * This service responds to any events/messages from Firebase Cloud Messaging. The intention is to
 * offer an entry point for any message from FCM and trigger the necessary steps based on the action
 * key..
 */
public class CommCareFirebaseMessagingService extends FirebaseMessagingService {

    private final static int FCM_NOTIFICATION = R.string.fcm_notification;
    private enum ActionTypes{
        SYNC,
        INVALID
    }

   /**
    * Upon receiving a new message from FCM, CommCare needs to:
    * 1) Verify if the message contains a data object. There are no more actions if not
    * 2) Trigger the necessary steps according to the action in the data payload
    *
    */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Logger.log(LogTypes.TYPE_FCM, "Message received: " + remoteMessage.getMessageId());
        Map<String, String> payloadData = remoteMessage.getData();
        RemoteMessage.Notification payloadNotification = remoteMessage.getNotification();

        // Check if message contains a data object, there is no further action if not
        if (payloadData.size() == 0)
            return;

        switch(getActionType(payloadData)){
            case SYNC ->
                    actionSyncData(payloadData, payloadNotification);
            default ->
                    Logger.log(LogTypes.TYPE_FCM, "Invalid FCM action");
        }
    }

    @Override
    public void onNewToken(String token) {
        // TODO: Remove the token from the log
        Logger.log(LogTypes.TYPE_FCM, "New registration token was generated"+token);
        FirebaseMessagingUtil.updateFCMToken(token);
    }

    /**
    * If we land here is because there is a data payload and the action there is to sync
    * 1) Check if there is an active session.
    *   - If yes, this will lead to further steps to attempt to trigger a sync
    *   - If not, a sync will be scheduled after the next successful login
    * 2) Ensure that the sync is only triggered for the intended 'recipient' of the message
    *
    */
    private void actionSyncData(Map<String, String> payloadData,
                                RemoteMessage.Notification payloadNotification) {

        if (!CommCareApplication.isSessionActive()){
            //  There is no active session at the moment, proceed accordingly
            // TODO: Decide whether to only trigger the Sync when the 'recipient' of the message logs in
            //  or anyone, in case multiple users are sharing the same device
            // TODO: Decide whether to check if when there is no active session, the recipient has ever
            //  logged in the device, before scheduling a sync post login
            HiddenPreferences.setPendingSyncRequestFromServer(true);
            showNotification(payloadNotification);
            return;
        }

        // Check if the recipient of the message matches the current logged in user
        // TODO: Decide whether we want to check the validity of the message, based on when it was
        //  created and the date/time of the last sync.
        if (checkUserAndDomain(payloadData)) {
            // Attempt to trigger the sync, according to the current state of the app
            attemptToTriggerSync();
        } else {
            // Username and Domain don't match current user OR payload data doesn't include username
            // or domain - Action: no actual, just log issue, no need to inform the user
            Logger.log(LogTypes.TYPE_FCM, "Invalid data payload");
        }
    }

    //
    private ActionTypes getActionType(Map<String, String> payloadData) {
        String action = payloadData.get("action");
        if (action.equalsIgnoreCase("sync"))
            return ActionTypes.SYNC;
        else
            return ActionTypes.INVALID;
    }

    /**
    * At this point, all the conditions are in place to trigger the sync: there is an active session
     * and the current user has been successfully verified. The principle here is to:
     * 1) Assess the current state of the app and decide the appropriate course of action, options
     * being:
     *  - Trigger a background sync and block the user from initiating any feature that involves
     *  Database I/O
     *  - Based on user input, go to the Home screen and trigger a blocking sync
     *  - Schedule the sync right after the form submission
    */
    private void attemptToTriggerSync() {

    }

    private boolean checkUserAndDomain(Map<String, String> payloadData) {
        String payloadUsername = payloadData.get("username");
        String payloadDomain = payloadData.get("domain");

        if(payloadUsername != null && payloadDomain != null){
            String loggedInUsername = CommCareApplication.instance().getSession().getLoggedInUser().getUsername();
            String userDomain = HiddenPreferences.getUserDomain();
            return payloadUsername.equalsIgnoreCase(loggedInUsername) && payloadDomain.equalsIgnoreCase(userDomain);
        }
        return false;
    }

   /**
    * This method purpose is to show notifications to the user when the app is in the foreground.
    * When the app is in the background, FCM is responsible for notifying the user
    *
    */
    private void showNotification(RemoteMessage.Notification notification) {
        if (notification != null) {
            String notificationTitle = notification.getTitle();
            String notificationText = notification.getBody();
            NotificationManager mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            Intent i = new Intent(this, DispatchActivity.class);
            i.setAction(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);

            PendingIntent contentIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                contentIntent = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
            else
                contentIntent = PendingIntent.getActivity(this, 0, i, 0);

            // TODO: Decide whether is justifiable to use a Notification channel with higher importance level
            NotificationCompat.Builder fcmNotification = new NotificationCompat.Builder(this,
                    CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationText)
                    .setContentIntent(contentIntent)
                    .setSmallIcon(R.drawable.notification)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setWhen(System.currentTimeMillis());

            mNM.notify(FCM_NOTIFICATION, fcmNotification.build());
        }
        else
            Logger.log(LogTypes.TYPE_FCM, "Message without notification");
    }
}

package org.commcare.services;

import android.app.NotificationManager;
import android.content.Context;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.commcare.dalvik.R;
import org.commcare.pn.workermanager.NotificationsSyncWorkerManager;
import org.commcare.util.LogTypes;
import org.commcare.utils.FirebaseMessagingUtil;
import org.javarosa.core.services.Logger;

import java.util.ArrayList;
import java.util.Map;

/**
 * This service responds to any events/messages from Firebase Cloud Messaging. The intention is to
 * offer an entry point for any message from FCM and trigger the necessary steps based on the action
 * key.
 */
public class CommCareFirebaseMessagingService extends FirebaseMessagingService {


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

        if(!startSyncForNotification(remoteMessage)){
            Logger.log(LogTypes.TYPE_FCM,"No sync present, it will try to raise the notification directly");
            FirebaseMessagingUtil.handleNotification(getApplicationContext(), remoteMessage.getData(), remoteMessage.getNotification(),true);
        }

    }

    private Boolean startSyncForNotification(RemoteMessage remoteMessage){
        ArrayList<Map<String,String>> pns = new ArrayList<>();
        pns.add(remoteMessage.getData());
        NotificationsSyncWorkerManager notificationsSyncWorkerManager = new NotificationsSyncWorkerManager(
                getApplicationContext(), pns, true, true);
        return notificationsSyncWorkerManager.startPNApiSync();
    }

    @Override
    public void onNewToken(String token) {
        // TODO: Remove the token from the log
        Logger.log(LogTypes.TYPE_FCM, "New registration token was generated: " + token);
        FirebaseMessagingUtil.updateFCMToken(token);
    }

    public static void clearNotification(Context context){
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(R.string.fcm_notification);
        }
    }
}

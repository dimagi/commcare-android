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
import org.commcare.dalvik.R;
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
    enum ActionTypes{
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
    *    presence of a Notification object causes the onMessageReceived to not be called when the
    *    app is in the background, which means that the data object won't be processed from here
    * 2) Verify if the message contains a data object and trigger the necessary steps according
    *    to the action it carries
    *
    */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Logger.log(LogTypes.TYPE_FCM, "Message received: " + remoteMessage.getMessageId());
        Map<String, String> payloadData = remoteMessage.getData();
        RemoteMessage.Notification payloadNotification = remoteMessage.getNotification();

        if (payloadNotification != null) {
            showNotification(payloadNotification);
        }

        // Check if the message contains a data object, there is no further action if not
        if (payloadData.size() == 0){
            return;
        }

        FCMMessageData fcmMessageData = new FCMMessageData(payloadData);

        switch(fcmMessageData.getAction()){
            case SYNC -> dataSyncer.syncData(fcmMessageData);
            default ->
                    Logger.log(LogTypes.TYPE_FCM, "Invalid FCM action");
        }
    }

    @Override
    public void onNewToken(String token) {
        // TODO: Remove the token from the log
        Logger.log(LogTypes.TYPE_FCM, "New registration token was generated: "+token);
        FirebaseMessagingUtil.updateFCMToken(token);
    }


   /**
    * This method purpose is to show notifications to the user when the app is in the foreground.
    * When the app is in the background, FCM is responsible for notifying the user
    *
    */
    private void showNotification(RemoteMessage.Notification notification) {
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
}

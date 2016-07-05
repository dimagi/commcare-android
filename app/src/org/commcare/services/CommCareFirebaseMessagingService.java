package org.commcare.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.commcare.dalvik.R;

/**
 * Created by Saumya on 6/29/2016.
 * Receives push notifications, alerts the device, and handles onclick actions
 */
public class CommCareFirebaseMessagingService extends FirebaseMessagingService{

    public static final String TAG = "MESSAGE";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        sendNotification(remoteMessage.getNotification());
    }

    /*
    This method alerts notifications and sets up the intent that gets broadcast when you click on the notif
    Both of this things should get done automatically according to FCM docs, but this works too..
     */
    private void sendNotification(RemoteMessage.Notification n) {

        PendingIntent pendingIntent;

        if(n.getClickAction() != null){
            Intent intent = new Intent(n.getClickAction());
            if(n.getBody() != null){
                intent.putExtra("BODY", n.getBody());
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
             pendingIntent = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT);
        }
        else{
             pendingIntent = null;
        }

        Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.apply_update)
                .setContentTitle(n.getTitle())
                .setContentText(n.getBody())
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(0, notificationBuilder.build());
    }
}

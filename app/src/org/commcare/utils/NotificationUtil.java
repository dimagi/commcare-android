package org.commcare.utils;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import org.commcare.dalvik.R;
import org.commcare.preferences.NotificationPrefs;

/**
 * Set of methods to post notifications to the user.
 *
 * @author avazirna
 */
public class NotificationUtil {
    public static void showNotification(Context context, String notificationChannel, int notificationId,
                                        String notificationTitle, String notificationText, Intent actionIntent) {

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent =
                PendingIntent.getActivity(context, 0, actionIntent, pendingIntentFlags);

        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context, notificationChannel)
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationText)
                        .setContentIntent(contentIntent)
                        .setSmallIcon(R.drawable.commcare_actionbar_logo)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setWhen(System.currentTimeMillis());
        ((NotificationManager) context.getSystemService(NOTIFICATION_SERVICE))
                .notify(notificationId, notification.build());
    }

    public static void cancelNotification(Context context, int notificationId) {
        ((NotificationManager) context.getSystemService(NOTIFICATION_SERVICE))
                .cancel(notificationId);
    }

    public static int getNotificationIcon(Context context) {
        boolean isRead = NotificationPrefs.INSTANCE.getNotificationReadStatus(context);
        return isRead ? R.drawable.ic_bell : R.drawable.ic_new_notification_bell;
    }
}

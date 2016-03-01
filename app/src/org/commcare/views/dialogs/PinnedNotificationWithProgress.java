package org.commcare.views.dialogs;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;

import org.commcare.activities.UpdateActivity;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.tasks.TaskListener;
import org.javarosa.core.services.locale.Localization;

/**
 * Pinned notification that receives updates from a task.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class PinnedNotificationWithProgress
        implements TaskListener<Integer, AppInstallStatus> {
    private final NotificationManager notificationManager;
    private final int notificationId;
    private final NotificationCompat.Builder notificationBuilder;

    private final String progressText;

    public PinnedNotificationWithProgress(Context ctx, String titleText,
                                          String progressText,
                                          int largeIconResource) {
        this.notificationId = titleText.hashCode();
        this.progressText = progressText;

        notificationManager =
                (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = buildPendingIntent(ctx);

        Bitmap largeIcon =
                BitmapFactory.decodeResource(ctx.getResources(), largeIconResource);

        notificationBuilder = new NotificationCompat.Builder(ctx)
                .setContentText(getProgressText(0, 0))
                .setContentTitle(Localization.get(titleText))
                .setProgress(100, 0, false)
                .setSmallIcon(org.commcare.dalvik.R.drawable.notification)
                .setLargeIcon(largeIcon)
                .setContentIntent(contentIntent)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true);

        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    private String getProgressText(int progress, int max) {
        return Localization.get(progressText, new String[]{"" + progress, "" + max});
    }

    private PendingIntent buildPendingIntent(Context ctx) {
        Intent i = new Intent(ctx, UpdateActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void handleTaskUpdate(Integer... updateVals) {
        int progress = 0;
        int max = 0;

        if (updateVals != null && updateVals.length > 1) {
            progress = updateVals[0];
            max = updateVals[1];
        }

        notificationBuilder.setProgress(max, progress, false);
        notificationBuilder.setContentText(getProgressText(progress, max));
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    @Override
    public void handleTaskCompletion(AppInstallStatus result) {
        notificationManager.cancel(notificationId);
    }

    @Override
    public void handleTaskCancellation(AppInstallStatus result) {
        notificationManager.cancel(notificationId);
    }
}

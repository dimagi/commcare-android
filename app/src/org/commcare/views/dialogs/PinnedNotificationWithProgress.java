package org.commcare.views.dialogs;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.UpdateActivity;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.TaskListener;
import org.javarosa.core.services.locale.Localization;

/**
 * Pinned notification that receives updates from a task.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class PinnedNotificationWithProgress<R>
        implements TaskListener<Integer, ResultAndError<R>> {
    private final NotificationManager notificationManager;
    private final int notificationId;
    private final NotificationCompat.Builder notificationBuilder;

    private String progressText;
    private String titleText;

    public PinnedNotificationWithProgress(Context ctx, String titleText,
                                          String progressText,
                                          int largeIconResource) {
        this.notificationId = titleText.hashCode();
        this.progressText = progressText;
        this.titleText = titleText;

        notificationManager =
                (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = buildPendingIntent(ctx);

        Bitmap largeIcon =
                BitmapFactory.decodeResource(ctx.getResources(), largeIconResource);

        notificationBuilder = new NotificationCompat.Builder(ctx, CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID)
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

    private String getProgressText(int completion) {
        return Localization.get(progressText, new String[]{"" + completion});
    }

    private PendingIntent buildPendingIntent(Context ctx) {
        int intentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            intentFlags = intentFlags | PendingIntent.FLAG_IMMUTABLE;

        Intent i = new Intent(ctx, UpdateActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 0, i, intentFlags);
    }

    @Override
    public void handleTaskUpdate(Integer... updateVals) {
        int progress = 0;
        int max = 0;

        if (updateVals != null && updateVals.length > 1) {
            progress = updateVals[0];
            max = updateVals[1];
        }

        // if max is -1, it means that the progress is percentage
        if (max == -1){
            notificationBuilder.setProgress(100, progress, false);
            notificationBuilder.setContentText(getProgressText(progress));
        }
        else{
            notificationBuilder.setProgress(max, progress, false);
            notificationBuilder.setContentText(getProgressText(progress, max));
        }
        notificationBuilder.setContentTitle(Localization.get(titleText));

        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    @Override
    public void handleTaskCompletion(ResultAndError<R> result) {
        notificationManager.cancel(notificationId);
    }

    @Override
    public void handleTaskCancellation() {
        notificationManager.cancel(notificationId);
    }

    public void setTitleText(String titleText) {
        this.titleText = titleText;
    }

    public void setProgressText(String progressText) {
        this.progressText = progressText;
    }
}

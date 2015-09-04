package org.commcare.dalvik.dialogs;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.tasks.TaskListener;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.UpdateActivity;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class PinnedNotificationWithProgress implements TaskListener<Integer, AppInstallStatus> {

    private NotificationManager notificationManager;
    Notification notification;
    int notificationId;

    public PinnedNotificationWithProgress(Context ctx, int notificationId) {
        notificationManager = (NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        this.notificationId = notificationId;


        notification = new Notification(org.commcare.dalvik.R.drawable.notification, "2) ticker?", System.currentTimeMillis());
        notification.flags |= (Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT);


        Intent callable = new Intent(ctx, UpdateActivity.class);
        callable.setAction("android.intent.action.MAIN");
        callable.addCategory("android.intent.category.LAUNCHER");


        PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0, callable, 0);

        RemoteViews contentView = new RemoteViews(ctx.getPackageName(), R.layout.submit_notification);
        contentView.setImageViewResource(R.id.image, R.drawable.notification);
        contentView.setTextViewText(R.id.submitTitle, "foo");
        contentView.setTextViewText(R.id.progressText, "progress text");
        contentView.setTextViewText(R.id.submissionDetails, "0b transmitted");


        notification.setLatestEventInfo(ctx, "foo", "progress text", contentIntent);
        notification.contentView = contentView;

        notification.contentView.setTextViewText(R.id.progressText, "1) Progress is at:");
        notification.contentView.setProgressBar(R.id.submissionProgress, 100, 0, false);

        notificationManager.notify(notificationId, notification);
    }

    @Override
    public void handleTaskUpdate(Integer... updateVals) {
        int progress = updateVals[0];
        int max = updateVals[1];
        notification.contentView.setTextViewText(R.id.submissionDetails, "progress details");
        notification.contentView.setProgressBar(R.id.submissionProgress, max, progress, false);
        notificationManager.notify(notificationId, notification);
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

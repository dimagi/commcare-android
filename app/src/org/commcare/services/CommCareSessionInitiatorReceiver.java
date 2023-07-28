package org.commcare.services;

import androidx.annotation.RequiresApi;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.commcare.CommCareApplication;


/**
 * Broadcast receiver to start foreground services and notifications
 *
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class CommCareSessionInitiatorReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ForegroundComponentType componentType = checkForegroundComponent(intent);

        cancelAlarm(context, intent, componentType);

        switch (componentType){
            case SERVICE:
                context.startForegroundService(new Intent(context, CommCareSessionService.class));
                break;
            case NOTIFICATION:
                if (intent.hasExtra(CommCareSessionService.EXTRA_NOTIFICATION_ID)) {
                    Bundle intentData = intent.getExtras();
                    int notificationId =intentData.getInt(CommCareSessionService.EXTRA_NOTIFICATION_ID);
                    Notification notification = intentData.getParcelable(CommCareSessionService.EXTRA_NOTIFICATION_OBJ);

                    CommCareApplication.instance().getSession().startForeground(notificationId, notification);
                }
        }
    }

    private void cancelAlarm(Context context, Intent intent, ForegroundComponentType componentType) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, componentType.ordinal(), intent, PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmmanager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (pendingIntent != null)
            alarmmanager.cancel(pendingIntent);
    }

    private ForegroundComponentType checkForegroundComponent(Intent intent) {
        if (intent.hasExtra(CommCareSessionService.EXTRA_COMPONENT_TYPE))
            return (ForegroundComponentType) intent.getExtras().get(CommCareSessionService.EXTRA_COMPONENT_TYPE);
        else
            // This branch is not expected to be reached in any situation
            throw new RuntimeException("Invalid CommCareSessionInitiatorReceiver intent!");
    }
}

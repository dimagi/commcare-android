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

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;

/**
 * Broadcast receiver to start foreground services and notifications
 *
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class CommCareSessionInitiatorReceiver extends BroadcastReceiver {

    public enum ForegroundComponent {
        SERVICE,
        NOTIFICATION
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ForegroundComponent component = checkForegroundComponent(intent);

        cancelAlarm(intent, component);

        switch (component){
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

    private void cancelAlarm(Intent intent, ForegroundComponent component) {
        int requestCode = component == ForegroundComponent.SERVICE? 0: 1;
        PendingIntent pendingIntent = new PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmmanager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (pendingIntent != null)
            alarmmanager.cancel(pendingIntent);
    }

    private ForegroundComponent checkForegroundComponent(Intent intent) {
        if (intent.hasExtra(CommCareSessionService.EXTRA_NOTIFICATION_ID))
            return ForegroundComponent.NOTIFICATION;
        return ForegroundComponent.SERVICE;
    }
}

package org.commcare.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast receiver to start foreground services and notifications
 *
 */
public class CommCareSessionInitiatorReceiver extends BroadcastReceiver {

    public enum ForegroundComponent {
        SERVICE,
        NOTIFICATION
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ForegroundComponent component = checkForegroundComponent(intent);

        cancelAlarm(intent);

        switch (component){
            case SERVICE: break;
            case NOTIFICATION: break;
        }
    }

    private void cancelAlarm(Intent intent) {
    }

    private ForegroundComponent checkForegroundComponent(Intent intent) {
        return ForegroundComponent.SERVICE;
    }
}

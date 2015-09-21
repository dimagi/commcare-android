/**
 * 
 */
package org.commcare.android.models.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.dalvik.application.CommCareApplication;

/**
 * Broadcast receiver to clear pending notifications.
 * 
 * @author ctsims
 *
 */
public class NotificationClearReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        CommCareApplication._().purgeNotifications();
    }

}

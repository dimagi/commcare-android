/**
 * 
 */
package org.commcare.android.models.notifications;

import org.commcare.dalvik.application.CommCareApplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Broadcast receiver to clear pending notifications.
 * 
 * @author ctsims
 *
 */
public class NotificationClearReceiver extends BroadcastReceiver {

	/* (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		CommCareApplication._().purgeNotifications();
	}

}

package org.commcare.pn.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager

object NotificationBroadcastHelper {
    const val ACTION_NEW_NOTIFICATIONS = "org.commcare.dalvik.action.NEW_NOTIFICATION"

    fun registerForNotifications(
        context: Context,
        owner: LifecycleOwner,
        onNewNotification: () -> Unit,
    ) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    i: Intent?,
                ) {
                    onNewNotification()
                }
            }

        val manager = LocalBroadcastManager.getInstance(context)

        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    manager.registerReceiver(receiver, IntentFilter(ACTION_NEW_NOTIFICATIONS))
                }

                override fun onPause(owner: LifecycleOwner) {
                    manager.unregisterReceiver(receiver)
                }
            },
        )
    }

    fun sendNewNotificationBroadcast(context: Context) {
        val intent = Intent(ACTION_NEW_NOTIFICATIONS)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}

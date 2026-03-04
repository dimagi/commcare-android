package org.commcare.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_LAUNCHER
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.commcare.CommCareNoficationManager
import org.commcare.activities.DispatchActivity
import org.commcare.dalvik.R
import org.commcare.utils.NotificationIdentifiers.NETWORK_SERVICE_NOTIFICATION_ID
import org.commcare.utils.StringUtils

/**
 * Service responsible for showing a notification when network activity is triggered by CommCareTasks when there
 * is no active user session on devices running Android 14 and higher. Duplicate tasks are supported.
 * The service will stop itself when all tasks are complete.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class NetworkNotificationService : Service() {
    companion object {
        const val UPDATE_PROGRESS_NOTIFICATION_ACTION = "update_progress_notification"
        const val STOP_NOTIFICATION_ACTION = "stop_notification"
        const val START_NOTIFICATION_ACTION = "start_notification"
        const val PROGRESS_TEXT_KEY_INTENT_EXTRA = "progress_text_key"
        const val TASK_TAG_INTENT_EXTRA = "task_tag"

        @Volatile
        @JvmStatic
        var isServiceRunning = false
    }

    private lateinit var notificationManager: NotificationManager
    private val taskTags = mutableListOf<String>()
    private val pendingIntent: PendingIntent by lazy {
        val intent =
            Intent(this, DispatchActivity::class.java).apply {
                action = ACTION_MAIN
                addCategory(CATEGORY_LAUNCHER)
            }
        PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(
            NETWORK_SERVICE_NOTIFICATION_ID,
            buildNotification(R.string.network_notification_service_starting),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        isServiceRunning = true
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            START_NOTIFICATION_ACTION -> {
                registerTaskId(intent.getStringExtra(TASK_TAG_INTENT_EXTRA), false)
            }
            UPDATE_PROGRESS_NOTIFICATION_ACTION -> {
                registerTaskId(intent.getStringExtra(TASK_TAG_INTENT_EXTRA), true)
                notificationManager.notify(
                    NETWORK_SERVICE_NOTIFICATION_ID,
                    buildNotification(
                        intent.getIntExtra(PROGRESS_TEXT_KEY_INTENT_EXTRA, R.string.network_notification_service_running),
                    ),
                )
            }
            STOP_NOTIFICATION_ACTION -> {
                removeTaskId(intent.getStringExtra(TASK_TAG_INTENT_EXTRA))
            }
        }

        return START_NOT_STICKY
    }

    private fun removeTaskId(taskTag: String?) {
        if (taskTag == null) return
        synchronized(taskTags) {
            taskTags.remove(taskTag)
            if (taskTags.isEmpty()) stopSelf()
        }
    }

    private fun registerTaskId(
        taskTag: String?,
        update: Boolean,
    ) {
        if (taskTag == null) return
        synchronized(taskTags) {
            if (update && taskTag in taskTags) return
            taskTags.add(taskTag)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(notificationTitleStringId: Int) = NotificationCompat
        .Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID)
        .setContentTitle(StringUtils.getStringRobust(this, notificationTitleStringId))
        .setOnlyAlertOnce(true)
        .setSmallIcon(R.drawable.commcare_actionbar_logo)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isServiceRunning = false
        super.onDestroy()
    }
}

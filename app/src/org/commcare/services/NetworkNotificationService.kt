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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.commcare.CommCareNoficationManager
import org.commcare.activities.DispatchActivity
import org.commcare.dalvik.R
import org.javarosa.core.services.locale.Localization

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class NetworkNotificationService : Service() {
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val NETWORK_NOTIFICATION_ID = "network_notification_service_id"
        const val UPDATE_PROGRESS_NOTIFICATION_ACTION = "update_progress_notification"
        const val STOP_NOTIFICATION_ACTION = "stop_notification"
        const val START_NOTIFICATION_ACTION = "start_notification"
        const val PROGRESS_TEXT_KEY_INTENT_EXTRA = "progress_text_key"
        const val TASK_ID_INTENT_EXTRA = "task_id"
        @JvmStatic
        var isServiceRunning = false
    }

    private val _taskIds = MutableStateFlow<List<Int>>(emptyList())
    val taskIds: StateFlow<List<Int>> = _taskIds
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NETWORK_NOTIFICATION_ID.hashCode(),
                buildNotification("network.notification.service.starting"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NETWORK_NOTIFICATION_ID.hashCode(), buildNotification("network.notification.service.starting"))
        }
        serviceScope.launch {
            taskIds.collect { list ->
                if (list.isEmpty() && isServiceRunning) {
                    stopSelf()
                }
            }
        }
        isServiceRunning = true
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            START_NOTIFICATION_ACTION -> {
                registerTaskId(intent.getIntExtra(TASK_ID_INTENT_EXTRA, -1), false)
            }
            UPDATE_PROGRESS_NOTIFICATION_ACTION -> {
                registerTaskId(intent.getIntExtra(TASK_ID_INTENT_EXTRA, -1), true)
                notificationManager.notify(
                    NETWORK_NOTIFICATION_ID.hashCode(),
                    buildNotification(
                        intent.getStringExtra(PROGRESS_TEXT_KEY_INTENT_EXTRA) ?: "network.notification.service.running",
                    ),
                )
            }
            STOP_NOTIFICATION_ACTION -> {
                removeTaskId(intent.getIntExtra(TASK_ID_INTENT_EXTRA, -1))
            }
        }
        return START_NOT_STICKY;
    }

    private fun removeTaskId(taskId: Int) {
        if (taskId == -1) return
        _taskIds.update { current -> current - taskId }
    }

    private fun registerTaskId(
        taskId: Int,
        update: Boolean,
    ) {
        if (taskId == -1 || (update && taskId in _taskIds.value)) return

        _taskIds.update { current -> current + taskId }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(notificationTitleKey: String): Notification {
        val activityToLaunch = Intent(this, DispatchActivity::class.java)
        activityToLaunch.setAction(ACTION_MAIN)
        activityToLaunch.addCategory(CATEGORY_LAUNCHER)

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, activityToLaunch, pendingIntentFlags)

        return NotificationCompat
            .Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID)
            .setContentTitle(Localization.get(notificationTitleKey))
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.commcare_actionbar_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isServiceRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }
}

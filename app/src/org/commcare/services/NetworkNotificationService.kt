package org.commcare.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.commcare.CommCareNoficationManager
import org.commcare.activities.DispatchActivity
import org.commcare.dalvik.R
import org.javarosa.core.services.locale.Localization

class NetworkNotificationService: Service() {
    lateinit var notificationManager: NotificationManager
    companion object {
        const val NETWORK_NOTIFICATION_ID = R.string.network_notification_service_id
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NETWORK_NOTIFICATION_ID, buildNotification("network.requests.starting",0,0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NETWORK_NOTIFICATION_ID, buildNotification("network.requests.starting",0,0))
        }
        isServiceRunning = true
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun buildNotification(notificationTitleKey: String, progress: Int, total: Int): Notification {
        val activityToLaunch = Intent(this, DispatchActivity::class.java)
        activityToLaunch.setAction("android.intent.action.MAIN")
        activityToLaunch.addCategory("android.intent.category.LAUNCHER")

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, activityToLaunch, pendingIntentFlags)

        return NotificationCompat.Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_SERVER_COMMUNICATIONS_ID)
            .setContentText(getProgressText(progress,total))
            .setContentTitle(Localization.get(notificationTitleKey))
            .setOnlyAlertOnce(true)
            .setProgress(total, progress, false)
            .setSmallIcon(R.drawable.commcare_actionbar_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun getProgressText(progress: Int, max: Int): String? {
        return Localization.get("network.requests.progress", arrayOf(progress.toString(),  max.toString()))
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isServiceRunning = false
        super.onDestroy()
    }
}

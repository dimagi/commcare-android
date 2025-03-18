package org.commcare.views.widgets;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import org.commcare.CommCareNoficationManager;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AudioRecordingService extends Service {
    private final IBinder binder = new AudioRecorderBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(RecordingFragment.RECORDING_NOTIFICATION_ID, createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(RecordingFragment.RECORDING_NOTIFICATION_ID, createNotification());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        this.stopForeground(true);
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_USER_SESSION_ID)
                .setContentTitle(Localization.get("recording.notification.title"))
                .setContentText(Localization.get("recording.notification.in.progress"))
                .setSmallIcon(R.drawable.commcare_actionbar_logo)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class AudioRecorderBinder extends Binder {
        public AudioRecordingService getService() {
            return AudioRecordingService.this;
        }
    }
}

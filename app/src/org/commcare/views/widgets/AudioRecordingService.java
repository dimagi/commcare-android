package org.commcare.views.widgets;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class AudioRecordingService extends Service {
    private final IBinder binder = new AudioRecorderBinder();

    @Override
    public void onCreate() {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {}

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

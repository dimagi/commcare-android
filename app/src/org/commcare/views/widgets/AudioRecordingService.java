package org.commcare.views.widgets;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioRecordingConfiguration;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.dalvik.R;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;

/**
 * A bounded foreground service intended to be bound to the RecordingFragment for managing audio recording
 * operations. Due to its persistent notification, the system treats it with higher importance, reducing the
 * likelihood of interruptions during recordings.
 *
 * @author avazirna
 **/
public class AudioRecordingService extends Service {
    private MediaRecorder recorder;
    private final IBinder binder = new AudioRecorderBinder();
    public static final String RECORDING_FILENAME_EXTRA_KEY = "recording-filename-extra-key";
    private NotificationManager notificationManager;
    private AudioRecordingHelper audioRecordingHelper = new AudioRecordingHelper();

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(RecordingFragment.RECORDING_NOTIFICATION_ID, createNotification(true),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(RecordingFragment.RECORDING_NOTIFICATION_ID, createNotification(true));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String fileName = intent.getExtras().getString(RECORDING_FILENAME_EXTRA_KEY);
        if (recorder == null) {
            recorder = audioRecordingHelper.setupRecorder(fileName);
        }
        recorder.start();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        resetRecorder();
        this.stopForeground(true);
    }

    private void resetRecorder() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }

    private Notification createNotification(boolean recordingRunning) {
        Intent activityToLaunch = new Intent(this, DispatchActivity.class);
        activityToLaunch.setAction("android.intent.action.MAIN");
        activityToLaunch.addCategory("android.intent.category.LAUNCHER");

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags = pendingIntentFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityToLaunch, pendingIntentFlags);

        return new NotificationCompat.Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_USER_SESSION_ID)
                .setContentTitle(Localization.get("recording.notification.title"))
                .setContentText(recordingRunning ? Localization.get("recording.notification.in.progress") :
                        Localization.get("recording.notification.paused"))
                .setSmallIcon(R.drawable.commcare_actionbar_logo)
                .setContentIntent(pendingIntent)
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

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public AudioRecordingConfiguration getActiveRecordingConfiguration() {
        if (!isRecorderActive()) {
            return null;
        }
        return recorder.getActiveRecordingConfiguration();
    }

    public boolean isRecorderActive() {
        return recorder != null;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void pauseRecording() {
        recorder.pause();
        notificationManager.notify(RecordingFragment.RECORDING_NOTIFICATION_ID,
                createNotification(false));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void resumeRecording() {
        recorder.resume();
        notificationManager.notify(RecordingFragment.RECORDING_NOTIFICATION_ID,
                createNotification(true));
    }

    public void stopRecording() {
        recorder.stop();
    }
}

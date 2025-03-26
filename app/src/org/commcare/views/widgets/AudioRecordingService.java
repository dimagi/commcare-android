package org.commcare.views.widgets;

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

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.dalvik.R;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;

public class AudioRecordingService extends Service {
    private static final int HEAAC_SAMPLE_RATE = 44100;
    private static final int AMRNB_SAMPLE_RATE = 8000;
    private MediaRecorder recorder;
    private final IBinder binder = new AudioRecorderBinder();
    public static final String RECORDING_FILENAME_EXTRA_KEY = "recording-filename-extra-key";
    private NotificationManager notificationManager;

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
        setupRecorder(fileName);
        recorder.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        this.stopForeground(true);
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

    private void setupRecorder(String fileName) {
        if (recorder == null) {
            recorder = new MediaRecorder();
        }

        boolean isHeAacSupported = isHeAacEncoderSupported();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
        } else {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            recorder.setPrivacySensitive(true);
        }
        recorder.setAudioSamplingRate(isHeAacSupported ? HEAAC_SAMPLE_RATE : AMRNB_SAMPLE_RATE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (isHeAacSupported) {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
        } else {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        }
        recorder.setOutputFile(fileName);

        try {
            recorder.prepare();
            Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Preparing recording: " + fileName
                    + " | " + (isHeAacSupported ? HEAAC_SAMPLE_RATE : AMRNB_SAMPLE_RATE)
                    + " | " + (isHeAacSupported ? MediaRecorder.AudioEncoder.HE_AAC : MediaRecorder.AudioEncoder.AMR_NB));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Checks whether the device supports High Efficiency AAC (HE-AAC) audio codec
    private boolean isHeAacEncoderSupported() {
        int numCodecs = MediaCodecList.getCodecCount();

        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            for (String supportedType : codecInfo.getSupportedTypes()) {
                if (supportedType.equalsIgnoreCase(MIMETYPE_AUDIO_AAC)) {
                    MediaCodecInfo.CodecCapabilities cap = codecInfo.getCapabilitiesForType(MIMETYPE_AUDIO_AAC);
                    MediaCodecInfo.CodecProfileLevel[] profileLevels = cap.profileLevels;
                    for (MediaCodecInfo.CodecProfileLevel profileLevel : profileLevels) {
                        int profile = profileLevel.profile;
                        if (profile == MediaCodecInfo.CodecProfileLevel.AACObjectHE
                                || profile == MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public AudioRecordingConfiguration getActiveRecordingConfiguration() {
        if (!isRecorderActive()) {
            return null;
        }
        return recorder.getActiveRecordingConfiguration();
    }

    public boolean isRecorderActive(){
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
}

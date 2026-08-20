package org.commcare.views.widgets;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.dalvik.R;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.locale.Localization;

import static org.commcare.utils.NotificationIdentifiers.RECORDING_NOTIFICATION_ID;

/**
 * A foreground service intended to be bound to the RecordingFragment for managing audio recording
 * operations. Due to its persistent notification, the system treats it with higher importance, reducing the
 * likelihood of interruptions during recordings. This service owns the authoritative recording state.
 *
 * @author avazirna
 **/
public class AudioRecordingService extends Service {

    /**
     * The lifecycle of a single recording session, from the service's point of view.
     */
    public enum RecordingState {
        IDLE,
        RECORDING,
        PAUSED,
        STOPPED
    }

    private MediaRecorder recorder;
    private final IBinder binder = new AudioRecorderBinder();
    public static final String RECORDING_FILENAME_EXTRA_KEY = "recording-filename-extra-key";
    public static final String PAUSE_SUPPORTED_EXTRA_KEY = "pause-supported-extra-key";
    public static final String ACTION_SAVE_RECORDING = "action-save-recording";
    public static final String ACTION_PAUSE_RECORDING = "action-pause-recording";
    public static final String ACTION_RESUME_RECORDING = "action-resume-recording";
    private NotificationManager notificationManager;
    private AudioRecordingHelper audioRecordingHelper = new AudioRecordingHelper();
    private RecordingActionListener actionListener;
    private boolean pauseSupported;

    /**
     * Base for the elapsed recording time, in the {@link SystemClock#elapsedRealtime()} timebase and in
     * the form a Chronometer expects: the recording has been running for
     * {@code elapsedRealtime() - chronometerBase} ms. Pausing advances it by the length of the pause.
     */
    private long chronometerBase;
    /** When elapsed time last stopped advancing, i.e. when the recording was paused or stopped. */
    private long mLastStopTime;
    private RecordingState state = RecordingState.IDLE;

    /**
     * Callback used to relay notification action button presses back to the bound
     * {@link RecordingFragment}, which owns the recording UI and lifecycle.
     */
    public interface RecordingActionListener {
        void onSaveRequested();
        void onPauseRequested();
        void onResumeRequested();
    }

    public void setRecordingActionListener(RecordingActionListener actionListener) {
        this.actionListener = actionListener;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(RECORDING_NOTIFICATION_ID, createNotification(true),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(RECORDING_NOTIFICATION_ID, createNotification(true));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Notification action buttons re-deliver this intent without the recording extras;
        // relay them to the bound fragment instead of (re)starting the recorder.
        String action = intent.getAction();
        if (action != null) {
            if (actionListener != null) {
                switch (action) {
                    case ACTION_SAVE_RECORDING -> actionListener.onSaveRequested();
                    case ACTION_PAUSE_RECORDING -> actionListener.onPauseRequested();
                    case ACTION_RESUME_RECORDING -> actionListener.onResumeRequested();
                }
            }
            return START_NOT_STICKY;
        }

        // A rebinding UI or second start intent for a session that is already under way must not restart the
        // recorder
        if (state != RecordingState.IDLE) {
            return START_NOT_STICKY;
        }

        String fileName = intent.getExtras().getString(RECORDING_FILENAME_EXTRA_KEY);
        pauseSupported = intent.getBooleanExtra(PAUSE_SUPPORTED_EXTRA_KEY, false);
        if (recorder == null) {
            recorder = audioRecordingHelper.setupRecorder(fileName,
                    DeveloperPreferences.getAudioQualityProfile());
        }
        recorder.start();
        chronometerBase = SystemClock.elapsedRealtime();
        state = RecordingState.RECORDING;
        // Re-post so the notification reflects pauseSupported, which is only known here (the
        // initial notification is built in onCreate, before this intent is delivered).
        notificationManager.notify(RECORDING_NOTIFICATION_ID, createNotification(true));
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        resetRecorder();
        this.stopForeground(true);
    }

    private void resetRecorder() {
        if (recorder == null) {
            return;
        }
        if (state == RecordingState.RECORDING || state == RecordingState.PAUSED) {
            stopRecording();
        }
        recorder.release();
        recorder = null;
    }

    private Notification createNotification(boolean recordingRunning) {
        Intent activityToLaunch = new Intent(this, DispatchActivity.class);
        activityToLaunch.setAction("android.intent.action.MAIN");
        activityToLaunch.addCategory("android.intent.category.LAUNCHER");

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, activityToLaunch, pendingIntentFlags);

        Intent saveIntent = new Intent(this, AudioRecordingService.class).setAction(ACTION_SAVE_RECORDING);
        PendingIntent savePendingIntent = PendingIntent.getService(this, 1, saveIntent, pendingIntentFlags);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CommCareNoficationManager.NOTIFICATION_CHANNEL_USER_SESSION_ID)
                        .setContentTitle(Localization.get("recording.notification.title"))
                        .setContentText(recordingRunning ? Localization.get("recording.notification.in.progress") :
                                Localization.get("recording.notification.paused"))
                        .setSmallIcon(R.drawable.commcare_actionbar_logo)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true);

        // A single toggling action: shows Pause while running and Resume while paused. The
        // service re-posts the notification on pause/resume, so the label/action flip themselves.
        if (pauseSupported) {
            String toggleAction = recordingRunning ? ACTION_PAUSE_RECORDING : ACTION_RESUME_RECORDING;
            int toggleLabelResourceId = recordingRunning ? R.string.recording_notification_pause_action
                    : R.string.recording_notification_resume_action;
            Intent toggleIntent = new Intent(this, AudioRecordingService.class).setAction(toggleAction);
            PendingIntent togglePendingIntent = PendingIntent.getService(this, 2, toggleIntent, pendingIntentFlags);
            builder.addAction(0, StringUtils.getStringRobust(this, toggleLabelResourceId), togglePendingIntent);
        }

        builder.addAction(0, StringUtils.getStringRobust(this, R.string.recording_notification_save_action), savePendingIntent);
        return builder.build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Provides other components with access to the functionality exposed by the AudioRecordingService
     *
     **/
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

    public RecordingState getState() {
        return state;
    }

    /**
     * The value to hand to {@link android.widget.Chronometer#setBase(long)} so that it displays the
     * elapsed recording time, excluding any time spent paused. Safe to call in any state; before the
     * recording starts it reads as zero elapsed time.
     */
    public long getChronometerBase() {
        switch (state) {
            case IDLE:
                return SystemClock.elapsedRealtime();
            case PAUSED:
            case STOPPED:
                // Keep the display frozen at the moment elapsed time stopped advancing.
                return chronometerBase + (SystemClock.elapsedRealtime() - mLastStopTime);
            default:
                return chronometerBase;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void pauseRecording() {
        if (state != RecordingState.RECORDING) {
            return;
        }
        recorder.pause();
        mLastStopTime = SystemClock.elapsedRealtime();
        state = RecordingState.PAUSED;
        notificationManager.notify(RECORDING_NOTIFICATION_ID,
                createNotification(false));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void resumeRecording() {
        recorder.resume();
        chronometerBase += SystemClock.elapsedRealtime() - mLastStopTime;
        state = RecordingState.RECORDING;
        notificationManager.notify(RECORDING_NOTIFICATION_ID,
                createNotification(true));
    }

    public void stopRecording() {
        if (state == RecordingState.RECORDING) {
            mLastStopTime = SystemClock.elapsedRealtime();
        }
        state = RecordingState.STOPPED;
        recorder.stop();
    }
}

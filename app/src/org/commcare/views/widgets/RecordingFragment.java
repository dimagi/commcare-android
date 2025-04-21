package org.commcare.views.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.activities.DispatchActivity;
import org.commcare.dalvik.R;
import org.commcare.util.LogTypes;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.NotificationUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * A popup dialog fragment that handles recording_fragment and saving of audio
 * files without external callout.
 *
 * @author Saumya Jain (sjain@dimagi.com)
 */
public class RecordingFragment extends DialogFragment {

    public static final String AUDIO_FILE_PATH_ARG_KEY = "audio_file_path";
    public static final String APPEARANCE_ATTR_ARG_KEY = "appearance_attr_key";
    private static final CharSequence LONG_APPEARANCE_VALUE = "long";
    private static final String SAVE_TEXT_KEY = "save";
    private static final String CANCEL_TEXT_KEY = "recording.cancel";
    private static final String CLEAR_TEXT_KEY = "recording.clear";

    private static final String MIMETYPE_AUDIO_AAC = "audio/mp4a-latm";

    private static final int HEAAC_SAMPLE_RATE = 44100;
    private static final int AMRNB_SAMPLE_RATE = 8000;
    private final int RECORDING_NOTIFICATION_ID = R.string.audio_recording_notification;

    private String fileName;
    private static final String FILE_EXT = ".mp3";

    private LinearLayout layout;
    private ImageButton toggleRecording;
    private ImageButton discardRecording;
    private Button actionButton;
    private TextView instruction;
    private ProgressBar recordingProgress;

    private Chronometer recordingDuration;

    private MediaRecorder recorder;
    private RecordingCompletionListener listener;
    private MediaPlayer player;
    private long mLastStopTime;
    private boolean inPausedState = false;
    private boolean savedRecordingExists = false;
    private AudioManager.AudioRecordingCallback audioRecordingCallback;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        layout = (LinearLayout) inflater.inflate(R.layout.recording_fragment, container);
        disableScreenRotation((AppCompatActivity) getContext());
        prepareButtons();
        prepareText();
        setWindowSize();

        Bundle args = getArguments();
        if (args != null) {
            fileName = args.getString(AUDIO_FILE_PATH_ARG_KEY);
        }

        if (fileName == null) {
            initAudioFile();
        }

        File f = new File(fileName);
        if (f.exists()) {
            reloadSavedRecording();
        }

        return layout;
    }

    private void initAudioFile() {
        fileName = CommCareApplication.instance().getAndroidFsTemp() + new Date().getTime() + FILE_EXT;
    }

    private void reloadSavedRecording() {
        savedRecordingExists = true;
        actionButton.setVisibility(View.VISIBLE);
        setActionText(CANCEL_TEXT_KEY);
        actionButton.setOnClickListener(v -> dismiss());
        recordingDuration.setVisibility(View.INVISIBLE);
        toggleRecording.setBackgroundResource(R.drawable.recording_trash);
        toggleRecording.setOnClickListener(v -> resetRecordingView());
        instruction.setText(Localization.get("delete.recording"));
    }

    private void setWindowSize() {
        Rect displayRectangle = new Rect();
        Window window = getActivity().getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(displayRectangle);
        layout.setMinimumWidth((int) (displayRectangle.width() * 0.9f));
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    }

    private void prepareText() {
        instruction = layout.findViewById(R.id.recording_header);
        instruction.setText(Localization.get("before.recording"));
        recordingDuration = layout.findViewById(R.id.recording_time);
    }

    private void prepareButtons() {
        discardRecording = layout.findViewById(R.id.discardrecording);
        discardRecording.setOnClickListener(v -> dismiss());
        toggleRecording = layout.findViewById(R.id.startrecording);
        actionButton = layout.findViewById(R.id.action_button);
        recordingProgress = layout.findViewById(R.id.demo_mpc);
        toggleRecording.setOnClickListener(v -> startRecording());
    }

    private void setActionText(String textKey) {
        actionButton.setText(Localization.get(textKey));
    }

    private void resetRecordingView() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }

        if (player != null) {
            resetAudioPlayer();
        }

        // reset the file path
        initAudioFile();

        toggleRecording.setBackgroundResource(R.drawable.record_start);
        toggleRecording.setOnClickListener(v -> startRecording());
        instruction.setText(Localization.get("before.overwrite.recording"));
        recordingDuration.setVisibility(View.INVISIBLE);
        enableSave();
        setActionText(CLEAR_TEXT_KEY);
    }

    private void startRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (MediaUtil.isRecordingActive(getContext())) {
                Toast.makeText(getContext(), Localization.get("start.recording.failed"), Toast.LENGTH_SHORT).show();
                Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording cancelled due to an ongoing recording");
                return;
            }
        }

        disableScreenRotation((AppCompatActivity) getContext());
        setCancelable(false);
        setupRecorder();
        recorder.start();
        recordingDuration.setBase(SystemClock.elapsedRealtime());
        recordingInProgress();
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording started");

        // Extend the user extension if about to expire, this is to prevent the session from expiring in the
        // middle of a recording
        CommCareApplication.instance().getSession().extendUserSessionIfNeeded();
    }

    private void recordingInProgress() {
        recordingDuration.start();
        if (isPauseSupported()) {
            toggleRecording.setBackgroundResource(R.drawable.pause);
            toggleRecording.setOnClickListener(v -> pauseRecording(true));
        } else {
            toggleRecording.setBackgroundResource(R.drawable.record_in_progress);
            toggleRecording.setOnClickListener(v -> stopRecording());
        }
        instruction.setText(Localization.get("during.recording"));
        recordingProgress.setVisibility(View.VISIBLE);
        recordingDuration.setVisibility(View.VISIBLE);
        actionButton.setVisibility(View.INVISIBLE);
        discardRecording.setVisibility(View.INVISIBLE);
    }

    private void setupRecorder() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerAudioRecordingConfigurationChangeCallback();
        }
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

    @SuppressLint("NewApi")
    private void stopRecording() {
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording stopping");
        recordingDuration.stop();
        recordingProgress.setVisibility(View.INVISIBLE);

        // resume first just in case we were paused
        if (inPausedState) {
            recorder.resume();
        }

        recorder.stop();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(v -> playAudio());
        instruction.setText(Localization.get("after.recording"));
        enableSave();
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording stopped");
    }

    @SuppressLint("NewApi")
    private void pauseRecording(boolean pausedByUser) {
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording pausing");
        inPausedState = true;
        recordingDuration.stop();
        chronoPause();
        recorder.pause();
        recordingProgress.setVisibility(View.INVISIBLE);
        enableSave();
        toggleRecording.setBackgroundResource(R.drawable.record_add);
        toggleRecording.setOnClickListener(v -> resumeRecording());
        instruction.setText(Localization.get(pausedByUser ? "pause.recording"
                : "pause.recording.because.no.sound.captured"));
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording paused");
    }

    private void enableSave() {
        discardRecording.setVisibility(savedRecordingExists ? View.VISIBLE : View.INVISIBLE);
        actionButton.setVisibility(View.VISIBLE);
        setActionText(SAVE_TEXT_KEY);
        actionButton.setOnClickListener(v -> saveRecording());
    }

    @SuppressLint("NewApi")
    private void resumeRecording() {
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording resuming");
        inPausedState = false;
        chronoResume();
        recorder.resume();
        recordingInProgress();
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording resumed");
    }

    private boolean isPauseSupported() {
        Bundle args = getArguments();
        if (args != null) {
            String appearance = args.getString(APPEARANCE_ATTR_ARG_KEY);
            return LONG_APPEARANCE_VALUE.equals(appearance)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
        }
        return false;
    }

    private void saveRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            unregisterAudioRecordingConfigurationChangeCallback();
        }
        if (inPausedState) {
            stopRecording();
        }
        if (listener != null) {
            listener.onRecordingCompletion(fileName);
        }
        dismiss();
    }

    public interface RecordingCompletionListener {
        void onRecordingCompletion(String audioFile);
    }

    public void setListener(RecordingCompletionListener listener) {
        this.listener = listener;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        enableScreenRotation((AppCompatActivity) getContext());
        if (recorder != null) {
            recorder.release();
            this.recorder = null;
        }

        if (player != null) {
            try {
                player.release();
            } catch (IllegalStateException e) {
                // Do nothing because player wasn't recording
            }
        }
    }

    private static void disableScreenRotation(AppCompatActivity context) {
        int currentOrientation = context.getResources().getConfiguration().orientation;

        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }
    }

    private static void enableScreenRotation(AppCompatActivity context) {
        context.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    private void playAudio() {
        Uri myPath = Uri.parse(fileName);
        player = MediaPlayer.create(getContext(), myPath);
        player.setOnCompletionListener(mp -> resetAudioPlayer());
        recordingDuration.setBase(SystemClock.elapsedRealtime());
        recordingDuration.start();
        player.start();
        toggleRecording.setBackgroundResource(R.drawable.pause);
        toggleRecording.setOnClickListener(v -> pauseAudioPlayer());
    }

    private void pauseAudioPlayer() {
        player.pause();
        chronoPause();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(v -> resumeAudioPlayer());
    }

    private void resumeAudioPlayer() {
        chronoResume();
        player.start();
        toggleRecording.setBackgroundResource(R.drawable.pause);
        toggleRecording.setOnClickListener(v -> pauseAudioPlayer());
    }

    private void resetAudioPlayer() {
        player.release();
        recordingDuration.stop();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(v -> playAudio());
    }

    private void chronoPause() {
        recordingDuration.stop();
        mLastStopTime = SystemClock.elapsedRealtime();
    }

    private void chronoResume() {
        if (mLastStopTime == 0) {
            recordingDuration.setBase(SystemClock.elapsedRealtime());
        } else {
            long intervalOnPause = SystemClock.elapsedRealtime() - mLastStopTime;
            recordingDuration.setBase(recordingDuration.getBase() + intervalOnPause);
        }
        recordingDuration.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void registerAudioRecordingConfigurationChangeCallback() {
        audioRecordingCallback = new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                super.onRecordingConfigChanged(configs);
                if (recorder == null) {
                    return;
                }

                if (hasRecordingGoneSilent(configs)) {
                    if (!inPausedState) {
                        pauseRecording(false);
                        NotificationUtil.showNotification(
                                getContext(),
                                CommCareNoficationManager.NOTIFICATION_CHANNEL_USER_SESSION_ID,
                                RECORDING_NOTIFICATION_ID,
                                Localization.get("recording.paused.due.another.app.recording.title"),
                                Localization.get("recording.paused.due.another.app.recording.message"),
                                new Intent(getContext(), DispatchActivity.class)
                                        .setAction(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_LAUNCHER));
                    }
                } else {
                    if (inPausedState) {
                        NotificationUtil.cancelNotification(getContext(), RECORDING_NOTIFICATION_ID);
                    }
                }
            }
        };
        ((AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE))
                .registerAudioRecordingCallback(audioRecordingCallback, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void unregisterAudioRecordingConfigurationChangeCallback() {
        if (audioRecordingCallback != null) {
            ((AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE))
                    .unregisterAudioRecordingCallback(audioRecordingCallback);
            audioRecordingCallback = null;
        }
    }

    private boolean hasRecordingGoneSilent(List<AudioRecordingConfiguration> configs) {
        if (recorder == null) {
            return false;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (recorder.getActiveRecordingConfiguration() == null) {
                return false;
            }

            Optional<AudioRecordingConfiguration> currentAudioConfig = configs.stream().filter(config ->
                            config.getClientAudioSessionId() == recorder.getActiveRecordingConfiguration()
                                    .getClientAudioSessionId())
                    .findAny();
            return currentAudioConfig.isPresent() ? currentAudioConfig.get().isClientSilenced() : false;
        } else {
            // TODO: Add logic to check if the recording has gone silent for Android 9 and prior
            return false;
        }
    }
}

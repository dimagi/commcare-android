package org.commcare.views.widgets;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.FrameLayout;
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
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.commcare.utils.NotificationIdentifiers.RECORDING_NOTIFICATION_ID;
import static org.commcare.views.widgets.AudioRecordingService.RECORDING_FILENAME_EXTRA_KEY;

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

    private String fileName;
    private static final String FILE_EXT = ".mp3";

    private LinearLayout layout;
    private FrameLayout recordingContainer;
    private ImageButton toggleRecording;
    private ImageButton discardRecording;
    private TextView recordingAnimationText;
    private Button negativeActionButton;
    private LinearLayout recordingActionContainer;
    private Button positiveActionButton;
    private TextView instruction;
    private ProgressBar recordingProgress;
    private Chronometer recordingDuration;

    private RecordingCompletionListener listener;
    private MediaPlayer player;
    private long mLastStopTime;
    private boolean inPausedState = false;
    private boolean savedRecordingExists = false;
    private AudioManager.AudioRecordingCallback audioRecordingCallback;
    private boolean audioRecordingServiceBounded = false;
    private AudioRecordingService audioRecordingService;
    private ServiceConnection audioRecordingServiceConnection;
    private Animation recordingAnimation;
    private int redColor;
    private int lightGrayColor;
    private Drawable recordingDrawable;
    private Drawable pausedDrawable;
    private Drawable progressBarDrawable;
    private Drawable progressBarPausedDrawable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        layout = (LinearLayout) inflater.inflate(R.layout.recording_fragment, container);
        disableScreenRotation((AppCompatActivity) getContext());
        prepareButtons();
        prepareText();
        initMediaResources();
        setWindowSize();

        return layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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
    }

    private void initMediaResources() {
        recordingAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.blink);
        redColor = getContext().getColor(R.color.red_incomplete);
        lightGrayColor = getContext().getColor(R.color.grey_light);

        progressBarDrawable = getContext().getDrawable(R.drawable.progress_bar);
        progressBarPausedDrawable = getContext().getDrawable(R.drawable.progress_bar_paused);
        pausedDrawable = getContext().getDrawable(R.drawable.recording_paused);
        recordingDrawable = getContext().getDrawable(R.drawable.recording_dot);
    }

    private void initAudioFile() {
        fileName = CommCareApplication.instance().getAndroidFsTemp() + new Date().getTime() + FILE_EXT;
    }

    private void reloadSavedRecording() {
        savedRecordingExists = true;
        negativeActionButton.setVisibility(VISIBLE);
        setActionText(negativeActionButton, CANCEL_TEXT_KEY);
        negativeActionButton.setOnClickListener(v -> dismiss());
        recordingDuration.setVisibility(GONE);
        recordingContainer.setVisibility(GONE);
        recordingActionContainer.setVisibility(VISIBLE);
        positiveActionButton.setVisibility(VISIBLE);
        setActionText(positiveActionButton, R.string.confirm_audio_file_deletion);
        positiveActionButton.setOnClickListener(v -> resetRecordingView());

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
        recordingContainer = layout.findViewById(R.id.recording_layout);
        discardRecording.setOnClickListener(v -> dismiss());
        toggleRecording = layout.findViewById(R.id.startrecording);
        toggleRecording.setOnClickListener(v -> startRecording());
        negativeActionButton = layout.findViewById(R.id.negative_action_button);
        recordingActionContainer = layout.findViewById(R.id.recording_action_container);
        positiveActionButton = layout.findViewById(R.id.positive_action_button);
        recordingProgress = layout.findViewById(R.id.demo_mpc);
        recordingAnimationText = layout.findViewById(R.id.recording_animation_text);
    }

    private void setActionText(Button actionButton, String textKey) {
        actionButton.setText(Localization.get(textKey));
    }

    private void setActionText(Button actionButton, int stringResourceId) {
        actionButton.setText(StringUtils.getStringRobust(getContext(), stringResourceId));
    }

    private void resetRecordingView() {
        if (player != null) {
            resetAudioPlayer();
        }

        // reset the file path
        initAudioFile();
        recordingContainer.setVisibility(VISIBLE);
        toggleRecording.setBackgroundResource(R.drawable.record_start);
        toggleRecording.setOnClickListener(v -> startRecording());
        instruction.setText(Localization.get("before.overwrite.recording"));
        recordingDuration.setVisibility(INVISIBLE);
        negativeActionButton.setVisibility(GONE);
        enableSave(false);
        setActionText(positiveActionButton, CLEAR_TEXT_KEY);
    }

    private void startRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (MediaUtil.isRecordingActive(getContext())) {
                Toast.makeText(getContext(), Localization.get("start.recording.failed"), Toast.LENGTH_SHORT)
                        .show();
                Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording cancelled due to an ongoing recording");
                return;
            }
        }

        disableScreenRotation((AppCompatActivity) getContext());
        setCancelable(false);

        Intent serviceIntent = new Intent(requireActivity(), AudioRecordingService.class);
        serviceIntent.putExtra(RECORDING_FILENAME_EXTRA_KEY, fileName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireActivity().startForegroundService(serviceIntent);
        } else {
            requireActivity().startService(serviceIntent);
        }
        requireActivity().bindService(serviceIntent, getAudioRecordingServiceConnection(),
                Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection getAudioRecordingServiceConnection() {
        return audioRecordingServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                audioRecordingService = ((AudioRecordingService.AudioRecorderBinder) service).getService();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    registerAudioRecordingConfigurationChangeCallback();
                }

                recordingDuration.setBase(SystemClock.elapsedRealtime());
                recordingInProgress();
                Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording started");

                // Extend the user extension if about to expire, this is to prevent the session from expiring
                // in the middle of a recording
                CommCareApplication.instance().getSession().extendUserSessionIfNeeded();

                audioRecordingServiceBounded = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                audioRecordingService = null;
                audioRecordingServiceBounded = false;
            }
        };
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
        recordingProgress.setVisibility(VISIBLE);
        resumeRecordingIndicators();
        recordingActionContainer.setVisibility(GONE);

        recordingDuration.setVisibility(VISIBLE);
        negativeActionButton.setVisibility(GONE);
        discardRecording.setVisibility(INVISIBLE);
    }

    @SuppressLint("NewApi")
    private void stopRecording() {
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording stopping");
        recordingDuration.stop();
        recordingProgress.setVisibility(INVISIBLE);
        recordingActionContainer.setVisibility(VISIBLE);
        recordingAnimationText.clearAnimation();
        recordingAnimationText.setVisibility(GONE);

        // resume first just in case we were paused
        if (inPausedState) {
            audioRecordingService.resumeRecording();
        }

        audioRecordingService.stopRecording();
        toggleRecording.setBackgroundResource(R.drawable.play);
        toggleRecording.setOnClickListener(v -> playAudio());
        instruction.setText(Localization.get("after.recording"));
        enableSave(false);
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording stopped");
    }

    @SuppressLint("NewApi")
    private void pauseRecording(boolean pausedByUser) {
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording pausing");
        inPausedState = true;
        recordingDuration.stop();
        chronoPause();

        audioRecordingService.pauseRecording();
        pauseRecordingIndicators();

        recordingActionContainer.setVisibility(VISIBLE);
        enableSave(true);
        toggleRecording.setBackgroundResource(R.drawable.record);
        toggleRecording.setOnClickListener(v -> resumeRecording());
        instruction.setText(Localization.get(pausedByUser ? "pause.recording"
                : "pause.recording.because.no.sound.captured"));
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording paused");
    }

    private void pauseRecordingIndicators() {
        progressBarPausedDrawable.setBounds(0,0, recordingProgress.getWidth(), recordingProgress.getHeight());
        recordingProgress.setIndeterminateDrawable(progressBarPausedDrawable);

        recordingAnimationText.clearAnimation();
        recordingAnimationText.setText(R.string.recording_paused);
        recordingAnimationText.setTextColor(lightGrayColor);
        recordingAnimationText.setCompoundDrawablesRelativeWithIntrinsicBounds(pausedDrawable, null, null, null);
    }

    private void resumeRecordingIndicators() {
        progressBarDrawable.setBounds(0,0, recordingProgress.getWidth(), recordingProgress.getHeight());
        recordingProgress.setIndeterminateDrawable(progressBarDrawable);

        recordingAnimationText.setVisibility(VISIBLE);
        recordingAnimationText.setText(R.string.recording_in_progress);
        recordingAnimationText.setTextColor(redColor);
        recordingAnimationText.startAnimation(recordingAnimation);
        recordingAnimationText.setCompoundDrawablesRelativeWithIntrinsicBounds(recordingDrawable, null, null, null);
    }

    private void enableSave(boolean isPaused) {
        discardRecording.setVisibility(savedRecordingExists ? VISIBLE : INVISIBLE);
        if (!isPaused) {
            recordingAnimationText.setVisibility(GONE);
        }
        positiveActionButton.setVisibility(VISIBLE);
        setActionText(positiveActionButton, SAVE_TEXT_KEY);
        positiveActionButton.setOnClickListener(v -> saveRecording());
    }

    @SuppressLint("NewApi")
    private void resumeRecording() {
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Recording resuming");
        inPausedState = false;
        chronoResume();

        audioRecordingService.resumeRecording();
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
        if (inPausedState) {
            stopRecording();
        }
        if (listener != null) {
            listener.onRecordingCompletion(fileName);
        }
        dismiss();
    }

    /**
     * A listener interface for handling post-recording events
     */
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
                if (audioRecordingService == null || !audioRecordingService.isRecorderActive()) {
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
        if (audioRecordingService == null || !audioRecordingService.isRecorderActive()) {
            return false;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (audioRecordingService.getActiveRecordingConfiguration() == null) {
                return false;
            }

            Optional<AudioRecordingConfiguration> currentAudioConfig = configs.stream().filter(config ->
                    config.getClientAudioSessionId() == audioRecordingService.getActiveRecordingConfiguration()
                            .getClientAudioSessionId())
                    .findAny();
            return currentAudioConfig.isPresent() ? currentAudioConfig.get().isClientSilenced() : false;
        } else {
            // TODO: Add logic to check if the recording has gone silent for Android 9 and prior
            return false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbindAudioRecordingService();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            unregisterAudioRecordingConfigurationChangeCallback();
        }
    }

    private void unbindAudioRecordingService() {
        if (audioRecordingServiceBounded) {
            requireActivity().unbindService(audioRecordingServiceConnection);
            requireActivity()
                    .stopService(new Intent(requireActivity(), AudioRecordingService.class));
        }
    }
}

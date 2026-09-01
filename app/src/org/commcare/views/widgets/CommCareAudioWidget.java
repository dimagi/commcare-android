package org.commcare.views.widgets;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.util.LogTypes;
import org.commcare.utils.Permissions;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.Logger;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import static android.Manifest.permission.RECORD_AUDIO;
import static org.commcare.views.widgets.RecordingFragment.APPEARANCE_ATTR_ARG_KEY;
import static org.commcare.views.widgets.RecordingFragment.AUDIO_FILE_PATH_ARG_KEY;
import static org.commcare.views.widgets.RecordingFragment.RESULT_REQUEST_KEY_ARG_KEY;

/**
 * An alternative audio widget that records and plays audio natively without
 * callout to any external application.
 *
 * @author Saumya Jain (sjain@dimagi.com)
 */
public class CommCareAudioWidget extends AudioWidget {

    private LinearLayout layout;
    private ConstraintLayout playbackContainer;
    private TextView recordingFilename;
    private TextView playbackDurationMain;
    private ImageButton mPlayButton;
    private TextView playbackDuration;
    private TextView playbackTime;
    private Timer playbackTimer;
    private SeekBar playbackSeekBar;
    private MediaPlayer player;
    private boolean showFileChooser;
    private static final String ACQUIRE_UPLOAD_FIELD = "acquire-or-upload";
    private static final String RECORDER_FRAGMENT_TAG_PREFIX = "recorder-";
    private static final String RECORDING_RESULT_REQUEST_KEY_PREFIX = "recording-result-";
    private ImageButton captureButton;
    private LinearLayout recordingContainer;
    private MaterialButton deleteAudio;

    public CommCareAudioWidget(Context context, FormEntryPrompt prompt,
                               PendingCalloutInterface pic) {
        super(context, prompt, pic);
    }


    @Override
    protected void initializeButtons() {
        LayoutInflater vi = LayoutInflater.from(getContext());
        layout = (LinearLayout)vi.inflate(R.layout.audio_prototype, null);

        playbackContainer = layout.findViewById(R.id.playback_container);
        playbackDurationMain = layout.findViewById(R.id.playback_duration_main);
        recordingFilename = layout.findViewById(R.id.recording_filename);
        mPlayButton = layout.findViewById(R.id.play_audio);
        captureButton = layout.findViewById(R.id.capture_button);
        recordingContainer = layout.findViewById(R.id.recording_container);
        ImageButton chooseButton = layout.findViewById(R.id.choose_file);
        playbackDuration = layout.findViewById(R.id.playback_duration);
        playbackTime = layout.findViewById(R.id.playback_time);
        playbackSeekBar = layout.findViewById(R.id.seekBar);
        deleteAudio = layout.findViewById(R.id.delete_audio);

        deleteAudio.setOnClickListener(v -> launchAudioRecorder(mPrompt));

        captureButton.setOnClickListener(v -> {
            if (Permissions.missingAppPermission((AppCompatActivity)getContext(), RECORD_AUDIO)) {
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                if (Permissions.shouldShowPermissionRationale(
                        (AppCompatActivity)getContext(),
                        Manifest.permission.RECORD_AUDIO)
                ) {
                    CommCareAlertDialog dialog =
                            DialogCreationHelpers.buildPermissionRequestDialog(
                                    (AppCompatActivity)getContext(), (RuntimePermissionRequester)getContext(),
                                    REQUEST_RECORD_AUDIO_PERMISSION,
                                    StringUtils.getStringRobust(getContext(), R.string.permission_microphone_title),
                                    StringUtils.getStringRobust(getContext(), R.string.permission_microphone_message)
                            );
                    dialog.showNonPersistentDialog(getContext());
                } else {
                    ((RuntimePermissionRequester)getContext()).requestNeededPermissions(REQUEST_RECORD_AUDIO_PERMISSION);
                }
            } else {
                captureAudio(mPrompt);
            }
        });

        // launch audio filechooser intent on click
        chooseButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("audio/*");
            try {
                ((AppCompatActivity)getContext()).startActivityForResult(i, FormEntryConstants.AUDIO_VIDEO_DOCUMENT_FETCH);
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(),
                        StringUtils.getStringSpannableRobust(getContext(),
                                R.string.activity_not_found,
                                "choose audio"),
                        Toast.LENGTH_SHORT).show();
            }
        });

        showFileChooser = ACQUIRE_UPLOAD_FIELD.equals(mPrompt.getAppearanceHint());
        chooseButton.setVisibility(showFileChooser ? VISIBLE : GONE);

        listenForRecordingResult();
        if (getSupportFragmentManager().findFragmentByTag(getRecorderFragmentTag()) != null) {
            // Rebuilt while the recorder is still open, so re-apply the disable it was launched with.
            setCaptureButtonEnabled(false);
        }
    }

    @Override
    public void notifyPermission(String permission, boolean permissionGranted) {
        if (permission.contentEquals(RECORD_AUDIO)) {
            if (permissionGranted) {
                captureAudio(mPrompt);
            } else {
                Toast.makeText(
                        getContext(),
                        StringUtils.getStringRobust(getContext(), R.string.permission_microphone_denial_message),
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    @Override
    public IAnswerData getAnswer() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    System.out.println("Playing");
                    player.pause();
                }
            } catch (IllegalStateException e) {
                //Do nothing because player is not playing
            }

            player.release();
        }

        return super.getAnswer();
    }

    @Override
    public void setupLayout() {
        addView(layout);
    }

    @Override
    protected void captureAudio(FormEntryPrompt prompt) {
        setCaptureButtonEnabled(false);
        launchAudioRecorder(prompt);
    }

    private void setCaptureButtonEnabled(boolean enabled) {
        captureButton.setClickable(enabled);
        captureButton.setAlpha(enabled ? 1.0f : 0.5f);
    }


    private void launchAudioRecorder(FormEntryPrompt prompt) {
        RecordingFragment recorder = new RecordingFragment();
        Bundle args = new Bundle();
        String sourceFilePath = getSourceFilePathToDisplay();
        if (!TextUtils.isEmpty(sourceFilePath)) {
            args.putString(AUDIO_FILE_PATH_ARG_KEY, sourceFilePath);
        }
        args.putString(APPEARANCE_ATTR_ARG_KEY, prompt.getAppearanceHint());
        args.putString(RESULT_REQUEST_KEY_ARG_KEY, getRecordingResultRequestKey());
        recorder.setArguments(args);
        recorder.show(getSupportFragmentManager(), getRecorderFragmentTag());
    }

    private String getRecorderFragmentTag() {
        return RECORDER_FRAGMENT_TAG_PREFIX + mPrompt.getIndex().toString();
    }

    /**
     * Keyed by form index so the result reaches this question rather than another audio widget on the
     * same screen, and so that it still matches once the widget has been rebuilt.
     */
    private String getRecordingResultRequestKey() {
        return RECORDING_RESULT_REQUEST_KEY_PREFIX + mPrompt.getIndex().toString();
    }

    private FragmentManager getSupportFragmentManager() {
        return ((FragmentActivity)getContext()).getSupportFragmentManager();
    }

    /**
     * Listens on the fragment manager rather than being handed to the dialog, so the result still
     * arrives when this widget was rebuilt while the dialog was open.
     */
    private void listenForRecordingResult() {
        getSupportFragmentManager().setFragmentResultListener(getRecordingResultRequestKey(),
                (FragmentActivity)getContext(),
                (requestKey, result) -> onRecordingResult(result));
    }

    private void onRecordingResult(Bundle result) {
        String audioFile = result.getString(RecordingFragment.RESULT_AUDIO_FILE_PATH_KEY);
        if (audioFile == null) {
            // Dismissed without recording anything, so just undo the disable done on launch.
            setCaptureButtonEnabled(true);
            return;
        }
        Logger.log(LogTypes.TYPE_MEDIA_EVENT, "Saving recording: " + audioFile);
        if (new File(audioFile).exists()) {
            setBinaryData(audioFile);
            togglePlayButton(true);
        } else {
            clearAnswer();
        }
    }

    @Override
    protected void playAudio() {
        mPlayButton.setImageResource(R.drawable.pause);
        mPlayButton.setOnClickListener(v -> pauseAudioPlayer());
        startPlaybackTimer();
        player.start();
    }

    private void startPlaybackTimer() {
        playbackTimer = new Timer();
        playbackTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                ((AppCompatActivity)getContext()).runOnUiThread(() -> {
                    if (playbackTimer != null) {
                        updatePlaybackInfo();
                    }
                });
            }
        }, 1 * 1000, 1 * 1000);
    }

    private void stopPlaybackTimer() {
        if (playbackTimer != null) {
            playbackTimer.cancel();
            playbackTimer = null;
        }
    }

    private void updatePlaybackInfo() {
        try {
            if (player.isPlaying()) {
                int mCurrentPosition = player.getCurrentPosition();
                playbackSeekBar.setProgress(mCurrentPosition / 1000);
                playbackTime.setText(getTimeString(mCurrentPosition));
            }
        } catch (IllegalStateException e){
            // ignore, can happen if this method is triggered after player has stopped
        }
    }


    private String getTimeString(long millis) {
        StringBuffer buf = new StringBuffer();

        int hours = (int)(millis / (1000 * 60 * 60));
        int minutes = (int)((millis % (1000 * 60 * 60)) / (1000 * 60));
        int seconds = (int)(((millis % (1000 * 60 * 60)) % (1000 * 60)) / 1000);

        if (hours > 0) {
            buf.append(String.format(Locale.getDefault(), "%02d", hours)).append(":");
        }

        buf.append(String.format(Locale.getDefault(), "%02d", minutes)).append(":")
                .append(String.format(Locale.getDefault(), "%02d", seconds));

        return buf.toString();
    }

    private void pauseAudioPlayer() {
        player.pause();
        mPlayButton.setImageResource(R.drawable.play);
        mPlayButton.setOnClickListener(v -> resumeAudioPlayer());
        stopPlaybackTimer();
    }

    private void resumeAudioPlayer() {
        player.start();
        mPlayButton.setImageResource(R.drawable.pause);
        mPlayButton.setOnClickListener(v -> pauseAudioPlayer());
        startPlaybackTimer();
    }

    private void resetAudioPlayer() {
        if (player != null) {
            player.release();
        }
        stopPlaybackTimer();
    }

    @Override
    protected void togglePlayButton(boolean enabled) {
        if (enabled) {
            initAudioPlayer();
            recordingContainer.setVisibility(GONE);
        } else {
            resetAudioPlayer();
            hidePlaybackIndicators();
            recordingContainer.setVisibility(VISIBLE);
            setCaptureButtonEnabled(true);
        }
    }

    private void hidePlaybackIndicators() {
        playbackContainer.setVisibility(GONE);
    }

    private void initAudioPlayer() {
        playbackContainer.setVisibility(VISIBLE);
        mPlayButton.setImageResource(R.drawable.play);
        mPlayButton.setOnClickListener(v -> playAudio());

        String sourceFilePath = getSourceFilePathToDisplay();
        Uri filePath = Uri.parse(sourceFilePath);
        player = MediaPlayer.create(getContext(), filePath);
        player.setOnCompletionListener(mp -> onCompletePlayback());
        recordingFilename.setText(new File(sourceFilePath).getName());

        String duration = getTimeString(player.getDuration());
        playbackDuration.setText(duration);
        playbackDurationMain.setText(duration);

        playbackTime.setText(R.string.playback_start_time);

        playbackSeekBar.setMax(player.getDuration() / 1000);
        playbackSeekBar.setProgress(0);
        playbackSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (player != null && fromUser) {
                    player.seekTo(progress * 1000);
                    playbackTime.setText(getTimeString(player.getCurrentPosition()));
                }
            }
        });
    }

    private void onCompletePlayback() {
        playbackSeekBar.setProgress(100);
        stopPlaybackTimer();
        playbackTime.setText(R.string.playback_start_time);
        mPlayButton.setImageResource(R.drawable.play);
        mPlayButton.setOnClickListener(v -> playAudio());
        playbackSeekBar.setVisibility(VISIBLE);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
    }

    @Override
    public void cancelLongPress() {
    }

    @Override
    public void unsetListeners() {
    }
}

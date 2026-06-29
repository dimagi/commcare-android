package org.commcare.views.widgets;

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
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.util.LogTypes;
import org.commcare.utils.StringUtils;
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

import static org.commcare.views.widgets.RecordingFragment.APPEARANCE_ATTR_ARG_KEY;
import static org.commcare.views.widgets.RecordingFragment.AUDIO_FILE_PATH_ARG_KEY;

/**
 * An alternative audio widget that records and plays audio natively without
 * callout to any external application.
 *
 * @author Saumya Jain (sjain@dimagi.com)
 */
public class CommCareAudioWidget extends AudioWidget
        implements RecordingFragment.RecordingCompletionListener {

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

        captureButton.setOnClickListener(v -> captureAudio(mPrompt));
        deleteAudio.setOnClickListener(v -> launchAudioRecorder(mPrompt));

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
        launchAudioRecorder(prompt);
    }

    private void launchAudioRecorder(FormEntryPrompt prompt) {
        RecordingFragment recorder = new RecordingFragment();
        recorder.setListener(this);
        Bundle args = new Bundle();
        String sourceFilePath = getSourceFilePathToDisplay();
        if (!TextUtils.isEmpty(sourceFilePath)) {
            args.putString(AUDIO_FILE_PATH_ARG_KEY, sourceFilePath);
        }
        args.putString(APPEARANCE_ATTR_ARG_KEY, prompt.getAppearanceHint());
        recorder.setArguments(args);
        recorder.show(((FragmentActivity)getContext()).getSupportFragmentManager(), "Recorder");
    }

    @Override
    public void onRecordingCompletion(String audioFile) {
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

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
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.StringUtils;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import androidx.appcompat.app.AppCompatActivity;
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
    private ImageButton mPlayButton;
    private TextView playbackDuration;
    private TextView playbackTime;
    private Timer playbackTimer;
    private SeekBar playbackSeekBar;
    private MediaPlayer player;
    private boolean showFileChooser;
    private static final String ACQUIRE_UPLOAD_FIELD = "acquire-or-upload";
    private ImageButton captureButton;

    public CommCareAudioWidget(Context context, FormEntryPrompt prompt,
                               PendingCalloutInterface pic) {
        super(context, prompt, pic);
    }


    @Override
    protected void initializeButtons() {
        LayoutInflater vi = LayoutInflater.from(getContext());
        layout = (LinearLayout)vi.inflate(R.layout.audio_prototype, null);

        mPlayButton = layout.findViewById(R.id.play_audio);
        captureButton = layout.findViewById(R.id.capture_button);
        ImageButton chooseButton = layout.findViewById(R.id.choose_file);
        playbackDuration = layout.findViewById(R.id.playback_duration);
        playbackTime = layout.findViewById(R.id.playback_time);
        playbackSeekBar = layout.findViewById(R.id.seekBar);

        captureButton.setOnClickListener(v -> captureAudio(mPrompt));

        // launch audio filechooser intent on click
        chooseButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("audio/*");
            try {
                ((AppCompatActivity)getContext()).startActivityForResult(i, FormEntryConstants.AUDIO_VIDEO_FETCH);
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
        RecordingFragment recorder = new RecordingFragment();
        recorder.setListener(this);
        Bundle args = new Bundle();
        if (!TextUtils.isEmpty(mTempBinaryPath)) {
            args.putString(AUDIO_FILE_PATH_ARG_KEY, mTempBinaryPath);
        }
        args.putString(APPEARANCE_ATTR_ARG_KEY, prompt.getAppearanceHint());
        recorder.setArguments(args);
        recorder.show(((FragmentActivity)getContext()).getSupportFragmentManager(), "Recorder");
    }

    @Override
    public void setBinaryData(Object binaryuri) {
        super.setBinaryData(binaryuri);
    }

    @Override
    public void onRecordingCompletion(String audioFile) {
        if (new File(audioFile).exists()) {
            setBinaryData(audioFile);
            togglePlayButton(true);
        } else {
            clearAnswer();
        }
    }

    @Override
    protected void playAudio() {
        mPlayButton.setBackgroundResource(R.drawable.pause);
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
        if (player.isPlaying()) {
            int mCurrentPosition = player.getCurrentPosition();
            playbackSeekBar.setProgress(mCurrentPosition / 1000);
            playbackTime.setText(getTimeString(mCurrentPosition));
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
        mPlayButton.setBackgroundResource(R.drawable.play);
        mPlayButton.setOnClickListener(v -> resumeAudioPlayer());
        stopPlaybackTimer();
    }

    private void resumeAudioPlayer() {
        player.start();
        mPlayButton.setBackgroundResource(R.drawable.pause);
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
            captureButton.setBackgroundResource(R.drawable.recording_trash);
        } else {
            resetAudioPlayer();
            hidePlaybackIndicators();
            captureButton.setBackgroundResource(R.drawable.record);
        }
    }

    private void hidePlaybackIndicators() {
        mPlayButton.setVisibility(INVISIBLE);
        playbackSeekBar.setVisibility(INVISIBLE);
        playbackDuration.setVisibility(INVISIBLE);
        playbackTime.setVisibility(INVISIBLE);
    }

    private void initAudioPlayer() {
        mPlayButton.setVisibility(VISIBLE);
        mPlayButton.setBackgroundResource(R.drawable.play);
        mPlayButton.setOnClickListener(v -> playAudio());

        Uri filePath = Uri.parse(mTempBinaryPath);
        player = MediaPlayer.create(getContext(), filePath);
        player.setOnCompletionListener(mp -> onCompletePlayback());

        playbackDuration.setVisibility(VISIBLE);
        playbackDuration.setText(String.format(Locale.getDefault(),
                "/%s",getTimeString(player.getDuration())));

        playbackTime.setVisibility(VISIBLE);
        playbackTime.setText(R.string.playback_start_time);

        playbackSeekBar.setVisibility(VISIBLE);
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
        mPlayButton.setBackgroundResource(R.drawable.play);
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

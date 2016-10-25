package org.commcare.views.media;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import org.commcare.dalvik.R;

import java.util.concurrent.TimeUnit;


/**
 * Audio playback widget with clickable horizontal progress bar
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ExpandedAudioPlaybackView extends AudioPlaybackButtonBase {
    private SeekBar seekBar;
    private Handler handler;
    private TextView progressText;

    public ExpandedAudioPlaybackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        progressText = (TextView)findViewById(R.id.duration_info);
    }

    public ExpandedAudioPlaybackView(Context context, String URI) {
        super(context, URI, null, true);
        progressText = (TextView)findViewById(R.id.duration_info);
    }

    @Override
    protected int getLayout() {
        return R.layout.expanded_audio_playback;
    }

    @Override
    protected void startProgressBar(int milliPosition, int milliDuration) {
        seekBar = (SeekBar)findViewById(R.id.seek_bar);
        seekBar.setEnabled(true);
        seekBar.setMax(milliDuration);
        seekBar.setProgress(milliPosition);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (AudioController.INSTANCE.mediaForInstance(ExpandedAudioPlaybackView.this) && fromUser) {
                    AudioController.INSTANCE.seekTo(progress);
                }
            }
        });

        launchProgressBarUpdater();

    }

    private void launchProgressBarUpdater() {
        handler = new Handler();
        this.post(new Runnable() {

            @Override
            public void run() {
                // make sure we are playing this audio
                if (AudioController.INSTANCE.mediaForInstance(ExpandedAudioPlaybackView.this)) {
                    int pos = AudioController.INSTANCE.getCurrentPosition();
                    seekBar.setProgress(pos);
                    updateProgressText(pos, seekBar.getMax());
                    handler.postDelayed(this, 20);
                }
            }
        });
    }

    @Override
    protected void resetProgressBar() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        if (seekBar != null) {
            seekBar.setProgress(0);
            seekBar.setEnabled(false);
            updateProgressText(0, seekBar.getMax());
        }
    }

    @Override
    protected void pauseProgressBar() {
        handler.removeCallbacksAndMessages(null);
    }

    private void updateProgressText(int progress, int max) {
        progressText.setText(milliToHumanReadable(progress) + " / " +milliToHumanReadable(max));
    }

    private static String milliToHumanReadable(int millis) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }
}

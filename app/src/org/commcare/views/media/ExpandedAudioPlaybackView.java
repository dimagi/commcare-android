package org.commcare.views.media;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.dalvik.R;

import java.util.concurrent.TimeUnit;

/**
 * Audio playback widget with clickable horizontal progress bar
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ExpandedAudioPlaybackView extends AudioPlaybackButtonBase {
    private ProgressBar seekBar;
    private ObjectAnimator animation;
    private Handler handler;
    private TextView progressText;
    private int playbackDurationMillis;

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
    protected void startProgressBar(int currentPositionMillis, int milliDuration) {
        playbackDurationMillis = milliDuration;
        setupProgressBar();
        setupProgressAnimation(currentPositionMillis);
        launchElapseTextUpdaterThread();
    }

    private void setupProgressBar() {
        seekBar = (ProgressBar)findViewById(R.id.seek_bar);
        seekBar.setEnabled(true);
        seekBar.setMax(playbackDurationMillis);
        seekBar.setOnTouchListener(
                new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return performProgressBarTouch(v, event);
                    }
                });
    }

    private boolean performProgressBarTouch(View v, MotionEvent event) {
        int progress = (int)(playbackDurationMillis * (event.getX() / v.getWidth()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            animation.setCurrentPlayTime(progress);
        }
        updateProgressText(progress, playbackDurationMillis);
        if (AudioController.INSTANCE.mediaForInstance(ExpandedAudioPlaybackView.this)) {
            AudioController.INSTANCE.seekTo(progress);
        }
        return false;
    }

    private void setupProgressAnimation(int currentPositionMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            final int startPosition = 0;
            animation = ObjectAnimator.ofInt(seekBar, "progress", startPosition, playbackDurationMillis);
            animation.setDuration(playbackDurationMillis);
            animation.setCurrentPlayTime(currentPositionMillis);
            animation.setInterpolator(new LinearInterpolator());
            animation.start();
        }
    }

    private void launchElapseTextUpdaterThread() {
        handler = new Handler();
        this.post(new Runnable() {

            @Override
            public void run() {
                // make sure we are playing this audio
                if (AudioController.INSTANCE.mediaForInstance(ExpandedAudioPlaybackView.this)) {
                    int pos = AudioController.INSTANCE.getCurrentPosition();
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (animation != null) {
                animation.removeAllListeners();
                animation.end();
                animation.cancel();
            }
        }

        if (seekBar != null) {
            seekBar.clearAnimation();
            seekBar.setProgress(0);
            seekBar.setEnabled(false);
            updateProgressText(0, seekBar.getMax());
        }
    }

    @Override
    protected void pauseProgressBar() {
        handler.removeCallbacksAndMessages(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            animation.cancel();
        }
    }

    private void updateProgressText(int progress, int max) {
        progressText.setText(milliToHumanReadable(progress) + " / " + milliToHumanReadable(max));
    }

    private static String milliToHumanReadable(int millis) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }
}

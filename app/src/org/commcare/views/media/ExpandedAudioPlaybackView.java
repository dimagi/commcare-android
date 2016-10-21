package org.commcare.views.media;

import android.content.Context;
import android.util.AttributeSet;

import org.commcare.dalvik.R;


/**
 * Audio playback widget with clickable horizontal progress bar
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ExpandedAudioPlaybackView extends AudioPlaybackButtonBase {

    public ExpandedAudioPlaybackView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExpandedAudioPlaybackView(Context context, String URI) {
        super(context, URI, null, true);
    }

    @Override
    protected int getLayout() {
        return R.layout.expanded_audio_playback;
    }

    @Override
    protected void startProgressBar(int milliPosition, int milliDuration) {

    }

    @Override
    protected void resetProgressBar() {

    }

    @Override
    protected void pauseProgressBar() {

    }

}

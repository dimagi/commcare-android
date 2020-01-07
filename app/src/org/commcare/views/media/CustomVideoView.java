package org.commcare.views.media;

import android.content.Context;
import android.widget.VideoView;
import java.util.Date;

/**
 * A custom VideoView which records the total video play duration
 * and send the update via {@link VideoDetachedListener#getPlayDuration(long) getPlayDuration}
 * @author $|-|!˅@M
 */
public class CustomVideoView extends VideoView {

    private VideoDetachedListener listener;

    private long duration;
    private long startTime;

    public CustomVideoView(Context context) {
        super(context);
    }

    public void setListener(VideoDetachedListener listener) {
        this.listener = listener;
    }

    @Override
    public void pause() {
        super.pause();
        duration += new Date().getTime() - startTime;
    }

    @Override
    public void start() {
        super.start();
        startTime = new Date().getTime();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        listener.getPlayDuration(duration);
    }

    public interface VideoDetachedListener {
        void getPlayDuration(long duration);
    }

}

package org.commcare.views.media;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

import java.util.Date;

/**
 * A custom VideoView which records the total video play duration
 * and send the update via {@link VideoDetachedListener#onVideoDetached(long) onVideoDetached}
 * @author $|-|!˅@M
 */
public class CommCareVideoView extends VideoView {

    private VideoDetachedListener listener;

    private long duration;
    private long startTime;

    private String videoPath;

    public CommCareVideoView(Context context) {
        super(context);
    }

    public CommCareVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CommCareVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
        if (listener != null) {
            listener.onVideoDetached(duration);
        }
    }

    @Override
    public void setVideoPath(String videoPath){
        super.setVideoPath(videoPath);
        this.videoPath = videoPath;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public interface VideoDetachedListener {
        void onVideoDetached(long duration);
    }

}
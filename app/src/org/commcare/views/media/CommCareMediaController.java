package org.commcare.views.media;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.MediaController;

import org.commcare.activities.FullscreenVideoViewActivity;
import org.commcare.activities.components.FormEntryConstants;
import org.commcare.dalvik.R;
import org.commcare.utils.AndroidUtil;
import org.commcare.utils.FileUtil;

import java.io.File;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Custom MediaController which provides a workaround to the issue where hide and show aren't
 * working while adding it in the view hierarchy.
 * Note: Use only when you're manually adding MediaController in the view hierarchy.
 * Used here {@link MediaLayout}
 * @author $|-|!˅@M
 */
public class CommCareMediaController extends MediaController {

    public static final String INLINE_VIDEO_TIME_POSITION = "inline-video-time-position";

    // A mock to superclass' isShowing property.
    // {@link https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/MediaController.java#96}
    private boolean _isShowing = false;
    private ImageButton fullscreenBtn;
    private boolean fullscreenMode;

    public CommCareMediaController(Context context, AttributeSet attrs, boolean fullscreenMode) {
        super(context, attrs);
        this.fullscreenMode = fullscreenMode;
    }

    public CommCareMediaController(Context context, boolean useFastForward, boolean fullscreenMode) {
        super(context, useFastForward);
        this.fullscreenMode = fullscreenMode;
    }

    public CommCareMediaController(Context context, boolean fullscreenMode) {
        super(context);
        this.fullscreenMode = fullscreenMode;
    }

    @Override
    public boolean isShowing() {
        return _isShowing;
    }

    @Override
    public void show() {
        super.show();
        _isShowing = true;
        ViewGroup parent = (ViewGroup) this.getParent();
        parent.setVisibility(VISIBLE);
    }

    @Override
    public void hide() {
        super.hide();
        _isShowing = false;
        ViewGroup parent = (ViewGroup) this.getParent();
        parent.setVisibility(View.GONE);
    }

    @Override
    public void setAnchorView(View view) {
        super.setAnchorView(view);

        int videoViewId = fullscreenMode ? R.id.fullscreen_video_view : R.id.inline_video_view;
        CommCareVideoView videoView = view.findViewById(videoViewId);

        if (videoView != null) {
            addFullscreenButton(videoView);
        }
    }

    private void addFullscreenButton(CommCareVideoView videoView) {
        if (fullscreenBtn == null) {
            fullscreenBtn = new ImageButton(getContext(), null, R.style.FullScreenVideoButton);
            fullscreenBtn.setId(AndroidUtil.generateViewId());
            if (fullscreenMode) {
                fullscreenBtn.setImageResource(R.drawable.ic_media_exit_fullscreen);
            } else {
                fullscreenBtn.setImageResource(R.drawable.ic_media_fullscreen);
            }
            fullscreenBtn.setOnClickListener(view1 -> {
                // if in fullscreen mode, we exit
                if (fullscreenMode) {
                    Intent i = new Intent();
                    i.putExtra(CommCareMediaController.INLINE_VIDEO_TIME_POSITION, videoView.getCurrentPosition());
                    ((AppCompatActivity)getContext()).setResult(Activity.RESULT_OK, i);
                    ((AppCompatActivity)getContext()).finish();
                } else {
                    Intent intent = new Intent(getContext(), FullscreenVideoViewActivity.class);
                    intent.setData(FileUtil.getUriForExternalFile(getContext(),
                            new File(videoView.getVideoPath())));
                    if (videoView.isPlaying()) {
                        intent.putExtra(INLINE_VIDEO_TIME_POSITION, videoView.getCurrentPosition());
                    }
                    ((AppCompatActivity) getContext()).startActivityForResult(intent,
                            FormEntryConstants.VIEW_VIDEO_FULLSCREEN);
                }
            });
        }
        FrameLayout.LayoutParams frameParams = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.END);
        this.addView(fullscreenBtn, frameParams);
    }
}

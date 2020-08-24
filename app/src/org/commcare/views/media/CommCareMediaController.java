package org.commcare.views.media;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;

/**
 * Custom MediaController which provides a workaround to the issue where hide and show aren't working while adding it in the view hierarchy.
 * Note: Use only when you're manually adding MediaController in the view hierarchy.
 * Used here {@link MediaLayout}
 * @author $|-|!˅@M
 */
public class CommCareMediaController extends MediaController {

    // A mock to superclass' isShowing property.
    // {@link https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/MediaController.java#96}
    private boolean _isShowing = false;

    public CommCareMediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CommCareMediaController(Context context, boolean useFastForward) {
        super(context, useFastForward);
    }

    public CommCareMediaController(Context context) {
        super(context);
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
}

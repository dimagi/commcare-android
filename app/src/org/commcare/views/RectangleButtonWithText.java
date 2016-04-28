package org.commcare.views;

import android.content.Context;
import android.util.AttributeSet;

import org.commcare.dalvik.R;

/**
 * Rectangular button with custom image, text, and color
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class RectangleButtonWithText extends CustomButtonWithText {
    public RectangleButtonWithText(Context context) {
        super(context);
    }

    public RectangleButtonWithText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RectangleButtonWithText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    int getLayoutResourceId() {
        return R.layout.rectangle_button_text;
    }
}

package org.commcare.android.view;

import android.content.Context;
import android.util.AttributeSet;

import org.commcare.dalvik.R;

/**
 * Square button with custom image, text, and color
 *
 * @author Daniel Luna (dluna@dimagi.com)
 */
public class SquareButtonWithText extends CustomButtonWithText {
    public SquareButtonWithText(Context context) {
        super(context);
    }

    public SquareButtonWithText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareButtonWithText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    int getLayoutResourceId() {
        return R.layout.square_button_text;
    }
}

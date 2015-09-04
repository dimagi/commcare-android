package org.commcare.android.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class RectangleButton extends ImageButton {
    public RectangleButton(Context context) {
        super(context);
    }

    public RectangleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RectangleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);
    }
}

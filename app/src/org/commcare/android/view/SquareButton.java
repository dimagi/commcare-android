package org.commcare.android.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * @author Daniel Luna (dluna@dimagi.com)
 */
public class SquareButton extends ImageButton {
    public SquareButton(Context context) {
        super(context);
    }

    public SquareButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);

        int height;
        if (getDrawable() == null) {
            height = width;
        } else {
            height = width * getDrawable().getIntrinsicHeight() / getDrawable().getIntrinsicWidth();
        }
        setMeasuredDimension(width, height);
    }
}

package org.commcare.android.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * Created by dancluna on 3/14/15.
 */
public class SquareButton extends ImageButton {
    public SquareButton(final Context context) {
        super(context);
    }

    public SquareButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareButton(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {

        //Get canvas width and height
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);

        w = Math.min(w, h);
        h = w;

        setMeasuredDimension(w, h);
    }
}

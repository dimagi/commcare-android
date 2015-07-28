package org.commcare.android.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 * Created by dancluna on 3/14/15.
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

        //Get canvas width and height
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);

        if(w == 0 || h == 0) { // this can happen in the home screen gridView, and then we'll have an invisible view
            w = Math.max(w, h);
        } else {
            w = Math.min(w, h);
        }

        h = w;

        setMeasuredDimension(w,w);
    }
}

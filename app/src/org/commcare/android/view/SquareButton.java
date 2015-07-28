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
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (width == 0 || height == 0) {
            /*
             * If this happens (for instance, the home screen GridView tries to force all its child views to have a height of 0),
             * we avoid making this button invisible by picking the greatest of the two dimensions.
             */
            width = Math.max(width, height);
        } else {
            width = Math.min(width, height);
        }

        setMeasuredDimension(width,width);
    }
}

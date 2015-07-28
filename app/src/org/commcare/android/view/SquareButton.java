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
        int canvasWidth = MeasureSpec.getSize(widthMeasureSpec);
        int canvasHeight = MeasureSpec.getSize(heightMeasureSpec);

        int squareCanvasDimension = Math.min(canvasWidth, canvasHeight);
        if (squareCanvasDimension <= 0) {
            // At times GridView forces its child views to have a height/width
            // of 0. Avoid button invisibility by choosing the max dimension.
            squareCanvasDimension = Math.max(canvasWidth, canvasHeight);
        }

        setMeasuredDimension(squareCanvasDimension, squareCanvasDimension);
    }
}

package org.commcare.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SquareImageView extends ImageView {

    public SquareImageView(Context context) {
        super(context);
    }

    public SquareImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);

        final Drawable image = getDrawable();
        if (image == null) {
            setMeasuredDimension(width, width);
        } else {
            int height = (width * image.getIntrinsicHeight()) / image.getIntrinsicWidth();
            setMeasuredDimension(width, height);
        }
    }
}

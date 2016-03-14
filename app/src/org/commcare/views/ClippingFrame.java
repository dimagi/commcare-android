package org.commcare.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import org.commcare.dalvik.R;

/**
 * Created by ctsims on 3/14/2016.
 */
public class ClippingFrame extends FrameLayout {

    private Rect mClipBounds= new Rect(0,0,1,1);

    private float startX;
    private float startY;
    private float clipWidth;
    private float clipHeight;

    public ClippingFrame(Context context, AttributeSet attrs) {
        super(context, attrs);

        setParams(context, attrs);
    }

    public ClippingFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setParams(context, attrs);
    }

    private void setParams(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ClippingView, 0, 0);
            startX = typedArray.getFloat(R.styleable.ClippingView_clip_start_x, 0.5f);
            startY = typedArray.getFloat(R.styleable.ClippingView_clip_start_y, 0.5f);
            clipWidth = typedArray.getFloat(R.styleable.ClippingView_clip_width, 1);
            clipHeight = typedArray.getFloat(R.styleable.ClippingView_clip_height, 1);
        }
    }

    public void setClipWidth(float clipWidth) {
        this.clipWidth = clipWidth;
        recalculateClippingBounds();
        this.invalidate();
    }

    private void recalculateClippingBounds() {
        int width = this.getMeasuredWidth();
        int height = this.getMeasuredHeight();


        int centerX = (int)(startX * width);

        int leftCoverage = (int)Math.ceil(clipWidth * centerX);
        int rightCoverage = (int)Math.ceil(clipWidth * (width - centerX));

        int leftStart = Math.max(0, centerX - leftCoverage);
        int rightEnd = Math.min(width, centerX + rightCoverage);


        int centerY = (int)(startY * height);

        int topCoverage = (int)Math.ceil(clipHeight * centerY);
        int bottomCoverage = (int)Math.ceil(clipHeight * (height - centerY));

        int topStart = Math.max(0, centerY - topCoverage);
        int bottomEnd = Math.min(height, centerY + bottomCoverage);

        mClipBounds.set(leftStart, topStart, rightEnd, bottomEnd);
    }

    public void setClipHeight(float clipHeight) {
        this.clipHeight = clipHeight;
        recalculateClippingBounds();
        this.invalidate();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        recalculateClippingBounds();
    }

    @Override
    public void draw(Canvas canvas) {
        //Clip boundary implementaiton, since we're targeting pre-jellybean.
        if (mClipBounds != null) {
            canvas.clipRect(mClipBounds);
        }
        super.draw(canvas);
    }


}

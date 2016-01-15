package org.commcare.android.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * A layout that will enforce a given aspect ratio. Layout should define the attributes ratio_width and ratio_height.
 * @author jschweers
 */
public class AspectRatioLayout extends FrameLayout {
    float mRatioWidth;
    float mRatioHeight;

    public AspectRatioLayout(Context context) {
        super(context);
    }

    public AspectRatioLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeRatio(attrs);
    }

    public AspectRatioLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initializeRatio(attrs);
    }
    
    /**
     * Set layout's aspect ratio.
     */
    public void setRatio(float ratioWidth, float ratioHeight) {
        mRatioWidth = ratioWidth;
        mRatioHeight = ratioHeight;
    }
    
    private void initializeRatio(AttributeSet attrs) {
        if(!isInEditMode()) {
            String namespace = "http://schemas.android.com/apk/lib/" + this.getClass().getPackage().getName();
            mRatioWidth = attrs.getAttributeFloatValue(namespace, "ratio_width", 1);
            mRatioHeight = attrs.getAttributeFloatValue(namespace, "ratio_height", 1);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((int) (widthMeasureSpec * mRatioHeight / mRatioWidth), MeasureSpec.EXACTLY)
        );
    }
}

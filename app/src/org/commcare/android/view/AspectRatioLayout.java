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

    public AspectRatioLayout(final Context context) {
        super(context);
    }

    public AspectRatioLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        initializeRatio(context, attrs);
    }

    public AspectRatioLayout(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        initializeRatio(context, attrs);
    }
    
    private void initializeRatio(final Context context, final AttributeSet attrs) {
        if(!isInEditMode()) {
            final String namespace = "http://schemas.android.com/apk/lib/" + this.getClass().getPackage().getName();
            mRatioWidth = attrs.getAttributeFloatValue(namespace, "ratio_width", 1);
            mRatioHeight = attrs.getAttributeFloatValue(namespace, "ratio_height", 1);
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((int) (widthMeasureSpec * mRatioHeight / mRatioWidth), MeasureSpec.EXACTLY)
        );
    }
}

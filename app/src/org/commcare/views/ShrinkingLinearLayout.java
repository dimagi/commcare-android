package org.commcare.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.commcare.dalvik.R;
import org.commcare.utils.AndroidUtil;

/**
 * A shrinking linear layout is capable of denoting one child view which can change its size
 * if the full space is not needed.
 *
 * Normally a linear layout requires either fully laying out (IE: Pre-determined static sizes or
 * ratios) all of its children _or_ accepting that if the linear layout grows larger than its parent
 * some views will be cut off.
 *
 * When designing a shrinking layout, the view can be laid out fully "expanded" (IE: All of the
 * child views are the largest they will ever be), then one view can be chosen to shrink to its
 * "natural" width if it doesn't need the full space. This allows for things like text fields with
 * an element next to the text that can grow horizontally _until_ running into the fully "expanded"
 * layout.
 *
 * Limitations:
 * This layout is incrementally slower than a traditional linear layout.
 * The view will currently only shrink horizontally
 *
 * Created by ctsims on 5/18/2016.
 */
public class ShrinkingLinearLayout extends LinearLayout {
    int shrinkingViewId = -1;

    public ShrinkingLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadViewConfig(context, attrs);
    }

    private void loadViewConfig(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ShrinkingLinearLayout);
        shrinkingViewId = typedArray.getResourceId(R.styleable.ShrinkingLinearLayout_shrinkable_view, -1);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View dynamicView = this.findViewById(shrinkingViewId);

        if(shrinkingViewId == -1 || dynamicView == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        //Figure out how much width the constrained view _really_ wants
        dynamicView.measure(MeasureSpec.UNSPECIFIED, heightMeasureSpec);
        int desiredWidth = dynamicView.getMeasuredWidth();

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        //If after everything is measured the view is bigger than it needs to be, forget the other
        //layout directives, and have the view only request that width
        if(dynamicView.getMeasuredWidth() > desiredWidth) {
            LayoutParams params = (LinearLayout.LayoutParams)dynamicView.getLayoutParams();

            //NOTE: Any parameters used to control the final size will need to be copied in this
            //way. API19+ supports copy construction for layout params, but that's a ways away
            int oldWidth = params.width;
            float oldWeight = params.weight;

            params.width = desiredWidth;
            params.weight = 0;

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            //reset the parameters, since they are directives, not values.
            params.width = oldWidth;
            params.weight = oldWeight;
        }

    }
}

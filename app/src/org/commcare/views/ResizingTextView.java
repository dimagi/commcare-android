package org.commcare.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * A basic text view that will resize itself to update its text size if text wraps longer than
 * a single line
 *
 * Created by ctsims on 3/14/15.
 */
public class ResizingTextView extends TextView {
    private boolean isResizable = false;
    private int mSmallTextPixels;
    private float mOriginalTextSize;

    private boolean mHasTriedSmallLayout = false;

    public ResizingTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        setParams(context, attrs);
    }

    public ResizingTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setParams(context, attrs);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        if (!getText().equals(text)) {
            resetOriginalTextSize();
        }
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
    }

    private void resetOriginalTextSize() {
        if (isResizable && mHasTriedSmallLayout) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, mOriginalTextSize);
            mHasTriedSmallLayout = false;
            requestLayout();
        }
    }

    private void setParams(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ResizingTextView, 0, 0);
            mSmallTextPixels = typedArray.getDimensionPixelSize(R.styleable.ResizingTextView_text_size_small, -1);
            if (mSmallTextPixels != -1) {
                isResizable = true;
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (isResizable && !mHasTriedSmallLayout && this.getLineCount() > 1) {
            setTextSizeToSmall();
        }
    }

    private void setTextSizeToSmall() {
        mOriginalTextSize = getTextSize();
        mHasTriedSmallLayout = true;
        setTextSize(TypedValue.COMPLEX_UNIT_PX, mSmallTextPixels);
        requestLayout();
    }
}

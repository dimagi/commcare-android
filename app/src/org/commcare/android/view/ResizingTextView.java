package org.commcare.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import org.commcare.dalvik.R;

/**
 * A basic text view that will resize itself to update its text size if text wraps longer than
 * a single line
 * <p/>
 * Created by ctsims on 3/14/15.
 */
public class ResizingTextView extends TextView {

    private boolean mIsResizing = false;
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
        this.reset();
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
    }

    private void reset() {
        if (!mIsResizing) {
            return;
        }
        if (mHasTriedSmallLayout) {
            this.setTextSize(TypedValue.COMPLEX_UNIT_PX, mOriginalTextSize);
            this.mHasTriedSmallLayout = false;
        }
    }

    private void setParams(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ResizingTextView, 0, 0);
            mSmallTextPixels = typedArray.getDimensionPixelSize(R.styleable.ResizingTextView_text_size_small, -1);
            if (mSmallTextPixels != -1) {
                mIsResizing = true;
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (!mIsResizing) {
            return;
        }

        if (!mHasTriedSmallLayout && this.getLineCount() > 1) {
            setTextSizeToSmall();
        }
        reset();
    }

    private void setTextSizeToSmall() {
        mOriginalTextSize = this.getTextSize();
        this.setTextSize(TypedValue.COMPLEX_UNIT_PX, mSmallTextPixels);
        mHasTriedSmallLayout = true;
        this.requestLayout();
    }
}

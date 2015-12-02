package org.odk.collect.android.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.TextView;

/**
 * @author ctsims
 */
public class ShrinkingTextView extends TextView {
    
    private boolean mExpanded = false;
    private boolean mInteractive = true;
    private static final int mAnimationDuration = 500;
    
    private ExpandAnimation mCurrentAnimation;
    
    private int mFullHeight = -1;
    private int mMaxHeight;
    private int mSetMaxHeight = -1;
    
    private int mAnimatingHeight= -1;

    public ShrinkingTextView(Context context, int max) {
        super(context);
        this.setOnClickListener(new PanelToggler());
        updateMaxHeight(max);
    }
    
    public void updateMaxHeight(int maxHeight) {
        int contentHeightAdj = this.getPaddingBottom() + this.getPaddingTop();
        
        float tH = this.getTextSize();
        float lH = this.getLineHeight();
        float stH = lH + contentHeightAdj;
        
        mSetMaxHeight = maxHeight;
        
        //Don't allow this control to be smaller than one line
        if(maxHeight * 1f < stH) {
            mMaxHeight = (int)stH;
        } else {
            //Otherwise, count up lines until this will spill over, then 
            //set the max height to be the bottom of the lowest line
            int contentHeight = maxHeight - contentHeightAdj;
            float countUp = tH;
            
            while(countUp + lH < contentHeight) {
                countUp = countUp + lH;
            }
            
            //We're now on the last full line. We either want to put our baseline at the bottom of this line,
            //or the bottom of the next
            
            if(countUp + tH < contentHeight) {
                countUp = countUp + tH;
            }

            mMaxHeight = (int)(countUp + contentHeightAdj);
        }
        
        //TODO: Don't mess with this during animation?
        //this.mMaxHeight = maxHeight;
        resetDynamicLayout();
        //TODO: request layout _on change_?
        this.requestLayout();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if(mInteractive && !mExpanded || isAnimating()) {
            Rect r = new Rect();
            this.getDrawingRect(r);
            Paint p = new Paint();
            
            float f = this.getTextSize();
            int px = (int)(f / 2);
            
            int bottom = r.bottom - this.getPaddingBottom();
            int top = bottom - px;
            
            int start = Color.argb(0, 255, 255, 255);
            int end = Color.argb(220, 255, 255, 255);
            
            Shader shader = new LinearGradient(r.left, top, r.left, bottom, start, end, TileMode.CLAMP);
            p.setShader(shader);
            
            r.set(r.left, top, r.right, bottom);
            p.setStyle(Paint.Style.FILL);
            canvas.drawRect(r, p);
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        this.resetDynamicLayout();
    }

    private void resetDynamicLayout() {
        //don't manipulate the expanded flag.
        
        mInteractive = true;
        mFullHeight = -1;
        if(mMaxHeight == -1) { mInteractive = false; mExpanded = false;}
    }

    private class PanelToggler implements OnClickListener {
        public void onClick(View v) {
            if(!mInteractive || isAnimating()) { return; }
            
            if (mExpanded) {
                mCurrentAnimation = new ExpandAnimation(mFullHeight, mMaxHeight);
            } else {
                mCurrentAnimation = new ExpandAnimation(mMaxHeight, mFullHeight);
            }
            mCurrentAnimation.setDuration(mAnimationDuration);
            ShrinkingTextView.this.startAnimation(mCurrentAnimation);
            mExpanded = !mExpanded;
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        
        if(isAnimating()) { 
            this.setMeasuredDimension(this.getMeasuredWidth(), mAnimatingHeight);
        }
        
        if(mExpanded || !mInteractive || isAnimating()) {
            return;
        }
        
        //We're in a collapsed state we think is interactive. First,
        //measure the total height
        mFullHeight = mFullHeight == -1 ? this.getMeasuredHeight() : mFullHeight;
        
        //The full height here isn't greater than the max,
        //no need for heroics
        if(mFullHeight <= mSetMaxHeight) { 
            mInteractive = false;
            return;
        }
        int measuredWidth = this.getMeasuredWidth();
        
        this.setMeasuredDimension(measuredWidth, mMaxHeight);
    }

    private boolean isAnimating() {
        return !(mCurrentAnimation == null || mCurrentAnimation.hasEnded() || !mCurrentAnimation.hasStarted());
    }

    private class ExpandAnimation extends Animation {
        private final int mStartHeight;
        private final int mDeltaHeight;

        public ExpandAnimation(int startHeight, int endHeight) {
            mStartHeight = startHeight;
            mDeltaHeight = endHeight - startHeight;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            //ShrinkingTextView.this.setHeight((int) (mStartHeight + mDeltaHeight * interpolatedTime));
            mAnimatingHeight = (int) (mStartHeight + mDeltaHeight * interpolatedTime);
            ShrinkingTextView.this.requestLayout();
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }
    }
}

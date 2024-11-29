package org.commcare.activities.components;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

public class RoundedButtonDrawable extends Drawable {
    private int backgroundColor;
    private float cornerRadius;
    private boolean borderVisible;
    private int borderColor;
    private float borderRadius;
    private float borderWidth;
    private Paint backgroundPaint;
    private Paint borderPaint;

    // Constructor
    public RoundedButtonDrawable(int cornerRadius, int backgroundColor, boolean borderVisible, int borderColor, float borderRadius, float borderWidth) {
        this.cornerRadius = cornerRadius;
        this.backgroundColor = backgroundColor;
        this.borderVisible = borderVisible;
        this.borderColor = borderColor;
        this.borderRadius = borderRadius;
        this.borderWidth = borderWidth;

        // Initialize background paint
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(backgroundColor);

        // Initialize border paint if border is visible
        if (borderVisible) {
            borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(borderColor);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(borderWidth);
        }
    }

    // Method to set the background color
    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        backgroundPaint.setColor(color);
        invalidateSelf();
    }

    // Method to get the corner radius
    public float getCornerRadius() {
        return cornerRadius;
    }

    // Method to set the corner radius
    public void setRadius(float radius) {
        this.cornerRadius = radius;
        invalidateSelf();
    }

    // Method to set border visibility
    public void setBorderVisible(boolean visible) {
        this.borderVisible = visible;
        if (visible && borderPaint == null) {
            borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(borderColor);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(borderWidth);
        }
        invalidateSelf();
    }

    // Method to set border color
    public void setBorderColor(int color) {
        this.borderColor = color;
        if (borderPaint != null) {
            borderPaint.setColor(color);
        }
        invalidateSelf();
    }

    // Method to set border radius
    public void setBorderRadius(float radius) {
        this.borderRadius = radius;
        invalidateSelf();
    }

    // Method to set border width
    public void setBorderWidth(float width) {
        this.borderWidth = width;
        if (borderPaint != null) {
            borderPaint.setStrokeWidth(width);
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        // Draw the background with rounded corners
        canvas.drawRoundRect(new RectF(getBounds()), cornerRadius, cornerRadius, backgroundPaint);

        // Draw the border if visible
        if (borderVisible && borderPaint != null) {
            float halfStrokeWidth = borderPaint.getStrokeWidth() / 2;
            RectF borderRect = new RectF(getBounds());
            borderRect.inset(halfStrokeWidth, halfStrokeWidth);
            canvas.drawRoundRect(borderRect, borderRadius, borderRadius, borderPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        backgroundPaint.setAlpha(alpha);
        if (borderPaint != null) {
            borderPaint.setAlpha(alpha);
        }
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        backgroundPaint.setColorFilter(colorFilter);
        if (borderPaint != null) {
            borderPaint.setColorFilter(colorFilter);
        }
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    private int dpToPx(int dp) {
        float density = Resources.getSystem().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

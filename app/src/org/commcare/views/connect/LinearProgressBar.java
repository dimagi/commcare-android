package org.commcare.views.connect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.commcare.dalvik.R;

public class LinearProgressBar extends View {

    private Paint backgroundPaint;
    private Paint progressPaint;
    private float progress = 0f;
    private float strokeWidth = 10f;
    private int startCornerRadius = 10; // Start corner radius
    private int endCornerRadius = 10;   // End corner radius
    private int[] gradientColors = {Color.BLUE, Color.GREEN};
    private boolean isGradient = false;
    private int progressColor = Color.BLUE;
    private int backgroundColor;

    public LinearProgressBar(Context context) {
        super(context);
        init(context);
    }

    public LinearProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public LinearProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        backgroundPaint = new Paint();
        backgroundColor = ContextCompat.getColor(context, R.color.connect_un_fill_progress);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setAntiAlias(true);

        progressPaint = new Paint();
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw the background with rounded corners
        RectF backgroundRect = new RectF(0, 0, width, height);
        canvas.drawRoundRect(backgroundRect, startCornerRadius, startCornerRadius, backgroundPaint);

        // Draw the progress with rounded corners at the end
        float progressWidth = (progress / 100f) * width;
        RectF progressRect = new RectF(0, 0, progressWidth, height);

        if (isGradient && gradientColors != null && gradientColors.length > 1) {
            LinearGradient shader = new LinearGradient(
                    0, 0, progressWidth, 0,
                    gradientColors, null, Shader.TileMode.CLAMP);
            progressPaint.setShader(shader);
        } else {
            progressPaint.setShader(null);
            progressPaint.setColor(progressColor);
        }
        canvas.drawRoundRect(progressRect, endCornerRadius, endCornerRadius, progressPaint);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0, Math.min(progress, 100)); // Clamp between 0 and 100
        invalidate();
    }

    public void setGradientColors(int[] colors) {
        this.gradientColors = colors;
        this.isGradient = true;
        invalidate();
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
        // Not applicable for a linear bar, but can be implemented if necessary
        invalidate();
    }

    public void setProgressColor(int color) {
        Log.e("LinearProgressBar", "Setting progress color to: " + color);
        this.progressColor = color;
        this.isGradient = false;
        progressPaint.setColor(color);
        invalidate();
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        backgroundPaint.setColor(color);
        invalidate();
    }
}

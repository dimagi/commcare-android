package org.commcare.views.connect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class CircleProgressBar extends View {

    private Paint backgroundPaint;
    private Paint progressPaint;
    private float progress = 0f;
    private float strokeWidth = 10f;
    private int startAngle = 270;
    private int[] gradientColors = {Color.BLUE, Color.GREEN};
    private boolean isGradient = false;
    private int progressColor = Color.BLUE;
    private int backgroundColor = Color.LTGRAY;

    public CircleProgressBar(Context context) {
        super(context);
        init();
    }

    public CircleProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircleProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);
        backgroundPaint.setAntiAlias(true);

        progressPaint = new Paint();
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setAntiAlias(true);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int radius = Math.min(width, height) / 2 - (int) strokeWidth;
        int centerX = width / 2;
        int centerY = height / 2;

        canvas.drawCircle(centerX, centerY, radius, backgroundPaint);
        RectF rectF = new RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
        float sweepAngle = progress * 360f / 100f;
        if (isGradient) {
            Shader shader = new SweepGradient(centerX, centerY, gradientColors, null);
            progressPaint.setShader(shader);
        } else {
            progressPaint.setShader(null);
            progressPaint.setColor(progressColor);
        }
        canvas.drawArc(rectF, startAngle, sweepAngle, false, progressPaint);
    }

    public void setProgress(float progress) {
        if (progress < 0f) {
            progress = 0f;
        } else if (progress > 100f) {
            progress = 100f;
        }
        this.progress=progress;
        invalidate();
    }

    public void setGradientColors(int[] colors) {
        this.gradientColors = colors;
        this.isGradient = true;
        invalidate();
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
        backgroundPaint.setStrokeWidth(width);
        progressPaint.setStrokeWidth(width);
        invalidate();
    }

    public void setProgressColor(int color) {
        Log.d("CircleProgressBar", "Setting progress color to: " + color);
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
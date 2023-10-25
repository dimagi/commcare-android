package org.commcare.views;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;

import org.commcare.dalvik.R;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class FaceCaptureView extends AppCompatImageView {

    private int faceCaptureAreaDelimiterColor;
    private int backgroundColor;
    private RectF faceCaptureArea = null;
    private int imageWidth;
    private int imageHeight;
    public static int DEFAULT_IMAGE_WIDTH = 480;
    public static int DEFAULT_IMAGE_HEIGHT = 640;
    private static float VIEW_CAPTURE_AREA_RATIO = 0.8f;

    public FaceCaptureView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        loadViewAttribs(attrs);
        int orientation = this.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            imageWidth = DEFAULT_IMAGE_WIDTH;
            imageHeight = DEFAULT_IMAGE_HEIGHT;
        } else {
            imageWidth = DEFAULT_IMAGE_HEIGHT;
            imageHeight = DEFAULT_IMAGE_WIDTH;
        }
    }

    private void loadViewAttribs(AttributeSet attrs) {
        TypedArray typedArr = getContext().obtainStyledAttributes(attrs, R.styleable.FaceCaptureView);
        try {
            faceCaptureAreaDelimiterColor = typedArr.getColor(R.styleable.FaceCaptureView_face_capture_area_delimiter_color, Color.WHITE);
            backgroundColor = typedArr.getColor(R.styleable.FaceCaptureView_background_color, Color.LTGRAY);
        } finally {
            typedArr.recycle();
        }
    }

    private void initCameraView(int viewWidth, int viewHeight){
        setFaceCaptureArea(viewWidth, viewHeight);

        Bitmap previewOverlay = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(previewOverlay);
        canvas.drawColor(backgroundColor);

        // draw capture area delimiter
        Paint faceCapturePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        faceCapturePaint.setStyle(Paint.Style.STROKE);
        faceCapturePaint.setColor(faceCaptureAreaDelimiterColor);
        int squareWidth = (int)((faceCaptureArea.width() + faceCaptureArea.height()) / 2);
        faceCapturePaint.setStrokeWidth(0.01f * squareWidth);
        canvas.drawOval(faceCaptureArea, faceCapturePaint);

        // draw clear oval
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawOval(faceCaptureArea, paint);

        setImageBitmap(previewOverlay);
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w != 0 && h !=0) {
            initCameraView(w, h);
        }
    }

    private void setFaceCaptureArea(int width, int height) {
        int captureAreaWidth = (int)(width * VIEW_CAPTURE_AREA_RATIO);
        int captureAreaHeigth = (int)(height * VIEW_CAPTURE_AREA_RATIO);

        int captureAreaLeft = (width - captureAreaWidth) / 2;
        int captureAreaTop = (height - captureAreaHeigth) / 2;
        int captureAreaRight = captureAreaLeft + captureAreaWidth;
        int captureAreaBottom = captureAreaTop + captureAreaHeigth;

        faceCaptureArea = new RectF(captureAreaLeft, captureAreaTop, captureAreaRight, captureAreaBottom);
    }
}

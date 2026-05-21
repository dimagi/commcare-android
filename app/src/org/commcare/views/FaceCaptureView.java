package org.commcare.views;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.google.mlkit.vision.face.Face;

import org.commcare.dalvik.R;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class FaceCaptureView extends AppCompatImageView {

    public interface ImageStabilizedListener {
        void onImageStabilizedListener(Rect faceArea);
    }

    private int faceCaptureAreaDelimiterColor;
    private int backgroundColor;
    private int faceMarkerColor;
    private int countdownTextSizeSp;
    private RectF faceCaptureArea = null;
    private int imageWidth;
    private int imageHeight;
    public static int DEFAULT_IMAGE_WIDTH = 480;
    public static int DEFAULT_IMAGE_HEIGHT = 640;
    private static float VIEW_CAPTURE_AREA_RATIO = 0.8f;
    private Object lock = new Object();
    private FaceOvalGraphic faceOvalGraphic;
    private float postScaleHeightOffset;
    private float postScaleWidthOffset;
    private float scaleFactor;
    private ImageStabilizedListener imageStabilizedListener;
    public enum CaptureMode {FaceDetectionMode, ManualMode}
    private CaptureMode captureMode = CaptureMode.FaceDetectionMode;

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

    public void setCaptureMode(CaptureMode captureMode){
        this.captureMode = captureMode;

        if (captureMode == CaptureMode.ManualMode) {
            imageStabilizedListener = null;
            faceOvalGraphic = null;
        }
        invalidate();
    }

    public CaptureMode getCaptureMode() {
        return captureMode;
    }

    private void loadViewAttribs(AttributeSet attrs) {
        TypedArray typedArr = getContext().obtainStyledAttributes(attrs, R.styleable.FaceCaptureView);
        try {
            faceCaptureAreaDelimiterColor = typedArr.getColor(R.styleable.FaceCaptureView_face_capture_area_delimiter_color, Color.WHITE);
            backgroundColor = typedArr.getColor(R.styleable.FaceCaptureView_background_color, Color.LTGRAY);
            faceMarkerColor = typedArr.getColor(R.styleable.FaceCaptureView_face_marker_color, Color.GREEN);
            countdownTextSizeSp = typedArr.getDimensionPixelSize(R.styleable.FaceCaptureView_countdown_text_size, -1);
        } finally {
            typedArr.recycle();
        }
    }

    private void initCameraView(int viewWidth, int viewHeight){
        setFaceCaptureArea(calcCaptureArea(viewWidth, viewHeight));
        calcScaleFactors(viewWidth, viewHeight);

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

        if (captureMode == CaptureMode.FaceDetectionMode) {
            faceOvalGraphic = new FaceOvalGraphic();
        } else {
            faceOvalGraphic = null;
        }
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        synchronized (lock) {
            if (faceOvalGraphic != null) {
                faceOvalGraphic.drawFaceOval(canvas);
            }
        }
    }

    public void updateFace(Face face) {
        if (!faceOvalGraphic.isFaceBlank() || face != null) {
            if (face == null) {
                faceOvalGraphic.clearFace();
            } else {
                faceOvalGraphic.updateFace(face);
            }
            postInvalidate();
        }
    }

    public void setImageStabilizedListener(ImageStabilizedListener imageStabilizedListener) {
        this.imageStabilizedListener = imageStabilizedListener;
    }

    public RectF getFaceCaptureArea() {
        return faceCaptureArea;
    }

    private void setFaceCaptureArea(RectF faceCaptureArea) {
        this.faceCaptureArea = faceCaptureArea;
    }

    public RectF calcCaptureArea(int width, int height) {
        int captureAreaWidth = (int)(width * VIEW_CAPTURE_AREA_RATIO);
        int captureAreaHeigth = (int)(height * VIEW_CAPTURE_AREA_RATIO);

        int captureAreaLeft = (width - captureAreaWidth) / 2;
        int captureAreaTop = (height - captureAreaHeigth) / 2;
        int captureAreaRight = captureAreaLeft + captureAreaWidth;
        int captureAreaBottom = captureAreaTop + captureAreaHeigth;

        return new RectF(captureAreaLeft, captureAreaTop, captureAreaRight, captureAreaBottom);
    }

    private void calcScaleFactors(int viewWidth, int viewHeight) {
        float viewAspectRatio = (float) viewWidth / viewHeight;
        float imageAspectRatio = (float) imageWidth / imageHeight;
        postScaleWidthOffset = 0;
        postScaleHeightOffset = 0;
        if (viewAspectRatio > imageAspectRatio) {
            // The image needs to be vertically cropped to be displayed in this view.
            scaleFactor = (float) viewWidth / imageWidth;
            postScaleHeightOffset = ((float) viewWidth / imageAspectRatio - viewHeight) / 2;
        } else {
            // The image needs to be horizontally cropped to be displayed in this view.
            scaleFactor = (float) viewHeight / imageHeight;
            postScaleWidthOffset = ((float) viewHeight * imageAspectRatio - viewWidth) / 2;
        }
    }

    /**
     * Translate coordinates from the preview's system to the view system.
     */
    private Rect translateFaceOvalCoordinates(Rect boundingBox){
        float x0 = scaleX(boundingBox.left);
        float y0 = scaleY(boundingBox.top);
        float dx = scaleX(boundingBox.right);
        float dy = scaleY(boundingBox.bottom);
        return new Rect((int)x0, (int)y0, (int)dx, (int)dy);
    }

    private float scaleY(float vertical) {
        return vertical * scaleFactor - postScaleHeightOffset;
    }

    private float scaleX(float horizontal) {
        return horizontal * scaleFactor - postScaleWidthOffset;
    }

    private class FaceOvalGraphic {
        private Paint faceAreaPaint;
        private Face currFace;
        private static final int IMAGE_STABILIZATION_BUFFER = 5;
        private static final int COUNTDOWN_START = 3;
        private int countdown = COUNTDOWN_START;
        private Paint faceAreaTextPaint;

        public FaceOvalGraphic(){
            faceAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            faceAreaPaint.setStyle(Paint.Style.STROKE);
            faceAreaPaint.setColor(faceMarkerColor);
            faceAreaPaint.setStrokeWidth(10);

            faceAreaTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            faceAreaTextPaint.setTextSize((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, countdownTextSizeSp, getResources().getDisplayMetrics()));
            faceAreaTextPaint.setTextAlign(Paint.Align.CENTER);
            faceAreaTextPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            faceAreaTextPaint.setColor(faceMarkerColor);
        }

        public void updateFace(Face face) {
            if (isFaceStable(face.getBoundingBox()) && isFaceInCaptureArea(face.getBoundingBox())) {
                currFace = face;
                if (countdown > 0) {
                    countdown--;
                }
            } else {
                clearFace();
            }
        }

        public void clearFace(){
            currFace = null;
            countdown = COUNTDOWN_START;
        }

        public void drawFaceOval(Canvas canvas) {
            if (!isFaceBlank()) {
                Rect faceOvalCoord = translateFaceOvalCoordinates(currFace.getBoundingBox());
                canvas.drawOval(faceOvalCoord.left, faceOvalCoord.top, faceOvalCoord.right, faceOvalCoord.bottom, faceAreaPaint);

                Point textCoord = calcTextPosition(faceOvalCoord);
                canvas.drawText(countdown != COUNTDOWN_START? String.valueOf(countdown):"", textCoord.x, textCoord.y, faceAreaTextPaint);

                if (countdown == 0) {
                    imageStabilizedListener.onImageStabilizedListener(currFace.getBoundingBox());
                }
            }
        }

        private Point calcTextPosition(Rect faceOval) {
            int xPos = faceOval.left + (faceOval.width() / 2);
            int yPos = faceOval.top + (int) ((faceOval.height() / 2) - ((faceAreaTextPaint.descent() + faceAreaTextPaint.ascent()) / 2));
            return new Point(xPos, yPos);
        }

        private boolean isFaceBlank() {
            return currFace == null;
        }

        private boolean isFaceInCaptureArea(Rect faceCoords){
            Rect faceViewCoords = translateFaceOvalCoordinates(faceCoords);
            if ((faceViewCoords.left < faceCaptureArea.left) ||
                    (faceViewCoords.top < faceCaptureArea.top) ||
                    (faceViewCoords.right > faceCaptureArea.right) ||
                    (faceViewCoords.bottom > faceCaptureArea.bottom)) {
                return false;
            }
            return true;
        }

        private boolean isFaceStable(Rect newFaceArea) {
            if (currFace == null || (currFace != null && areRectsEqual(newFaceArea, currFace.getBoundingBox()))) {
                return true;
            }
            return false;
        }

        private boolean areRectsEqual(Rect a, Rect b) {
            if ((Math.abs(a.left - b.left) < IMAGE_STABILIZATION_BUFFER) &&
                    (Math.abs(a.top - b.top) < IMAGE_STABILIZATION_BUFFER) &&
                    (Math.abs(a.right - b.right) < IMAGE_STABILIZATION_BUFFER) &&
                    (Math.abs(a.bottom - b.bottom) < IMAGE_STABILIZATION_BUFFER)) {
                return true;
            }
            return false;
        }
    }
}

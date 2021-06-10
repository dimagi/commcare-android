package org.commcare.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.commcare.dalvik.R;
import org.commcare.utils.MediaUtil;
import org.commcare.views.widgets.SignatureWidget;

import java.io.File;

public class DrawView extends View {

    public interface Callback {
        void drawn();
    }

    private boolean isSignature;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private final Path mCurrentPath;
    private final Paint mBitmapPaint;
    private File mBackgroundBitmapFile;
    private final Paint paint;
    private final Paint pointPaint;
    private float mX, mY;
    private Callback callback;

    public DrawView(final Context c, Paint paint, Paint pointPaint) {
        super(c);

        this.paint = paint;
        this.pointPaint = pointPaint;
        isSignature = false;
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
        mCurrentPath = new Path();
        setBackgroundColor(0xFFFFFFFF);
        mBackgroundBitmapFile = SignatureWidget.getTempFileForDrawingCapture();
    }

    public DrawView(Context c, boolean isSignature, File f, Paint paint, Paint pointPaint) {
        this(c, paint, pointPaint);

        this.isSignature = isSignature;
        mBackgroundBitmapFile = f;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void removeCallback() {
        this.callback = null;
    }

    public void reset() {
        Display display = ((WindowManager)getContext().getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        int screenWidth = display.getWidth();
        int screenHeight = display.getHeight();
        resetImage(screenWidth, screenHeight);
    }

    public void resetImage(int w, int h) {
        if (mBackgroundBitmapFile.exists()) {
            mBitmap = MediaUtil.getBitmapScaledToContainer(
                    mBackgroundBitmapFile, w, h).copy(
                    Bitmap.Config.RGB_565, true);
            // mBitmap =
            // Bitmap.createScaledBitmap(BitmapFactory.decodeFile(mBackgroundBitmapFile.getPath()),
            // w, h, true);
            mCanvas = new Canvas(mBitmap);
        } else {
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            mCanvas = new Canvas(mBitmap);
            mCanvas.drawColor(0xFFFFFFFF);
            if (isSignature) {
                drawSignLine();
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetImage(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(getResources().getColor(R.color.grey));
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        canvas.drawPath(mCurrentPath, paint);
    }

    private void touch_start(float x, float y) {
        mCurrentPath.reset();
        mCurrentPath.moveTo(x, y);
        mX = x;
        mY = y;
    }

    public void drawSignLine() {
        mCanvas.drawLine(0, (int)(mCanvas.getHeight() * .7),
                mCanvas.getWidth(), (int)(mCanvas.getHeight() * .7), paint);
    }

    private void touch_move(float x, float y) {
        double distance = Math.sqrt(Math.pow(x - mX, 2) + Math.pow(y - mY, 2));
        if (callback != null && distance > 5.0) {
            callback.drawn();
        }
        mCurrentPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
        mX = x;
        mY = y;
    }

    private void touch_up() {
        if (mCurrentPath.isEmpty()) {
            mCanvas.drawPoint(mX, mY, pointPaint);
        } else {
            mCurrentPath.lineTo(mX, mY);
            // commit the path to our offscreen
            mCanvas.drawPath(mCurrentPath, paint);
        }
        // kill this so we don't double draw
        mCurrentPath.reset();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touch_start(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touch_move(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touch_up();
                invalidate();
                break;
        }
        return true;
    }
}

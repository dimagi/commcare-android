package org.commcare.activities;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.commcare.dalvik.R;
import org.commcare.models.ODKStorage;
import org.commcare.utils.FileUtil;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.widgets.ImageWidget;
import org.commcare.views.widgets.SignatureWidget;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Modified from the FingerPaint example found in The Android Open Source
 * Project.
 *
 * @author BehrAtherton@gmail.com
 */
public class DrawActivity extends Activity {
    private static final String t = "DrawActivity";

    public static final String OPTION = "option";
    public static final String OPTION_SIGNATURE = "signature";
    private static final String OPTION_ANNOTATE = "annotate";
    private static final String OPTION_DRAW = "draw";
    public static final String REF_IMAGE = "refImage";
    public static final String EXTRA_OUTPUT = android.provider.MediaStore.EXTRA_OUTPUT;
    private static final String SAVEPOINT_IMAGE = "savepointImage"; // during
    // restore

    // incoming options...
    private String loadOption = null;
    private File refImage = null;
    private File output = null;
    private File savepointImage = null;

    private DrawView drawView;
    private String alertTitleString;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            saveFile(savepointImage);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (savepointImage.exists()) {
            outState.putString(SAVEPOINT_IMAGE, savepointImage.getAbsolutePath());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Bundle extras = getIntent().getExtras();

        if (extras == null) {
            loadOption = OPTION_DRAW;
            refImage = null;
            savepointImage = SignatureWidget.getTempFileForDrawingCapture();
            savepointImage.delete();
            output = ImageWidget.getTempFileForImageCapture();
        } else {
            loadOption = extras.getString(OPTION);
            if (loadOption == null) {
                loadOption = OPTION_DRAW;
            }
            // refImage can also be present if resuming a drawing
            Uri uri = (Uri)extras.get(REF_IMAGE);
            if (uri != null) {
                refImage = new File(uri.getPath());
            }
            String savepoint = extras.getString(SAVEPOINT_IMAGE);
            if (savepoint != null) {
                savepointImage = new File(savepoint);
                if (!savepointImage.exists() && refImage != null
                        && refImage.exists()) {
                    try {
                        FileUtil.copyFile(refImage, savepointImage);
                    }catch (IOException e) {
                        Log.e(t, "IOExeception copying drawn image.");
                        e.printStackTrace();
                    }
                }
            } else {
                savepointImage = SignatureWidget.getTempFileForDrawingCapture();
                savepointImage.delete();
                if (refImage != null && refImage.exists()) {
                    try {
                        FileUtil.copyFile(refImage, savepointImage);
                    } catch (IOException e) {
                        Log.e(t, "IOExeception copying drawn image");
                        e.printStackTrace();
                    }
                }
            }
            //sets where the result will be saved to
            uri = (Uri)extras.get(EXTRA_OUTPUT);
            if (uri != null) {
                output = new File(uri.getPath());
            } else {
                output = ImageWidget.getTempFileForImageCapture();
            }
        }

        // At this point, we have:
        // loadOption -- type of activity (draw, signature, annotate)
        // refImage -- original image to work with
        // savepointImage -- drawing to use as a starting point (may be copy of
        // original)
        // output -- where the output should be written

        if (OPTION_SIGNATURE.equals(loadOption)) {
            // set landscape
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            alertTitleString = getString(R.string.quit_application,
                    getString(R.string.sign_button));
        } else if (OPTION_ANNOTATE.equals(loadOption)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            alertTitleString = getString(R.string.quit_application,
                    getString(R.string.markup_image));
        } else {
            alertTitleString = getString(R.string.quit_application,
                    getString(R.string.draw_image));
        }

        setTitle(getString(R.string.application_name) + " > "
                + getString(R.string.draw_image));

        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        RelativeLayout v = (RelativeLayout)inflater.inflate(
                R.layout.draw_layout, null);
        LinearLayout ll = v.findViewById(R.id.drawViewLayout);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(10);
        paint.setColor(Color.BLACK);

        Paint pointPaint = new Paint();
        pointPaint.setAntiAlias(true);
        pointPaint.setDither(true);
        pointPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        pointPaint.setStrokeWidth(10);
        pointPaint.setColor(Color.BLACK);

        drawView = new DrawView(this, OPTION_SIGNATURE.equals(loadOption),
                savepointImage, paint, pointPaint);

        ll.addView(drawView);

        setContentView(v);

        Button btnFinished = findViewById(R.id.btnFinishDraw);
        btnFinished.setText(StringUtils.getStringRobust(this, R.string.save_and_close));
        btnFinished.setOnClickListener(v13 -> saveAndClose());

        Button btnReset = findViewById(R.id.btnResetDraw);
        btnReset.setOnClickListener(v12 -> reset());
        btnReset.setText(StringUtils.getStringRobust(this, R.string.reset_image));

        Button btnCancel = findViewById(R.id.btnCancelDraw);
        btnCancel.setOnClickListener(v1 -> cancelAndClose());
        btnCancel.setText(StringUtils.getStringRobust(this, R.string.cancel));
    }

    private void saveAndClose() {
        try {
            saveFile(output);
            setResult(Activity.RESULT_OK);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            setResult(Activity.RESULT_CANCELED);
        }
        this.finish();
    }

    private void saveFile(File f) throws FileNotFoundException {
        if (drawView.getWidth() == 0 || drawView.getHeight() == 0) {
            // apparently on 4.x, the orientation change notification can occur
            // sometime before the view is rendered. In that case, the view
            // dimensions will not be known.
            Log.e(t, "view has zero width or zero height");
        } else {
            FileOutputStream fos;
            fos = new FileOutputStream(f);
            Bitmap bitmap = Bitmap.createBitmap(drawView.getWidth(),
                    drawView.getHeight(), Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            drawView.draw(canvas);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos);
            try {
                fos.flush();
                fos.close();
            } catch (Exception e) {
            }
        }
    }

    private void reset() {
        savepointImage.delete();
        if (!OPTION_SIGNATURE.equals(loadOption) && refImage != null
                && refImage.exists()) {
            try {
                FileUtil.copyFile(refImage, savepointImage);
            }catch (IOException e) {
                Log.e(t, "IOExeception while video audio");
                e.printStackTrace();
            }
        }
        drawView.reset();
        drawView.invalidate();
    }

    private void cancelAndClose() {
        setResult(Activity.RESULT_CANCELED);
        this.finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:

                createQuitDrawDialog();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.isAltPressed()) {

                    createQuitDrawDialog();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.isAltPressed()) {

                    createQuitDrawDialog();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Create a dialog with options to save and exit, save, or quit without
     * saving
     */
    private void createQuitDrawDialog() {
        final PaneledChoiceDialog dialog = new PaneledChoiceDialog(this, alertTitleString);

        View.OnClickListener keepChangesListener = v -> saveAndClose();
        DialogChoiceItem keepOption = new DialogChoiceItem(getString(R.string.keep_changes), -1,
                keepChangesListener);

        View.OnClickListener discardChangesListener = v -> cancelAndClose();
        DialogChoiceItem discardOption = new DialogChoiceItem(getString(R.string.do_not_save), -1,
                discardChangesListener);

        dialog.setChoiceItems(new DialogChoiceItem[]{keepOption, discardOption});

        dialog.addButton(getString(R.string.cancel), v -> dialog.dismiss());

        dialog.showNonPersistentDialog();
    }

    public static class DrawView extends View {
        private boolean isSignature;
        private Bitmap mBitmap;
        private Canvas mCanvas;
        private final Path mCurrentPath;
        private final Paint mBitmapPaint;
        private File mBackgroundBitmapFile;
        private final Paint paint;
        private final Paint pointPaint;
        private float mX, mY;

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
}

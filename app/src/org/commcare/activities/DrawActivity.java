package org.commcare.activities;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.commcare.dalvik.R;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.commcare.utils.StringUtils;
import org.commcare.views.DrawView;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.widgets.ImageWidget;
import org.commcare.views.widgets.SignatureWidget;
import org.javarosa.core.services.Logger;

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
public class DrawActivity extends AppCompatActivity implements DrawView.Callback {
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
    private Button saveAndCloseButton;

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
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);

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

        saveAndCloseButton = findViewById(R.id.btnFinishDraw);
        saveAndCloseButton.setText(StringUtils.getStringRobust(this, R.string.save_and_close));
        saveAndCloseButton.setOnClickListener(v13 -> saveAndClose());
        if (refImage != null && refImage.exists()) {
            // Means we're editing a saved signature
            saveAndCloseButton.setEnabled(true);
        }

        Button btnReset = findViewById(R.id.btnResetDraw);
        btnReset.setOnClickListener(v12 -> reset());
        btnReset.setText(StringUtils.getStringRobust(this, R.string.reset_image));

        Button btnCancel = findViewById(R.id.btnCancelDraw);
        btnCancel.setOnClickListener(v1 -> cancelAndClose());
        btnCancel.setText(StringUtils.getStringRobust(this, R.string.cancel));
    }

    @Override
    protected void onStart() {
        super.onStart();
        drawView.setCallback(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        drawView.removeCallback();
    }

    private void saveAndClose() {
        try {
            Logger.log(LogTypes.SOFT_ASSERT, "Attempting to save signature");
            saveFile(output);
            setResult(AppCompatActivity.RESULT_OK);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            // We shouldn't have gotten a file not found exception since we're using ImageWidget.getTempFileForImageCapture.
            Logger.log(LogTypes.TYPE_ERROR_DESIGN, "Couldn't save signature at " + output.toString() + " because of" + e.getMessage());
            setResult(AppCompatActivity.RESULT_CANCELED);
        }
        this.finish();
    }

    private void saveFile(File f) throws FileNotFoundException {
        if (drawView.getWidth() == 0 || drawView.getHeight() == 0) {
            // apparently on 4.x, the orientation change notification can occur
            // sometime before the view is rendered. In that case, the view
            // dimensions will not be known.
            Logger.log(LogTypes.TYPE_ERROR_ASSERTION, "Found 0 width or height of view while saving signature");
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
        saveAndCloseButton.setEnabled(false);
    }

    private void cancelAndClose() {
        setResult(AppCompatActivity.RESULT_CANCELED);
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

        View.OnClickListener discardChangesListener = v -> cancelAndClose();
        DialogChoiceItem discardOption = new DialogChoiceItem(getString(R.string.do_not_save), -1,
                discardChangesListener);

        if (saveAndCloseButton.isEnabled()) {
            View.OnClickListener keepChangesListener = v -> saveAndClose();
            DialogChoiceItem keepOption = new DialogChoiceItem(getString(R.string.keep_changes), -1,
                    keepChangesListener);
            dialog.setChoiceItems(new DialogChoiceItem[]{keepOption, discardOption});
        } else {
            dialog.setChoiceItems(new DialogChoiceItem[]{discardOption});
        }

        dialog.addButton(getString(R.string.cancel), v -> dialog.dismiss());

        dialog.showNonPersistentDialog();
    }

    @Override
    public void drawn() {
        saveAndCloseButton.setEnabled(true);
    }
}

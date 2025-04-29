package org.commcare.views.widgets;


import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.components.FormEntryConstants;
import org.commcare.activities.components.FormEntryInstanceState;
import org.commcare.activities.components.ImageCaptureProcessing;
import org.commcare.dalvik.R;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.Permissions;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.javarosa.core.model.QuestionDataExtension;
import org.javarosa.core.model.UploadQuestionExtension;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;

import javax.crypto.spec.SecretKeySpec;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Widget that allows user to take pictures, sounds or video and add them to the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class ImageWidget extends QuestionWidget {

    public static final int REQUEST_CAMERA_PERMISSION = 1001;

    public static final Object IMAGE_VIEW_TAG = "image_view_tag";

    private final Button mCaptureButton;
    private final Button mChooseButton;
    private final Button mDiscardButton;
    private ImageView mImageView;

    private String mBinaryName;

    private final String mInstanceFolder;

    private final TextView mErrorTextView;

    private int mMaxDimen;
    private final PendingCalloutInterface pendingCalloutInterface;

    public static File getTempFileForImageCapture() {
        return new File(CommCareApplication.instance().
                getExternalTempPath(GlobalConstants.TEMP_FILE_STEM_IMAGE_HOLDER));
    }

    public ImageWidget(final Context context, FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt);
        this.pendingCalloutInterface = pic;

        mMaxDimen = -1;
        mInstanceFolder = FormEntryInstanceState.getInstanceFolder();

        setOrientation(LinearLayout.VERTICAL);

        mErrorTextView = new TextView(context);
        mErrorTextView.setText("Selected file is not a valid image");

        // setup capture button
        mCaptureButton = new MaterialButton(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_image),
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(v -> {
            mErrorTextView.setVisibility(View.GONE);
            if (Permissions.missingAppPermission((AppCompatActivity)getContext(), Manifest.permission.CAMERA)) {
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                if (Permissions.shouldShowPermissionRationale((AppCompatActivity)getContext(), Manifest.permission.CAMERA)) {
                    CommCareAlertDialog dialog =
                            DialogCreationHelpers.buildPermissionRequestDialog((AppCompatActivity)getContext(), (RuntimePermissionRequester)getContext(),
                                    REQUEST_CAMERA_PERMISSION,
                                    Localization.get("permission.camera.title"),
                                    Localization.get("permission.camera.message"));
                    dialog.showNonPersistentDialog();
                } else {
                    ((RuntimePermissionRequester)getContext()).requestNeededPermissions(REQUEST_CAMERA_PERMISSION);
                }
            } else {
                takePicture();
            }
        });

        // setup chooser button
        mChooseButton = new MaterialButton(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_image),
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mChooseButton.setOnClickListener(v -> {
            if (ImageCaptureProcessing.getCustomImagePath() != null) {
                // This block is only in use for a Calabash test and
                // processes the custom file path set from a broadcast triggered by calabash test
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                ImageCaptureProcessing.processImageFromBroadcast((FormEntryActivity)getContext(), FormEntryInstanceState.getInstanceFolder());
                ImageCaptureProcessing.setCustomImagePath(null);
            } else {
                mErrorTextView.setVisibility(View.GONE);

                try {
                    ((AppCompatActivity)getContext())
                            .startActivityForResult(WidgetUtils.createPickMediaIntent (getContext(), "image/*"),
                                    FormEntryConstants.IMAGE_CHOOSER);
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "choose image"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // setup discard button
        mDiscardButton = new MaterialButton(getContext());
        WidgetUtils.setupButton(mDiscardButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.discard_image),
                !mPrompt.isReadOnly());
        mDiscardButton.setOnClickListener(v -> {
            deleteMedia();
            widgetEntryChanged();
        });
        mDiscardButton.setVisibility(View.GONE);

        // finish complex layout
        //
        addView(mCaptureButton);
        addView(mChooseButton);
        addView(mDiscardButton);

        String acq = mPrompt.getAppearanceHint();
        if (QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(acq)) {
            mChooseButton.setVisibility(View.GONE);
        }
        addView(mErrorTextView);
        mErrorTextView.setVisibility(View.GONE);

        // retrieve answer from data model and update ui
        mBinaryName = mPrompt.getAnswerText();

        // Only add the imageView if the user has taken a picture
        if (mBinaryName != null) {
            mImageView = new ImageView(getContext());
            //to identify the view in tests
            mImageView.setTag(IMAGE_VIEW_TAG);
            Display display =
                    ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE))
                            .getDefaultDisplay();
            int screenWidth = display.getWidth();
            int screenHeight = display.getHeight();

            File imageBeingSubmitted = new File(mInstanceFolder + "/" + mBinaryName);
            File encryptedFile = new File(imageBeingSubmitted.getAbsolutePath() + MediaWidget.AES_EXTENSION);

            if (imageBeingSubmitted.exists()) {
                checkFileSize(imageBeingSubmitted);
            } else if (encryptedFile.exists()) {
                checkFileSize(encryptedFile);
            }

            File toDisplay = getFileToDisplay(mInstanceFolder, mBinaryName,
                    ((FormEntryActivity)getContext()).getSymetricKey());

            if (toDisplay.exists()) {
                Bitmap bmp = MediaUtil.getBitmapScaledToContainer(toDisplay,
                        screenHeight, screenWidth);
                if (bmp == null) {
                    mErrorTextView.setVisibility(View.VISIBLE);
                }
                mImageView.setImageBitmap(bmp);
            } else {
                mImageView.setImageBitmap(null);
            }

            mImageView.setPadding(10, 10, 10, 10);
            mImageView.setAdjustViewBounds(true);

            mImageView.setOnClickListener(v ->
                    MediaWidget.playMedia(getContext(), "image/*", toDisplay.getAbsolutePath()));

            addView(mImageView);
            mDiscardButton.setVisibility(View.VISIBLE);
        }
    }

    // If there is an image in the raw folder, use that as the display image, since it is better quality
    // otherwise checks if the file to be uploaded exists and decrypt if needed
    public static File getFileToDisplay(String instanceFolder, String binaryName, SecretKeySpec secretKey) {
        File imageBeingSubmitted = new File(instanceFolder + "/" + binaryName);
        File toDisplay = new File(ImageCaptureProcessing.getRawDirectoryPath(instanceFolder) + "/" + binaryName);
        if (!toDisplay.exists()) {
            if (imageBeingSubmitted.exists()) {
                toDisplay = imageBeingSubmitted;
            } else {
                File encryptedFile = new File(imageBeingSubmitted.getAbsolutePath() + MediaWidget.AES_EXTENSION);
                if (encryptedFile.exists()) {
                    // we need to decrypt the file and store it in a temp path to display
                    String mTempPath = MediaWidget.decryptMedia(encryptedFile, secretKey);
                    toDisplay = new File(mTempPath);
                }
            }
        }
        return toDisplay;
    }

    private void takePicture() {
        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        Uri uri = FileUtil.getUriForExternalFile(getContext(), getTempFileForImageCapture());

        // We give the camera an absolute filename/path where to put the
        // picture because of bug:
        // http://code.google.com/p/android/issues/detail?id=1480
        // The bug appears to be fixed in Android 2.0+, but as of feb 2,
        // 2010, G1 phones only run 1.6. Without specifying the path the
        // images returned by the camera in 1.6 (and earlier) are ~1/4
        // the size. boo.
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
        i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            ((AppCompatActivity)getContext()).startActivityForResult(i,
                    FormEntryConstants.IMAGE_CAPTURE);
            pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    StringUtils.getStringSpannableRobust(getContext(),
                            R.string.activity_not_found, "image capture"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteMedia() {
        MediaWidget.deleteMediaFiles(mInstanceFolder, mBinaryName);
        // clean up variables
        mBinaryName = null;
        removeView(mImageView);
        mDiscardButton.setVisibility(View.GONE);
    }

    @Override
    public void clearAnswer() {
        // remove the file
        deleteMedia();
        mImageView.setImageBitmap(null);
        mErrorTextView.setVisibility(View.GONE);

        // reset buttons
        mCaptureButton.setText(StringUtils.getStringSpannableRobust(getContext(), R.string.capture_image));
    }

    @Override
    public IAnswerData getAnswer() {
        if (mBinaryName != null) {
            return new StringData(mBinaryName);
        } else {
            return null;
        }
    }

    @Override
    public void setBinaryData(Object binaryPath) {
        // you are replacing an answer. delete the previous image using the
        // content provider.
        if (mBinaryName != null) {
            deleteMedia();
        }

        File f = new File(binaryPath.toString());
        mBinaryName = f.getName();
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mCaptureButton.setOnLongClickListener(l);
        mChooseButton.setOnLongClickListener(l);
        if (mImageView != null) {
            mImageView.setOnLongClickListener(l);
        }
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mCaptureButton.setOnLongClickListener(null);
        mChooseButton.setOnLongClickListener(null);
        if (mImageView != null) {
            mImageView.setOnLongClickListener(null);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mCaptureButton.cancelLongPress();
        mChooseButton.cancelLongPress();
        if (mImageView != null) {
            mImageView.cancelLongPress();
        }
    }

    @Override
    public void applyExtension(QuestionDataExtension extension) {
        if (extension instanceof UploadQuestionExtension) {
            this.mMaxDimen = ((UploadQuestionExtension)extension).getMaxDimen();
        }
    }

    @Override
    public void notifyPermission(String permission, boolean permissionGranted) {
        if (permission.contentEquals(Manifest.permission.CAMERA)) {
            if (permissionGranted) {
                takePicture();
            } else {
                Toast.makeText(getContext(),
                        Localization.get("permission.camera.denial.message"),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public int getMaxDimen() {
        return this.mMaxDimen;
    }
}

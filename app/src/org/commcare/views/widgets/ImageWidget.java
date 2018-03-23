package org.commcare.views.widgets;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import org.commcare.utils.UrlUtils;
import org.commcare.views.dialogs.CommCareAlertDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.javarosa.core.model.QuestionDataExtension;
import org.javarosa.core.model.UploadQuestionExtension;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;

/**
 * Widget that allows user to take pictures, sounds or video and add them to the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class ImageWidget extends QuestionWidget {

    public static final int REQUEST_CAMERA_PERMISSION = 1001;

    private final static String t = "MediaWidget";

    private final Button mCaptureButton;
    private final Button mChooseButton;
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
        mInstanceFolder =
                FormEntryInstanceState.mInstancePath.substring(0,
                        FormEntryInstanceState.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);

        mErrorTextView = new TextView(context);
        mErrorTextView.setText("Selected file is not a valid image");

        // setup capture button
        mCaptureButton = new Button(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_image),
                mAnswerFontSize,
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(v -> {
            mErrorTextView.setVisibility(View.GONE);
            if (Permissions.missingAppPermission((Activity)getContext(), Manifest.permission.CAMERA)) {
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                if (Permissions.shouldShowPermissionRationale((Activity)getContext(), Manifest.permission.CAMERA)) {
                    CommCareAlertDialog dialog =
                            DialogCreationHelpers.buildPermissionRequestDialog((Activity)getContext(), (RuntimePermissionRequester)getContext(),
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
        mChooseButton = new Button(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_image),
                mAnswerFontSize,
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ImageCaptureProcessing.getCustomImagePath() != null) {
                    // This block is only in use for a Calabash test and
                    // processes the custom file path set from a broadcast triggered by calabash test
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                    ImageCaptureProcessing.processImageFromBroadcast((FormEntryActivity)getContext(), FormEntryInstanceState.getInstanceFolder());
                    ImageCaptureProcessing.setCustomImagePath(null);
                } else {
                    mErrorTextView.setVisibility(View.GONE);
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");

                    try {
                        ((Activity)getContext()).startActivityForResult(i,
                                FormEntryConstants.IMAGE_CHOOSER);
                        pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(getContext(),
                                StringUtils.getStringSpannableRobust(getContext(),
                                        R.string.activity_not_found, "choose image"),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        // finish complex layout
        //
        addView(mCaptureButton);
        addView(mChooseButton);

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
            Display display =
                    ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE))
                            .getDefaultDisplay();
            int screenWidth = display.getWidth();
            int screenHeight = display.getHeight();

            File imageBeingSubmitted = new File(mInstanceFolder + "/" + mBinaryName);

            // If there is an image in the raw folder, use that as the display image, since it is
            // better quality
            File toDisplay = new File(mInstanceFolder + "/raw/" + mBinaryName);
            if (!toDisplay.exists()) {
                toDisplay = imageBeingSubmitted;
            }

            checkFileSize(imageBeingSubmitted);

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
            mImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent("android.intent.action.VIEW");
                    String[] projection = {
                            "_id"
                    };
                    Cursor c = null;
                    try {
                        c = getContext().getContentResolver().query(
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                projection, "_data='" + mInstanceFolder + mBinaryName + "'",
                                null, null);
                        if (c != null && c.getCount() > 0) {
                            c.moveToFirst();
                            String id = c.getString(c.getColumnIndex("_id"));

                            Log.i(t, "setting view path to: " +
                                    Uri.withAppendedPath(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));

                            i.setDataAndType(Uri.withAppendedPath(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                                    "image/*");
                            try {
                                getContext().startActivity(i);
                            } catch (ActivityNotFoundException e) {
                                Toast.makeText(getContext(),
                                        StringUtils.getStringSpannableRobust(getContext(),
                                                R.string.activity_not_found, "view image"),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                }
            });

            addView(mImageView);
        }
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
            ((Activity)getContext()).startActivityForResult(i,
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
        // get the file path and delete the file
        File f = new File(mInstanceFolder + "/" + mBinaryName);
        if (!f.delete()) {
            Log.e(t, "Failed to delete " + f);
        }
        // clean up variables
        mBinaryName = null;

        //TODO: possibly switch back to this implementation, but causes NullPointerException right now
        /*
        int del = MediaUtils.deleteImageFileFromMediaProvider(mInstanceFolder + File.separator + mBinaryName);
        Log.i(t, "Deleted " + del + " rows from media content provider");
        mBinaryName = null;*/
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
    public void setBinaryData(Object binaryuri) {
        // you are replacing an answer. delete the previous image using the
        // content provider.
        if (mBinaryName != null) {
            deleteMedia();
        }
        String binaryPath = UrlUtils.getPathFromUri((Uri)binaryuri, getContext());

        File f = new File(binaryPath);
        mBinaryName = f.getName();
        Log.i(t, "Setting current answer to " + f.getName());
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

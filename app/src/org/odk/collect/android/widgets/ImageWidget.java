package org.odk.collect.android.widgets;

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

import org.commcare.android.util.MediaUtil;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.QuestionDataExtension;
import org.javarosa.core.model.UploadQuestionExtension;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.application.ODKStorage;
import org.odk.collect.android.logic.PendingCalloutInterface;
import org.odk.collect.android.utilities.UrlUtils;

import java.io.File;

/**
 * Widget that allows user to take pictures, sounds or video and add them to the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class ImageWidget extends QuestionWidget {
    private final static String t = "MediaWidget";
    public final static File TEMP_FILE_FOR_IMAGE_CAPTURE = new File(ODKStorage.TMPFILE_PATH);

    private final Button mCaptureButton;
    private final Button mChooseButton;
    private ImageView mImageView;

    private String mBinaryName;

    private final String mInstanceFolder;

    private final TextView mErrorTextView;

    private int mMaxDimen;
    private final PendingCalloutInterface pendingCalloutInterface;

    public ImageWidget(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt);
        this.pendingCalloutInterface = pic;

        mMaxDimen = -1;
        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                        FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);

        mErrorTextView = new TextView(context);
        mErrorTextView.setText("Selected file is not a valid image");

        // setup capture button
        mCaptureButton = new Button(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_image),
                mAnswerFontsize,
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mErrorTextView.setVisibility(View.GONE);
                Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                // We give the camera an absolute filename/path where to put the
                // picture because of bug:
                // http://code.google.com/p/android/issues/detail?id=1480
                // The bug appears to be fixed in Android 2.0+, but as of feb 2,
                // 2010, G1 phones only run 1.6. Without specifying the path the
                // images returned by the camera in 1.6 (and earlier) are ~1/4
                // the size. boo.
                i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(TEMP_FILE_FOR_IMAGE_CAPTURE));
                try {
                    ((Activity)getContext()).startActivityForResult(i,
                            FormEntryActivity.IMAGE_CAPTURE);
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "image capture"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // setup chooser button
        mChooseButton = new Button(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_image),
                mAnswerFontsize,
                !mPrompt.isReadOnly());

        // launch capture intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mErrorTextView.setVisibility(View.GONE);
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");

                try {
                    ((Activity)getContext()).startActivityForResult(i,
                            FormEntryActivity.IMAGE_CHOOSER);
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "choose image"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // finish complex layout
        //
        addView(mCaptureButton);
        addView(mChooseButton);

        String acq = mPrompt.getAppearanceHint();
        if ((QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(acq))) {
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

            // Check if we have a raw folder, and if so pull the image to display from there
            File toDisplay = new File(mInstanceFolder + "/raw/" + mBinaryName);
            if (!toDisplay.exists()) {
                // Otherwise, just use the image in the instance folder
                toDisplay = new File(mInstanceFolder + "/" + mBinaryName);
            }

            checkFileSize(toDisplay);

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

    public int getMaxDimen() {
        return this.mMaxDimen;
    }
}

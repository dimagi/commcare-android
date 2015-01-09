package org.odk.collect.android.widgets;

/*
 * Copyright (C) 2012 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */


import java.io.File;

import org.commcare.dalvik.R;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.DrawActivity;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.UrlUtils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Signature widget.
 * 
 * @author BehrAtherton@gmail.com
 *
 */
public class SignatureWidget extends QuestionWidget implements IBinaryWidget {
    private final static String t = "SignatureWidget";

    private Button mSignButton;
    private String mBinaryName;
    private String mInstanceFolder;
    private ImageView mImageView;
    private boolean mWaitingForData;

    private TextView mErrorTextView;

    public SignatureWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                    FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);

        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);

        mErrorTextView = new TextView(context);
        mErrorTextView.setText("Selected file is not a valid image");

        // setup Blank Image Button
        mSignButton = new Button(getContext());
        mSignButton.setText(getContext().getString(R.string.sign_button));
        mSignButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mSignButton.setPadding(20, 20, 20, 20);
        mSignButton.setEnabled(!prompt.isReadOnly());
        mSignButton.setLayoutParams(params);
        // launch capture intent on click
        mSignButton.setOnClickListener(new View.OnClickListener() {
            /*
             * (non-Javadoc)
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {
                launchSignatureActivity();
            }
        });


        // finish complex layout
        addView(mSignButton);
        addView(mErrorTextView);

        // and hide the sign button if read-only
        if ( prompt.isReadOnly() ) {
            mSignButton.setVisibility(View.GONE);
        }
        mErrorTextView.setVisibility(View.GONE);

        // retrieve answer from data model and update ui
        mBinaryName = prompt.getAnswerText();

        // Only add the imageView if the user has signed
        if (mBinaryName != null) {
            mImageView = new ImageView(getContext());
            Display display =
                    ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            int screenWidth = display.getWidth();
            int screenHeight = display.getHeight();

            File f = new File(mInstanceFolder + File.separator + mBinaryName);

            if (f.exists()) {
                Bitmap bmp = FileUtils.getBitmapScaledToDisplay(f, screenHeight, screenWidth);
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
                /*
                 * (non-Javadoc)
                 * @see android.view.View.OnClickListener#onClick(android.view.View)
                 */
                @Override
                public void onClick(View v) {
    
                    launchSignatureActivity();
                }
            });

            addView(mImageView);
        }

    }

    private void launchSignatureActivity() {
        mErrorTextView.setVisibility(View.GONE);
        Intent i = new Intent(getContext(), DrawActivity.class);
        i.putExtra(DrawActivity.OPTION, DrawActivity.OPTION_SIGNATURE);
        // copy...
        //mBinaryName would be a preexisting signature that is getting displayed when the activity starts
        if ( mBinaryName != null ) {
            File f = new File(mInstanceFolder + File.separator + mBinaryName);
            i.putExtra(DrawActivity.REF_IMAGE, Uri.fromFile(f));
        }
        //path to output the signature file to
        i.putExtra(DrawActivity.EXTRA_OUTPUT, 
                Uri.fromFile(new File(Collect.TMPFILE_PATH)));

        try {
            //tells the form controller that when onActivityResult is called (when the DrawActivity)
            //finishes, the requestCode is SIGNATURE_CAPTURE
            ((Activity)getContext()).startActivityForResult(i, FormEntryActivity.SIGNATURE_CAPTURE);
            setWaitingForBinaryData();
        }
        catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    getContext().getString(R.string.activity_not_found, "signature capture"),
                    Toast.LENGTH_SHORT).show();
            cancelWaitingForBinaryData();
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
        
        //TODO: Possibly switch back to this implementation, but causes NullPointerException right now 
        /*int del = MediaUtils.deleteImageFileFromMediaProvider(mInstanceFolder + File.separator + mBinaryName);
        Log.i(t, "Deleted " + del + " rows from media content provider");
        mBinaryName = null;*/
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#clearAnswer()
     */
    @Override
    public void clearAnswer() {
        // remove the file
        deleteMedia();
        mImageView.setImageBitmap(null);
        mErrorTextView.setVisibility(View.GONE);

        // reset buttons
        mSignButton.setText(getContext().getString(R.string.sign_button));
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#getAnswer()
     */
    @Override
    public IAnswerData getAnswer() {
        if (mBinaryName != null) {
            return new StringData(mBinaryName.toString());
        } else {
            return null;
        }
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.IBinaryWidget#setBinaryData(java.lang.Object)
     */
    @Override
    public void setBinaryData(Object answer) {
        // you are replacing an answer. delete the previous image using the
        // content provider.
        if (mBinaryName != null) {
            deleteMedia();
        }

        String binaryPath = UrlUtils.getPathFromUri((Uri) answer,getContext());
        File newImage = new File(binaryPath);

        if (newImage.exists()) {
            // Add the new image to the Media content provider so that the
            // viewing is fast in Android 2.0+
            ContentValues values = new ContentValues(6);
            values.put(Images.Media.TITLE, newImage.getName());
            values.put(Images.Media.DISPLAY_NAME, newImage.getName());
            values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(Images.Media.MIME_TYPE, "image/jpeg");
            values.put(Images.Media.DATA, newImage.getAbsolutePath());

            Uri imageURI = getContext().getContentResolver().insert(
                    Images.Media.EXTERNAL_CONTENT_URI, values);
            Log.i(t, "Inserting image returned uri = " + imageURI.toString());

            mBinaryName = newImage.getName();
            Log.i(t, "Setting current answer to " + newImage.getName());
        } else {
            Log.e(t, "NO IMAGE EXISTS at: " + newImage.getAbsolutePath());
        }
        setWaitingForBinaryData();
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#setFocus(android.content.Context)
     */
    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.IBinaryWidget#isWaitingForBinaryData()
     */
    @Override
    public boolean isWaitingForBinaryData() {
        return mWaitingForData;
    }

    public void setWaitingForBinaryData() {
        mWaitingForData = true;
    }
    
    public void cancelWaitingForBinaryData() {
        mWaitingForData = false;
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#setOnLongClickListener(android.view.View.OnLongClickListener)
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mSignButton.setOnLongClickListener(l);
        if (mImageView != null) {
            mImageView.setOnLongClickListener(l);
        }
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#cancelLongPress()
     */
    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mSignButton.cancelLongPress();
        if (mImageView != null) {
            mImageView.cancelLongPress();
        }
    }

}

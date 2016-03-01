package org.commcare.views.widgets;

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


import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
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

import org.commcare.activities.DrawActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.models.ODKStorage;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.StringUtils;
import org.commcare.utils.UrlUtils;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;

/**
 * Signature widget.
 *
 * @author BehrAtherton@gmail.com
 */
public class SignatureWidget extends QuestionWidget {
    private final static String t = "SignatureWidget";

    private final Button mSignButton;
    private String mBinaryName;
    private final String mInstanceFolder;
    private ImageView mImageView;
    private final PendingCalloutInterface pendingCalloutInterface;

    private final TextView mErrorTextView;

    public SignatureWidget(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt);

        this.pendingCalloutInterface = pic;

        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                        FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);

        mErrorTextView = new TextView(context);
        mErrorTextView.setText("Selected file is not a valid image");

        // setup Blank Image Button
        mSignButton = new Button(getContext());
        WidgetUtils.setupButton(mSignButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.sign_button),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // launch capture intent on click
        final FormIndex questionIndex = prompt.getIndex();
        mSignButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchSignatureActivity(questionIndex);
            }
        });


        // finish complex layout
        addView(mSignButton);
        addView(mErrorTextView);

        // and hide the sign button if read-only
        if (prompt.isReadOnly()) {
            mSignButton.setVisibility(View.GONE);
        }
        mErrorTextView.setVisibility(View.GONE);

        // retrieve answer from data model and update ui
        mBinaryName = prompt.getAnswerText();

        // Only add the imageView if the user has signed
        if (mBinaryName != null) {
            mImageView = new ImageView(getContext());
            Display display =
                    ((WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE))
                            .getDefaultDisplay();
            int screenWidth = display.getWidth();
            int screenHeight = display.getHeight();

            File f = new File(mInstanceFolder + File.separator + mBinaryName);

            if (f.exists()) {
                Bitmap bmp = MediaUtil.getBitmapScaledToContainer(f, screenHeight, screenWidth);
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
                    launchSignatureActivity(questionIndex);
                }
            });

            addView(mImageView);
        }
    }

    private void launchSignatureActivity(FormIndex questionIndex) {
        mErrorTextView.setVisibility(View.GONE);
        Intent i = new Intent(getContext(), DrawActivity.class);
        i.putExtra(DrawActivity.OPTION, DrawActivity.OPTION_SIGNATURE);
        // copy...
        //mBinaryName would be a preexisting signature that is getting displayed when the activity starts
        if (mBinaryName != null) {
            File f = new File(mInstanceFolder + File.separator + mBinaryName);
            i.putExtra(DrawActivity.REF_IMAGE, Uri.fromFile(f));
        }
        //path to output the signature file to
        i.putExtra(DrawActivity.EXTRA_OUTPUT,
                Uri.fromFile(new File(ODKStorage.TMPFILE_PATH)));

        try {
            //tells the form controller that when onActivityResult is called (when the DrawActivity)
            //finishes, the requestCode is SIGNATURE_CAPTURE
            ((Activity)getContext()).startActivityForResult(i, FormEntryActivity.SIGNATURE_CAPTURE);
            pendingCalloutInterface.setPendingCalloutFormIndex(questionIndex);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    getContext().getString(R.string.activity_not_found, "signature capture"),
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

        //TODO: Possibly switch back to this implementation, but causes NullPointerException right now 
        /*int del = MediaUtils.deleteImageFileFromMediaProvider(mInstanceFolder + File.separator + mBinaryName);
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
        mSignButton.setText(getContext().getString(R.string.sign_button));
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
    public void setBinaryData(Object binaryURI) {
        // you are replacing an answer. delete the previous image using the
        // content provider.
        if (mBinaryName != null) {
            deleteMedia();
        }

        String binaryPath = UrlUtils.getPathFromUri((Uri)binaryURI, getContext());
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
        mSignButton.setOnLongClickListener(l);
        if (mImageView != null) {
            mImageView.setOnLongClickListener(l);
        }
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mSignButton.setOnLongClickListener(null);
        if (mImageView != null) {
            mImageView.setOnLongClickListener(null);
        }
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mSignButton.cancelLongPress();
        if (mImageView != null) {
            mImageView.cancelLongPress();
        }
    }

}

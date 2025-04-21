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


import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
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
import org.commcare.activities.DrawActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.components.FormEntryConstants;
import org.commcare.activities.components.FormEntryInstanceState;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.MediaUtil;
import org.commcare.utils.StringUtils;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;

import javax.annotation.Nullable;

import androidx.appcompat.app.AppCompatActivity;

import static org.commcare.views.widgets.ImageWidget.getFileToDisplay;

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

    public static File getTempFileForDrawingCapture() {
        return new File(CommCareApplication.instance().
                getExternalTempPath(GlobalConstants.TEMP_FILE_STEM_DRAW_HOLDER));
    }

    public SignatureWidget(Context context, FormEntryPrompt prompt, PendingCalloutInterface pic) {
        super(context, prompt);

        this.pendingCalloutInterface = pic;

        mInstanceFolder = FormEntryInstanceState.getInstanceFolder();

        setOrientation(LinearLayout.VERTICAL);

        mErrorTextView = new TextView(context);
        mErrorTextView.setText("Selected file is not a valid image");

        // setup Blank Image Button
        mSignButton = new MaterialButton(getContext());
        WidgetUtils.setupButton(mSignButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.sign_button),
                !prompt.isReadOnly());

        // launch capture intent on click
        final FormIndex questionIndex = prompt.getIndex();
        mSignButton.setOnClickListener(v -> launchSignatureActivity(questionIndex, null));

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

            File toDisplay = getFileToDisplay(mInstanceFolder, mBinaryName,
                    ((FormEntryActivity)getContext()).getSymetricKey());
            if (toDisplay.exists()) {
                Bitmap bmp = MediaUtil.getBitmapScaledToContainer(toDisplay, screenHeight, screenWidth);
                if (bmp == null) {
                    mErrorTextView.setVisibility(View.VISIBLE);
                }
                mImageView.setImageBitmap(bmp);
            } else {
                mImageView.setImageBitmap(null);
            }

            mImageView.setPadding(10, 10, 10, 10);
            mImageView.setAdjustViewBounds(true);
            mImageView.setOnClickListener(v -> launchSignatureActivity(questionIndex, toDisplay));
            addView(mImageView);

            mSignButton.setOnClickListener(v -> launchSignatureActivity(questionIndex, toDisplay));
        }
    }

    private void launchSignatureActivity(FormIndex questionIndex, @Nullable File toDisplay) {
        mErrorTextView.setVisibility(View.GONE);
        Intent i = new Intent(getContext(), DrawActivity.class);
        i.putExtra(DrawActivity.OPTION, DrawActivity.OPTION_SIGNATURE);

        // If a signature has already been captured for this question, will want to display it
        if (toDisplay != null) {
            i.putExtra(DrawActivity.REF_IMAGE, Uri.fromFile(toDisplay));
        }

        i.putExtra(DrawActivity.EXTRA_OUTPUT,
                Uri.fromFile(ImageWidget.getTempFileForImageCapture()));

        try {
            ((AppCompatActivity)getContext()).startActivityForResult(i, FormEntryConstants.SIGNATURE_CAPTURE);
            pendingCalloutInterface.setPendingCalloutFormIndex(questionIndex);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    getContext().getString(R.string.activity_not_found, "signature capture"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteMedia() {
        MediaWidget.deleteMediaFiles(mInstanceFolder, mBinaryName);
        // clean up variables
        mBinaryName = null;
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
    public void setBinaryData(Object binaryPath) {
        // you are replacing an answer. delete the previous image using the
        // content provider.
        if (mBinaryName != null) {
            deleteMedia();
        }

        File f = new File(binaryPath.toString());
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

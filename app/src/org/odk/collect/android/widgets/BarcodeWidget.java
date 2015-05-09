/*
 * Copyright (C) 2009 University of Washington
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

package org.odk.collect.android.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.Toast;

import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class BarcodeWidget extends QuestionWidget implements IBinaryWidget {
    private Button mGetBarcodeButton;
    private EditText mStringAnswer;
    private boolean mWaitingForData;


    public BarcodeWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);
        mWaitingForData = false;
        setOrientation(LinearLayout.VERTICAL);

        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);
        
        // set button formatting
        mGetBarcodeButton = new Button(getContext());
        mGetBarcodeButton.setText(StringUtils.getStringSpannableRobust(getContext(), R.string.get_barcode));
        mGetBarcodeButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mGetBarcodeButton.setPadding(20, 20, 20, 20);
        mGetBarcodeButton.setEnabled(!prompt.isReadOnly());
        mGetBarcodeButton.setLayoutParams(params);

        // launch barcode capture intent on click
        mGetBarcodeButton.setOnClickListener(new View.OnClickListener() {
        	/*
        	 * (non-Javadoc)
        	 * @see android.view.View.OnClickListener#onClick(android.view.View)
        	 */
            @Override
            public void onClick(View v) {
                Intent i = new Intent("com.google.zxing.client.android.SCAN");
                mWaitingForData = true;
                try {
                    ((Activity) getContext()).startActivityForResult(i,
                        FormEntryActivity.BARCODE_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(), R.string.barcode_scanner_error), Toast.LENGTH_SHORT)
                            .show();
                    mWaitingForData = false;
                }
            }
        });

        // set text formatting
        mStringAnswer = new EditText(getContext());
        mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mStringAnswer.setGravity(Gravity.CENTER);

        String s = prompt.getAnswerText();
        if (s != null) {
            mGetBarcodeButton.setText(StringUtils.getStringSpannableRobust(getContext(), R.string.replace_barcode));
            mStringAnswer.setText(s);
        }
        // finish complex layout
        addView(mGetBarcodeButton);
        addView(mStringAnswer);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#clearAnswer()
     */
    @Override
    public void clearAnswer() {
        mStringAnswer.setText(null);
        mGetBarcodeButton.setText(StringUtils.getStringSpannableRobust(getContext(), R.string.get_barcode));
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#getAnswer()
     */
    @Override
    public IAnswerData getAnswer() {
        String s = mStringAnswer.getText().toString();
        if (s == null || s.equals("")) {
            return null;
        } else {
            return new StringData(s);
        }
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.IBinaryWidget#setBinaryData(java.lang.Object)
     * Allows answer to be set externally in {@Link FormEntryActivity}.
     */
    @Override
    public void setBinaryData(Object answer) {
        mStringAnswer.setText((String) answer);
        mWaitingForData = false;
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


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#setOnLongClickListener(android.view.View.OnLongClickListener)
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mStringAnswer.setOnLongClickListener(l);
        mGetBarcodeButton.setOnLongClickListener(l);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#cancelLongPress()
     */
    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mGetBarcodeButton.cancelLongPress();
        mStringAnswer.cancelLongPress();
    }

}

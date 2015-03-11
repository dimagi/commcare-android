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

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class TriggerWidget extends QuestionWidget {

    private CheckBox mTriggerButton;
    private TextView mStringAnswer;
    private boolean mInteractive = true; 
    private static String mOK = "OK";

    private FormEntryPrompt mPrompt;


    public FormEntryPrompt getPrompt() {
        return mPrompt;
    }


    public TriggerWidget(Context context, FormEntryPrompt prompt, boolean interactive) {
        super(context, prompt);
        
        if(prompt.getAppearanceHint() != null && prompt.getAppearanceHint().startsWith("floating-")) {
            this.setVisibility(View.GONE);
        }
        
        mPrompt = prompt;
        mInteractive = interactive;
        
        int padding = (int)Math.floor(context.getResources().getDimension(R.dimen.select_padding));

        this.setOrientation(LinearLayout.VERTICAL);

        mTriggerButton = new CheckBox(getContext());
        mTriggerButton.setText(StringUtils.getStringRobust(getContext(), R.string.trigger));
                mTriggerButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        // mActionButton.setPadding(20, 20, 20, 20);
        mTriggerButton.setEnabled(!prompt.isReadOnly());
        
        mTriggerButton.setPadding(mTriggerButton.getPaddingLeft(), padding, mTriggerButton.getPaddingRight(), padding);

        mTriggerButton.setOnClickListener(new View.OnClickListener() {
        	/*
        	 * (non-Javadoc)
        	 * @see android.view.View.OnClickListener#onClick(android.view.View)
        	 */
            @Override
            public void onClick(View v) {
                if (mTriggerButton.isChecked()) {
                    mStringAnswer.setText(mOK);
                } else {
                    mStringAnswer.setText(null);
                }
                TriggerWidget.this.widgetEntryChanged();
            }
        });

        mStringAnswer = new TextView(getContext());
        mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mStringAnswer.setGravity(Gravity.CENTER);

        String s = prompt.getAnswerText();
        if (s != null) {
            if (s.equals(mOK)) {
                mTriggerButton.setChecked(true);
            } else {
                mTriggerButton.setChecked(false);
            }
            mStringAnswer.setText(s);

        }

        if(mInteractive) {
            // finish complex layout
            this.addView(mTriggerButton);
            // this.addView(mStringAnswer);
        }
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#clearAnswer()
     */
    @Override
    public void clearAnswer() {
        mStringAnswer.setText(null);
        mTriggerButton.setChecked(false);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#getAnswer()
     */
    @Override
    public IAnswerData getAnswer() {
        if(!mInteractive) {
            return new StringData(mOK);
        }
        String s = mStringAnswer.getText().toString();
        if (s == null || s.equals("")) {
            return null;
        } else {
            return new StringData(s);
        }
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
     * @see org.odk.collect.android.widgets.QuestionWidget#setOnLongClickListener(android.view.View.OnLongClickListener)
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mTriggerButton.setOnLongClickListener(l);
        mStringAnswer.setOnLongClickListener(l);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#cancelLongPress()
     */
    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mTriggerButton.cancelLongPress();
        mStringAnswer.cancelLongPress();
    }

}

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
import android.text.Spannable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.jr.extensions.IntentCallout;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class IntentWidget extends QuestionWidget implements IBinaryWidget {

    protected Button launchIntentButton;
    protected TextView mStringAnswer;
    protected boolean mWaitingForData;
    private Intent intent;
    protected IntentCallout ic;
    private int calloutId = FormEntryActivity.INTENT_CALLOUT;

    public IntentWidget(Context context, FormEntryPrompt prompt, Intent in, IntentCallout ic, int calloutId) {
        this(context, prompt, in, ic);
        this.calloutId = calloutId;
    }

    public IntentWidget(Context context, FormEntryPrompt prompt, Intent in,
                        IntentCallout ic) {
        super(context, prompt);

        this.intent = in;
        this.ic = ic;

        mWaitingForData = false;

        makeTextView(prompt);
        makeButton(prompt);

    }

    public void makeTextView(FormEntryPrompt prompt) {
        // set text formatting
        mStringAnswer = new TextView(getContext());
        mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mStringAnswer.setGravity(Gravity.CENTER);

        String s = prompt.getAnswerText();
        if (s != null) {
            mStringAnswer.setText(s);
        }

        // finish complex layout
        addView(mStringAnswer);


        //only auto advance if 1) we have no data 2) its quick 3) we weren't just cancelled
        if(s == null && "quick".equals(ic.getAppearance()) && !ic.getCancelled()){

            performCallout();
        } else if (ic.getCancelled()) {
            // reset the cancelled flag
            ic.setCancelled(false);
        }
    }

    public void makeButton(FormEntryPrompt prompt){
        setOrientation(LinearLayout.VERTICAL);

        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);

        launchIntentButton = new Button(getContext());

        String s = prompt.getAnswerText();
        Spannable label;
        if (s != null) {
            label = StringUtils.getStringSpannableRobust(getContext(), R.string.intent_callout_button_update);
        } else{
            label = StringUtils.getStringSpannableRobust(getContext(), R.string.intent_callout_button);
        }

        WidgetUtils.setupButton(launchIntentButton,
                label,
                mAnswerFontsize,
                !prompt.isReadOnly());

        // launch barcode capture intent on click
        launchIntentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performCallout();
            }
        });
        addView(launchIntentButton);
    }

    public void performCallout() {

        mWaitingForData = true;
        try {
            //Set Data
            String data = mStringAnswer.getText().toString();
            if (data != null && !"".equals(data)) {
                intent.putExtra(IntentCallout.INTENT_RESULT_VALUE, data);
            }
            
            ((Activity) getContext()).startActivityForResult(intent,
                calloutId);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    "Couldn't find intent for callout!", Toast.LENGTH_SHORT).show();
            mWaitingForData = false;
        }
    }

    private void setButtonLabel(){
        if(ic.getButtonLabel() != null){
            launchIntentButton.setText(ic.getButtonLabel());
        } else{
            launchIntentButton.setText(StringUtils.getStringSpannableRobust(getContext(),
                        R.string.intent_callout_button));
        }
    }


    @Override
    public void clearAnswer() {
        mStringAnswer.setText(null);
        setButtonLabel();
    }


    @Override
    public IAnswerData getAnswer() {
        String s = mStringAnswer.getText().toString();
        if (s == null || s.equals("")) {
            return null;
        } else {
            return new StringData(s);
        }
    }


    /**
     * Allows answer to be set externally in {@Link FormEntryActivity}.
     */
    @Override
    public void setBinaryData(Object answer) {
        mStringAnswer.setText((String) answer);
        mWaitingForData = false;
    }


    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
            (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }


    @Override
    public boolean isWaitingForBinaryData() {
        return mWaitingForData;
    }


    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mStringAnswer.setOnLongClickListener(l);
        launchIntentButton.setOnLongClickListener(l);
    }


    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        launchIntentButton.cancelLongPress();
        mStringAnswer.cancelLongPress();
    }
    
    public IntentCallout getIntentCallout() {
        //TODO: This is really not great, but the alternative
        //is doubling up all of this code in the ODKView, which
        //is silly. It's not generalizable
        return ic;
    }
}

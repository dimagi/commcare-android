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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.jr.extensions.IntentCallout;
import org.odk.collect.android.logic.PendingCalloutInterface;


/**
 * Widget that allows user to scan barcodes and add them to the form.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */

public class BarcodeWidget extends IntentWidget {

    private TextView mStringAnswer;

    public BarcodeWidget(Context context, FormEntryPrompt prompt, Intent i, IntentCallout ic,
                         PendingCalloutInterface pendingCalloutInterface) {
        // todo: I don't think pendingCalloutInterface is actually useful for BarcodeWidget
        // todo: it's only here because it subclasses IntentWidget
        super(context, prompt, i, ic, pendingCalloutInterface, FormEntryActivity.BARCODE_CAPTURE);

        setOrientation(LinearLayout.VERTICAL);
    }

    @Override
    public void makeButton() {
        setOrientation(LinearLayout.VERTICAL);
        launchIntentButton = new Button(getContext());
        WidgetUtils.setupButton(launchIntentButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.get_barcode),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // launch barcode capture intent on click
        launchIntentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("com.google.zxing.client.android.SCAN");
                try {
                    ((Activity)getContext()).startActivityForResult(i,
                            FormEntryActivity.BARCODE_CAPTURE);
                    pendingCalloutInterface.setPendingCalloutFormIndex(prompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.barcode_scanner_error),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        addView(launchIntentButton);
    }


    @Override
    public void clearAnswer() {
        mStringAnswer.setText(null);
        launchIntentButton.setText(StringUtils.getStringSpannableRobust(getContext(), R.string.get_barcode));
    }

    @Override
    public void makeTextView() {
        if ("editable".equals(ic.getAppearance())) {
            // set text formatting
            mStringAnswer = new EditText(getContext());
            mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
            mStringAnswer.setGravity(Gravity.CENTER);

            String s = prompt.getAnswerText();
            if (s != null) {
                mStringAnswer.setText(s);
            }
            // finish complex layout
            addView(mStringAnswer);
        } else {
            super.makeTextView();
        }
    }

}

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
import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;

import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.jr.extensions.IntentCallout;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class BarcodeWidget extends IntentWidget implements IBinaryWidget {


    public BarcodeWidget(Context context, FormEntryPrompt prompt, Intent i, IntentCallout ic) {
        super(context, prompt, i, ic, FormEntryActivity.BARCODE_CAPTURE);
    }

    @Override
    public void makeTextView(FormEntryPrompt prompt){
        if("editable".equals(ic.getAppearance())){
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
        } else{
            super.makeTextView(prompt);
        }
    }

}

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
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import org.javarosa.core.model.data.DecimalData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

import java.text.NumberFormat;

/**
 * A widget that restricts values to floating point numbers.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class DecimalWidget extends StringWidget {

    public DecimalWidget(Context context, FormEntryPrompt prompt, boolean secret) {
        super(context, prompt, secret);

        // formatting
        mAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mAnswer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);

        // needed to make long readonly text scroll
        mAnswer.setHorizontallyScrolling(false);
        if (!secret) {
            mAnswer.setSingleLine(false);
        }

        // only numbers are allowed
        mAnswer.setKeyListener(new DigitsKeyListener(true, true));

        // only 15 characters allowed
        InputFilter[] fa = new InputFilter[1];
        fa[0] = new InputFilter.LengthFilter(15);
        mAnswer.setFilters(fa);

        Double d = null;
        if (getCurrentAnswer() != null) {
            d = (Double)getCurrentAnswer().getValue();
        }

        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(15);
        nf.setMaximumIntegerDigits(15);
        nf.setGroupingUsed(false);
        if (d != null) {
            Double dAnswer = (Double)getCurrentAnswer().getValue();
            String dString = nf.format(dAnswer);
            d = Double.parseDouble(dString.replace(',', '.'));
            mAnswer.setText(d.toString());
        }

        // disable if read only
        if (prompt.isReadOnly()) {
            setBackgroundDrawable(null);
            setFocusable(false);
            setClickable(false);
        }
    }

    @Override
    protected void setTextInputType(EditText mAnswer) {
        if (secret) {
            mAnswer.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
            mAnswer.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }
    }

    @Override
    public IAnswerData getAnswer() {
        String s = mAnswer.getText().toString().trim();
        if (s == null || s.equals("")) {
            return null;
        } else {
            try {
                return new DecimalData(Double.valueOf(s).doubleValue());
            } catch (Exception NumberFormatException) {
                return null;
            }
        }
    }

    /**
     * If this is the last question, set the action button to close the keyboard
     */
    @Override
    public void setLastQuestion(boolean isLast) {
        if (isLast) {
            mAnswer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        } else {
            mAnswer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);
        }
    }

    @Override
    public void onClick(View v) {
        setFocus(getContext());
        // don't revert click behavior in this case since it might be customized.
    }

}

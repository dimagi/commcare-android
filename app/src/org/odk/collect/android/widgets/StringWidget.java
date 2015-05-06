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
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TableLayout;

import org.javarosa.core.model.condition.pivot.StringLengthRangeHint;
import org.javarosa.core.model.condition.pivot.UnpivotableExpressionException;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * The most basic widget that allows for entry of any text.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class StringWidget extends QuestionWidget implements OnClickListener, TextWatcher {

    boolean mReadOnly = false;
    protected EditText mAnswer;
    protected boolean secret = false;
    
    public StringWidget(Context context, FormEntryPrompt prompt, boolean secret) {
        super(context, prompt);
        mAnswer = new EditText(context);
        mAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mAnswer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        mAnswer.setOnClickListener(this);
        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);
        mAnswer.setLayoutParams(params);
        
        mAnswer.addTextChangedListener(this);
        
        //Let's see if we can figure out a constraint for this string
        try {
            addAnswerFilter(new InputFilter.LengthFilter(guessMaxStringLength(prompt)));
        } catch (UnpivotableExpressionException e) {
            //expected if there isn't a constraint that does this
        }
        
        this.secret = secret;
        
        if(!secret) {
            // capitalize the first letter of the sentence
            mAnswer.setKeyListener(new TextKeyListener(Capitalize.SENTENCES, false));
        }
        setTextInputType(mAnswer);

        // needed to make long read only text scroll
        mAnswer.setHorizontallyScrolling(false);
        if(!secret) {
            mAnswer.setSingleLine(false);
        }

        if (prompt != null) {
            mReadOnly = prompt.isReadOnly();
            IAnswerData value = prompt.getAnswerValue();
            if (value != null) {
                mAnswer.setText(value.getDisplayText());
            }

            if (mReadOnly) {
                if (value == null) {
                    mAnswer.setText("---");
                }
                mAnswer.setBackgroundDrawable(null);
                mAnswer.setFocusable(false);
                mAnswer.setClickable(false);
            }
        }

        addView(mAnswer);
    }
    
    /**
     * Guess the max string length based on the datatypes.
     * 
     * @param prompt
     * @return
     * @throws UnpivotableExpressionException
     */
    protected int guessMaxStringLength(FormEntryPrompt prompt) throws UnpivotableExpressionException{
        StringLengthRangeHint hint = new StringLengthRangeHint();
        prompt.requestConstraintHint(hint);
        if(hint.getMax() != null) {
            //We can!
            int length  = ((String)hint.getMax().getValue()).length();
            if(!hint.isMaxInclusive()) {
                length -= 1;
            }
            
            return length;
        }
        throw new UnpivotableExpressionException();
    }
    
    
    protected void addAnswerFilter(InputFilter filter) {
        //Let's add a filter
        InputFilter[] currentFilters = mAnswer.getFilters();
        InputFilter[] newFilters = new InputFilter[currentFilters.length + 1];
        System.arraycopy(currentFilters, 0, newFilters, 0, currentFilters.length);
        newFilters[currentFilters.length] = filter;
        
        mAnswer.setFilters(newFilters);
    }

    protected void setTextInputType(EditText mAnswer) {
        if(secret) {
            mAnswer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            mAnswer.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#clearAnswer()
     */
    @Override
    public void clearAnswer() {
        mAnswer.setText(null);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#getAnswer()
     */
    @Override
    public IAnswerData getAnswer() {
        String s = mAnswer.getText().toString().trim();
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
        
        // Put focus on text input field and display soft keyboard if appropriate.
        mAnswer.requestFocus();
        InputMethodManager inputManager =
            (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!mReadOnly) {
            inputManager.showSoftInput(mAnswer, 0);
            /*
             * If you do a multi-question screen after a "add another group" dialog, this won't
             * automatically pop up. It's an Android issue.
             * 
             * That is, if I have an edit text in an activity, and pop a dialog, and in that
             * dialog's button's OnClick() I call edittext.requestFocus() and
             * showSoftInput(edittext, 0), showSoftinput() returns false. However, if the edittext
             * is focused before the dialog pops up, everything works fine. great.
             */
        } else {
            inputManager.hideSoftInputFromWindow(mAnswer.getWindowToken(), 0);
        }
    }

    /*
     * (non-Javadoc)
     * @see android.view.View#onKeyDown(int, android.view.KeyEvent)
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.isAltPressed() == true) {
            return false;
        }
        widgetEntryChanged();
        return super.onKeyDown(keyCode, event);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#setOnLongClickListener(android.view.View.OnLongClickListener)
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mAnswer.setOnLongClickListener(l);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#cancelLongPress()
     */
    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mAnswer.cancelLongPress();
    }

    /*
     * (non-Javadoc)
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    @Override
    public void onClick(View v) {
        //revert to default editor behavior
        setFocus(getContext());
        mAnswer.setImeOptions(EditorInfo.IME_ACTION_UNSPECIFIED);
    }
    
    @Override
    public void acceptFocus() {
        mAnswer.performClick();
    }

    /*
     * (non-Javadoc)
     * @see android.text.TextWatcher#afterTextChanged(android.text.Editable)
     */
    @Override
    public void afterTextChanged(Editable s) {
        widgetEntryChanged();
    }

    /*
     * (non-Javadoc)
     * @see android.text.TextWatcher#beforeTextChanged(java.lang.CharSequence, int, int, int)
     */
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
            int after) {
        // TODO Auto-generated method stub
        
    }

    /*
     * (non-Javadoc)
     * @see android.text.TextWatcher#onTextChanged(java.lang.CharSequence, int, int, int)
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // TODO Auto-generated method stub
        
    }
    
    public void setLastQuestion(boolean isLast){
           // nothing changes for Strings
    }
}

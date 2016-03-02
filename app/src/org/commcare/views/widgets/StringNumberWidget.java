package org.commcare.views.widgets;

import android.content.Context;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Widget that restricts values to integers.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class StringNumberWidget extends StringWidget {

    public StringNumberWidget(Context context, FormEntryPrompt prompt, boolean secret) {
        super(context, prompt, secret);

        mAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mAnswer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NEXT);

        // needed to make long readonly text scroll
        mAnswer.setHorizontallyScrolling(false);
        if (!secret) {
            mAnswer.setSingleLine(false);
        }

        mAnswer.setKeyListener(new DigitsKeyListener(true, true) {
            @Override
            protected char[] getAcceptedChars() {
                return new char[]{
                        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '-', '+', ' '
                };
            }
        });

        if (prompt.isReadOnly()) {
            setBackgroundDrawable(null);
            setFocusable(false);
            setClickable(false);
        }

        //This might be redundant, but I assume that it's about there being a difference
        //between a display value somewhere. We should double check
        if (prompt.getAnswerValue() != null) {
            String curAnswer = getCurrentAnswer().getValue().toString().trim();
            try {
                mAnswer.setText(curAnswer);
            } catch (Exception NumberFormatException) {

            }
        }

    }

    @Override
    protected void setTextInputType(EditText mAnswer) {
        if (secret) {
            mAnswer.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED);
            mAnswer.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }
    }

    @Override
    public IAnswerData getAnswer() {
        String s = mAnswer.getText().toString();
        if (s == null || s.equals("")) {
            return null;
        } else {
            try {
                return new StringData(s);
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

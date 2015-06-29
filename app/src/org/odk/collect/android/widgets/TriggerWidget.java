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
 * Implements xform trigger tags, which are used to display messages on button
 * presses. This implementation is incomplete, acting like an output tag due to
 * a lack of message tag handling.
 *
 * @author wspride
 */
public class TriggerWidget extends QuestionWidget {
    private CheckBox mTriggerButton;
    /**
     * Stores the answer value of this question. Trigger elements shouldn't
     * have values, so this is contrary to the spec.
     */
    private TextView mStringAnswer;

    /**
     * Shows a checkbox when set.
     */
    private boolean mInteractive = true;

    /**
     * Value that this question is set to when in interactive mode and the
     * checkbox is clicked.
     */
    private static String mOK = "OK";

    public TriggerWidget(Context context, FormEntryPrompt prompt,
                         boolean interactive) {
        super(context, prompt);

        this.mInteractive = interactive;

        if (mPrompt.getAppearanceHint() != null &&
                mPrompt.getAppearanceHint().startsWith("floating-")) {
            this.setVisibility(View.GONE);
        }

        this.setOrientation(LinearLayout.VERTICAL);

        mTriggerButton = new CheckBox(getContext());
        WidgetUtils.setupButton(mTriggerButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.trigger),
                mAnswerFontsize,
                !mPrompt.isReadOnly());

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

        // TODO PLM: This is never shown, but rather used to store the value of
        // this question, which shouldn't be needed since trigger shouldn't
        // have values. Figure out if anyone actually uses interactive mode,
        // and if not, remove.
        mStringAnswer = new TextView(getContext());
        mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mStringAnswer.setGravity(Gravity.CENTER);

        String s = mPrompt.getAnswerText();
        if (s != null) {
            mTriggerButton.setChecked(s.equals(mOK));
            mStringAnswer.setText(s);
        }

        if (mInteractive) {
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
        if (!mInteractive) {
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
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
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

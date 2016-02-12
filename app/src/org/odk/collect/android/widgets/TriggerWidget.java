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
    private final CheckBox mTriggerButton;
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
    private static final String mOK = "OK";


    /**
     * @param context    Used to get font settings
     * @param prompt     Contains question data
     * @param appearance Hint from form builder, when set to:
     *                   - 'minimal' show text label
     *                   - 'selectable' show a selectable text label useful for
     *                   copy/pasting output
     *                   - otherwise display interactively, showing a checkbox
     *                   with text
     */
    public TriggerWidget(Context context, FormEntryPrompt prompt,
                         String appearance) {
        super(context, prompt);

        // enable interactive mode if 'appearance' is an unrecognized string
        mInteractive = !("minimal".equals(appearance) ||
                "selectable".equals(appearance));

        if ("selectable".equals(appearance)) {
            if (android.os.Build.VERSION.SDK_INT >= 11) {
                // Let users to copy form display outputs.
                mQuestionText.setTextIsSelectable(true);
            }
        }

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

    @Override
    public void clearAnswer() {
        mStringAnswer.setText(null);
        mTriggerButton.setChecked(false);
    }

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

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mTriggerButton.setOnLongClickListener(l);
        mStringAnswer.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mTriggerButton.setOnLongClickListener(null);
        mStringAnswer.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mTriggerButton.cancelLongPress();
        mStringAnswer.cancelLongPress();
    }

    @Override
    public void setAnswerFromPrompt() {
        mStringAnswer.setText((String)mPrompt.getAnswerValue().getValue());
    }
}

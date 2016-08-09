package org.commcare.views.widgets;

import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.TextView;

import org.commcare.android.javarosa.IntentCallout;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class BarcodeWidget extends IntentWidget {

    private TextView mStringAnswer;
    private final boolean isEditable;
    private boolean hasTextChanged;

    public BarcodeWidget(Context context, FormEntryPrompt prompt, Intent i, IntentCallout ic,
                         PendingCalloutInterface pendingCalloutInterface) {
        super(context, prompt, i, ic, pendingCalloutInterface,
                "intent.barcode.get", "intent.barcode.update", "barcode.reader.missing");

        isEditable = ic.getAppearance().contains("editable");
    }

    @Override
    public void setupTextView() {
        if (isEditable) {
            mStringAnswer = new EditText(getContext());
            mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
            mStringAnswer.setGravity(Gravity.CENTER);

            String s = mPrompt.getAnswerText();
            if (s != null) {
                mStringAnswer.setText(s);
            }
            addView(mStringAnswer);
        } else {
            super.setupTextView();
        }
    }

    @Override
    public IAnswerData getAnswer() {
        if (isEditable && hasTextChanged) {
            hasTextChanged = false;
            String s = mStringAnswer.getText().toString();
            if ("".equals(s)) {
                return null;
            } else {
                return new StringData(s);
            }
        } else {
            return mPrompt.getAnswerValue();
        }
    }

    @Override
    protected void loadCurrentAnswerToIntent() {
        // zero out super class implementation
    }
}

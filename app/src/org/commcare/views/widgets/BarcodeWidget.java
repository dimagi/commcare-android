package org.commcare.views.widgets;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;

import org.commcare.android.javarosa.IntentCallout;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class BarcodeWidget extends IntentWidget implements TextWatcher {

    private boolean hasTextChanged;

    public BarcodeWidget(Context context, FormEntryPrompt prompt, Intent i, IntentCallout ic,
                         PendingCalloutInterface pendingCalloutInterface) {
        super(context, prompt, i, ic, pendingCalloutInterface,
                "intent.barcode.get", "intent.barcode.update", "barcode.reader.missing");
    }

    @Override
    public void setupTextView() {
        if (isEditable) {
            mStringAnswer.addTextChangedListener(this);
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
            storeTextAnswerToForm();
        }

        return mPrompt.getAnswerValue();
    }

    private void storeTextAnswerToForm() {
        hasTextChanged = false;
        String textFieldAnswer = mStringAnswer.getText().toString();
        ic.processBarcodeResponse(mPrompt.getIndex().getReference(), textFieldAnswer);
    }

    @Override
    protected void loadCurrentAnswerToIntent() {
        // zero out super class implementation
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        hasTextChanged = true;
    }
}

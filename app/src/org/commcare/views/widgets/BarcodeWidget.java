package org.commcare.views.widgets;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;

import com.google.zxing.integration.android.IntentIntegrator;

import org.commcare.android.javarosa.IntentCallout;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 */
public class BarcodeWidget extends IntentWidget implements TextWatcher {

    private boolean hasTextChanged;

    public BarcodeWidget(Context context, FormEntryPrompt prompt, PendingCalloutInterface pendingCalloutInterface,
                         String appearance, FormDef formDef) {
        super(context, prompt, null, pendingCalloutInterface,
                "intent.barcode.get", "intent.barcode.update", "barcode.reader.missing",
                appearance != null && appearance.contains("editable"), appearance, formDef);
        // this has to be done after call to super in order to be able to access getContext()
        this.intent = new IntentIntegrator((AppCompatActivity)getContext()).createScanIntent();
    }

    @Override
    protected void performCallout() {
        if (this.intent == null) {
            this.intent = new IntentIntegrator((AppCompatActivity)getContext()).createScanIntent();
        }
        super.performCallout();
    }

    public void processBarcodeResponse(TreeReference intentQuestionRef, String scanResult) {
        IntentCallout.setNodeValue(this.formDef, intentQuestionRef, scanResult);
    }

    @Override
    public void setupTextView() {
        if (isEditable) {
            mStringAnswer.addTextChangedListener(this);
            mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontSize);
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
        processBarcodeResponse(mPrompt.getIndex().getReference(), textFieldAnswer);
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

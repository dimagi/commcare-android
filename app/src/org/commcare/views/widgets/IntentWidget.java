package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.FormEntryActivity;
import org.commcare.engine.extensions.IntentCallout;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class IntentWidget extends QuestionWidget {

    protected Button launchIntentButton;
    private TextView mStringAnswer;
    private final Intent intent;
    protected final IntentCallout ic;
    private int calloutId = FormEntryActivity.INTENT_CALLOUT;
    protected final PendingCalloutInterface pendingCalloutInterface;
    private final String getButtonLocalizationKey;
    private final String updateButtonLocalizationKey;

    public IntentWidget(Context context, FormEntryPrompt prompt, Intent in, IntentCallout ic,
                        PendingCalloutInterface pendingCalloutInterface, int calloutId) {
        this(context, prompt, in, ic, pendingCalloutInterface, "intent.barcode.get", "intent.barcode.update");

        this.calloutId = calloutId;
    }

    public IntentWidget(Context context, FormEntryPrompt prompt, Intent in, IntentCallout ic,
                        PendingCalloutInterface pendingCalloutInterface) {
        this(context, prompt, in, ic, pendingCalloutInterface, "intent.callout.get", "intent.callout.update");
    }

    public IntentWidget(Context context, FormEntryPrompt prompt, Intent in, IntentCallout ic,
                        PendingCalloutInterface pendingCalloutInterface,
                        String getButtonLocalizationKey, String updateButtonLocalizationKey) {
        super(context, prompt);

        this.intent = in;
        this.ic = ic;
        this.pendingCalloutInterface = pendingCalloutInterface;
        this.getButtonLocalizationKey = getButtonLocalizationKey;
        this.updateButtonLocalizationKey = updateButtonLocalizationKey;

        makeTextView();
        makeButton();
    }

    public void makeTextView() {
        // set text formatting
        mStringAnswer = new TextView(getContext());
        mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
        mStringAnswer.setGravity(Gravity.CENTER);

        String s = mPrompt.getAnswerText();
        if (s != null) {
            mStringAnswer.setText(s);
        }

        // finish complex layout
        addView(mStringAnswer);

        //only auto advance if 1) we have no data 2) its quick 3) we weren't just cancelled
        if (s == null && "quick".equals(ic.getAppearance()) && !ic.getCancelled()) {
            performCallout();
        } else if (ic.getCancelled()) {
            // reset the cancelled flag
            ic.setCancelled(false);
        }
    }

    protected Spannable getButtonLabel() {
        if (mStringAnswer.getText() == null || "".equals(mStringAnswer.getText().toString())) {
            if (ic.getButtonLabel() != null) {
                return new SpannableString(ic.getButtonLabel());
            } else {
                return new SpannableString(Localization.get(getButtonLocalizationKey));
            }
        } else {
            if (ic.getUpdateButtonLabel() != null) {
                return new SpannableString(ic.getUpdateButtonLabel());
            } else {
                return new SpannableString(Localization.get(updateButtonLocalizationKey));
            }
        }
    }

    public void makeButton() {
        setOrientation(LinearLayout.VERTICAL);

        launchIntentButton = new Button(getContext());

        WidgetUtils.setupButton(launchIntentButton,
                getButtonLabel(),
                mAnswerFontsize,
                !mPrompt.isReadOnly());

        // launch barcode capture intent on click
        launchIntentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performCallout();
            }
        });
        addView(launchIntentButton);
    }

    private void performCallout() {
        try {
            //Set Data
            String data = mStringAnswer.getText().toString();
            if (!"".equals(data)) {
                intent.putExtra(IntentCallout.INTENT_RESULT_VALUE, data);
            }
            intent.setType("text/plain");
            ((Activity)getContext()).startActivityForResult(intent, calloutId);
            pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    "Couldn't find intent for callout!", Toast.LENGTH_SHORT).show();
        }
    }

    private void setButtonLabel() {
        launchIntentButton.setText(getButtonLabel());
    }

    @Override
    public void clearAnswer() {
        mStringAnswer.setText(null);
        setButtonLabel();
    }

    @Override
    public IAnswerData getAnswer() {
        String s = mStringAnswer.getText().toString();
        if (s.equals("")) {
            return null;
        } else {
            return new StringData(s);
        }
    }

    @Override
    public void setBinaryData(Object answer) {
        mStringAnswer.setText((String)answer);
        setButtonLabel();
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
        mStringAnswer.setOnLongClickListener(l);
        launchIntentButton.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mStringAnswer.setOnLongClickListener(null);
        launchIntentButton.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        launchIntentButton.cancelLongPress();
        mStringAnswer.cancelLongPress();
    }

    public IntentCallout getIntentCallout() {
        //TODO: This is really not great, but the alternative
        //is doubling up all of this code in the ODKView, which
        //is silly. It's not generalizable
        return ic;
    }
}

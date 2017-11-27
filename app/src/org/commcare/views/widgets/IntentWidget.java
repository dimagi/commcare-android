package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.components.FormEntryConstants;
import org.commcare.android.javarosa.IntentCallout;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.utils.CompoundIntentList;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import javax.annotation.Nullable;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class IntentWidget extends QuestionWidget {

    protected final TextView mStringAnswer;
    protected Intent intent;
    private final String getButtonLocalizationKey;
    private final String updateButtonLocalizationKey;

    protected final Button launchIntentButton;
    protected final PendingCalloutInterface pendingCalloutInterface;

    @Nullable
    // This will be null for a BarcodeWidget because it uses a different callout mechanism
    protected final IntentCallout ic;

    protected final String missingCalloutKey;
    protected final boolean isEditable;
    private final String appearance;
    protected final FormDef formDef;

    public IntentWidget(Context context, FormEntryPrompt prompt,
                        Intent in, IntentCallout ic, PendingCalloutInterface pendingCalloutInterface) {
        this(context, prompt, in, ic, pendingCalloutInterface,
                "intent.callout.get", "intent.callout.update", "intent.callout.activity.missing",
                false, ic.getAppearance(), ic.getFormDef());
    }

    // Constructor for IntentWidgets not using an IntentCallout
    public IntentWidget(Context context, FormEntryPrompt prompt, Intent intent,
                        PendingCalloutInterface pendingCalloutInterface,
                        String getButtonLocalizationKey, String updateButtonLocalizationKey,
                        String missingCalloutKey, boolean isEditable, String appearance,
                        FormDef formDef) {
        this(context, prompt, intent ,null, pendingCalloutInterface, getButtonLocalizationKey,
                updateButtonLocalizationKey, missingCalloutKey, isEditable, appearance, formDef);
    }

    protected IntentWidget(Context context, FormEntryPrompt prompt, Intent in, IntentCallout ic,
                           PendingCalloutInterface pendingCalloutInterface,
                           String getButtonLocalizationKey, String updateButtonLocalizationKey,
                           String missingCalloutKey, boolean isEditable, String appearance,
                           FormDef formDef) {
        super(context, prompt);

        this.missingCalloutKey = missingCalloutKey;
        this.intent = in;
        this.ic = ic;
        this.appearance = appearance;
        this.pendingCalloutInterface = pendingCalloutInterface;
        this.getButtonLocalizationKey = getButtonLocalizationKey;
        this.updateButtonLocalizationKey = updateButtonLocalizationKey;
        this.isEditable = isEditable;
        this.formDef = formDef;

        if (isEditable) {
            mStringAnswer = new EditText(getContext());
        } else {
            mStringAnswer = new TextView(getContext());
        }
        launchIntentButton = new Button(getContext());
        setupTextView();
        setupButton();
    }

    protected void setupTextView() {
        mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontSize);
        mStringAnswer.setGravity(Gravity.CENTER);

        String s = mPrompt.getAnswerText();
        if (s != null) {
            mStringAnswer.setText(s);
        }

        addView(mStringAnswer);

        //only auto advance if 1) we have no data 2) its quick 3) we weren't just cancelled
        if (s == null
                && "quick".equals(appearance)
                && !pendingCalloutInterface.wasCalloutPendingAndCancelled(mPrompt.getIndex())) {
            performCallout();
        }
    }

    private void setupButton() {
        setOrientation(LinearLayout.VERTICAL);

        WidgetUtils.setupButton(launchIntentButton,
                getButtonLabel(),
                mAnswerFontSize,
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

    protected Spannable getButtonLabel() {
        if (mStringAnswer.getText() == null || "".equals(mStringAnswer.getText().toString())) {
            if (ic != null && ic.getButtonLabel() != null) {
                return new SpannableString(ic.getButtonLabel());
            } else {
                return new SpannableString(Localization.get(getButtonLocalizationKey));
            }
        } else {
            if (ic != null && ic.getUpdateButtonLabel() != null) {
                return new SpannableString(ic.getUpdateButtonLabel());
            } else {
                return new SpannableString(Localization.get(updateButtonLocalizationKey));
            }
        }
    }

    protected void performCallout() {
        if (calloutUnsupportedOnDevice()) {
            Toast.makeText(getContext(),
                    Localization.get("intent.callout.not.supported"), Toast.LENGTH_SHORT).show();
        } else {
            try {
                loadCurrentAnswerToIntent();
                ((Activity) getContext()).startActivityForResult(intent, FormEntryConstants.INTENT_CALLOUT);
                pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(),
                        Localization.get(missingCalloutKey), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public boolean calloutUnsupportedOnDevice() {
        return ic != null && ic.isSimprintsCallout() &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    protected void loadCurrentAnswerToIntent() {
        String data = mStringAnswer.getText().toString();
        if (!"".equals(data)) {
            intent.putExtra(IntentCallout.INTENT_RESULT_VALUE, data);
        }
    }

    private void setButtonLabel() {
        launchIntentButton.setText(getButtonLabel());
    }

    @Override
    public void clearAnswer() {
        IntentCallout.setNodeValue(formDef, mPrompt.getIndex().getReference(), null);
        mStringAnswer.setText(null);
        setButtonLabel();
    }

    @Override
    public IAnswerData getAnswer() {
        return mPrompt.getAnswerValue();
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

    public String getAppearance() {
        return appearance;
    }

    public CompoundIntentList addToCompoundIntent(CompoundIntentList compoundedCallout) {
        if (!intent.getBooleanExtra(IntentCallout.INTENT_EXTRA_CAN_AGGREGATE, false)) {
            return compoundedCallout;
        }
        if (compoundedCallout == null) {
            CompoundIntentList list = new CompoundIntentList(intent, this.getFormId().toString());
            list.setTitle(this.getButtonLabel().toString());
            return list;
        }
        if (!compoundedCallout.addIntentIfCompatible(intent, this.getFormId().toString())) {
            return null;
        } else {
            return compoundedCallout;
        }
    }

}

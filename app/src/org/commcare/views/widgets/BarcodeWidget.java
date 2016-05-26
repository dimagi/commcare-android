package org.commcare.views.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.activities.FormEntryActivity;
import org.commcare.android.javarosa.IntentCallout;
import org.commcare.logic.PendingCalloutInterface;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

/**
 * Widget that allows user to scan barcodes and add them to the form.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */

public class BarcodeWidget extends IntentWidget {

    private TextView mStringAnswer;

    public BarcodeWidget(Context context, FormEntryPrompt prompt, Intent i, IntentCallout ic,
                         PendingCalloutInterface pendingCalloutInterface) {
        // todo: I don't think pendingCalloutInterface is actually useful for BarcodeWidget
        // todo: it's only here because it subclasses IntentWidget
        super(context, prompt, i, ic, pendingCalloutInterface, FormEntryActivity.BARCODE_CAPTURE);

        setOrientation(LinearLayout.VERTICAL);
    }

    @Override
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
                Intent i = new Intent("com.google.zxing.client.android.SCAN");
                try {
                    ((Activity)getContext()).startActivityForResult(i,
                            FormEntryActivity.BARCODE_CAPTURE);
                    pendingCalloutInterface.setPendingCalloutFormIndex(mPrompt.getIndex());
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            Localization.get("barcode.reader.missing"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        addView(launchIntentButton);
    }

    @Override
    public void clearAnswer() {
        mStringAnswer.setText(null);
        launchIntentButton.setText(new SpannableString(Localization.get("intent.barcode.get")));
    }

    @Override
    public void makeTextView() {
        if ("editable".equals(ic.getAppearance())) {
            // set text formatting
            mStringAnswer = new EditText(getContext());
            mStringAnswer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mAnswerFontsize);
            mStringAnswer.setGravity(Gravity.CENTER);

            String s = mPrompt.getAnswerText();
            if (s != null) {
                mStringAnswer.setText(s);
            }
            // finish complex layout
            addView(mStringAnswer);
        } else {
            super.makeTextView();
        }
    }
}

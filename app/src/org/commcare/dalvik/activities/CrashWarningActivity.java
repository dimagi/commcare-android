package org.commcare.dalvik.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.android.util.CommCareExceptionHandler;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Shows reason for unrecoverable crash to user.
 */
public class CrashWarningActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_crash_warning);

        Button closeButton = (Button)findViewById(R.id.CloseWarning);
        closeButton.setText(Localization.get("crash.warning.button"));
        closeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_OK);
                finish();
            }
        });

        Intent intent = getIntent();
        setWarningText(intent);
        setHowToProceedText(intent);
    }

    private void setWarningText(Intent intent) {
        SpannableStringBuilder warningText =
                new SpannableStringBuilder(Localization.get("crash.warning.header") + "\n");
        int headerLength = warningText.length();
        warningText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                0, headerLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (intent.hasExtra(CommCareExceptionHandler.WARNING_MESSAGE_KEY)) {
            String warningMessage =
                    intent.getStringExtra(CommCareExceptionHandler.WARNING_MESSAGE_KEY);
            warningText.append(warningMessage);
            warningText.setSpan(new ForegroundColorSpan(Color.RED),
                    headerLength, warningText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        TextView warningDetailsView = (TextView)findViewById(R.id.WarningDetails);
        warningDetailsView.setText(warningText);
    }

    private void setHowToProceedText(Intent intent) {
        if (!intent.hasExtra(CommCareExceptionHandler.HOW_TO_FIX_MESSAGE_KEY)) {
            return;
        }

        SpannableStringBuilder proceedTextSpan =
                new SpannableStringBuilder(Localization.get("crash.proceed.header") + "\n");
        proceedTextSpan.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                0, proceedTextSpan.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        String proceedMessage =
                intent.getStringExtra(CommCareExceptionHandler.HOW_TO_FIX_MESSAGE_KEY);
        proceedTextSpan.append(proceedMessage);

        TextView howToProceedView = (TextView)findViewById(R.id.HowToProceedText);
        howToProceedView.setText(proceedTextSpan);
    }
}

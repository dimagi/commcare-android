package org.commcare.dalvik.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
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
 * Shows reason for unrecoverable crash to user and restarts CommCare
 */
public class CrashWarningActivity extends Activity {
    private int errorMessageVisibility = View.GONE;
    private static final String ERROR_VISIBLE = "error-message-is-visible";

    private TextView errorMessageView;
    private Button showErrorButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadSaveInstanceState(savedInstanceState);

        setContentView(R.layout.activity_crash_warning);

        setupUi();
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            errorMessageVisibility =
                    savedInstanceState.getInt(ERROR_VISIBLE, View.GONE);
        }
    }

    private void setupUi() {
        setupButtons();
        setupWarningText();
        setupErrorMessage();
    }

    private void setupButtons() {
        Button closeButton = (Button)findViewById(R.id.RestartCommCare);
        closeButton.setText(Localization.get("crash.warning.button"));
        closeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                restartCommCare();
            }
        });

        showErrorButton = (Button)findViewById(R.id.ShowError);
        showErrorButton.setText(Localization.get("crash.show.error.button"));
        showErrorButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleErrorMessageVisibility();
            }
        });
    }

    private void restartCommCare() {
        Intent intent = new Intent(CrashWarningActivity.this, CommCareHomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        }
        CrashWarningActivity.this.startActivity(intent);
        CrashWarningActivity.this.finish();
    }

    private void setupWarningText() {
        SpannableStringBuilder warningText =
                new SpannableStringBuilder(Localization.get("crash.warning.header") + "\n");
        warningText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                0, warningText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        TextView simpleWarningView = (TextView)findViewById(R.id.SimpleWarningMessage);
        simpleWarningView.setText(warningText);
    }

    private void setupErrorMessage() {
        Intent intent = getIntent();
         errorMessageView = (TextView)findViewById(R.id.ErrorMessage);

        if (intent.hasExtra(CommCareExceptionHandler.WARNING_MESSAGE_KEY)) {
            String warningMessage =
                    intent.getStringExtra(CommCareExceptionHandler.WARNING_MESSAGE_KEY);
            SpannableStringBuilder errorText =
                    new SpannableStringBuilder(warningMessage);
            errorText.setSpan(new ForegroundColorSpan(Color.RED),
                    0, errorText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            errorMessageView.setText(errorText);
        }
        errorMessageView.setVisibility(errorMessageVisibility);
        updateErrorButtonText();
    }

    private void toggleErrorMessageVisibility() {
        if (errorMessageView.getVisibility() == View.GONE) {
            errorMessageView.setVisibility(View.VISIBLE);
        } else {
            errorMessageView.setVisibility(View.GONE);
        }
        updateErrorButtonText();
    }

    private void updateErrorButtonText() {
        if (errorMessageView.getVisibility() == View.GONE) {
            showErrorButton.setText(Localization.get("crash.show.error.button"));
        } else {
            showErrorButton.setText(Localization.get("crash.hide.error.button"));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(ERROR_VISIBLE, errorMessageView.getVisibility());
    }
}

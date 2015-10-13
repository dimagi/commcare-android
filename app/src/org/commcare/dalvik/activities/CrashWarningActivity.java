package org.commcare.dalvik.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.util.CommCareExceptionHandler;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

/**
 * Shows reason for unrecoverable crash to user, reports crash to server,
 * and restarts CommCare.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class CrashWarningActivity extends Activity {
    private int errorMessageVisibility = View.GONE;
    private static final String ERROR_VISIBLE = "error-message-is-visible";

    private LinearLayout errorView;
    private ImageButton infoButton;
    private Throwable exception;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadSaveInstanceState(savedInstanceState);

        setContentView(R.layout.activity_crash_warning);

        loadExceptionFromIntent();

        ExceptionReportTask task = new ExceptionReportTask();
        task.execute(exception);

        setupUi();
    }

    private void loadExceptionFromIntent() {
        Intent intent = getIntent();
        if (intent.hasExtra(CommCareExceptionHandler.CRASH_EXCEPTION_KEY)) {
            Bundle extras = intent.getExtras();
            exception = (Throwable)extras.getSerializable(CommCareExceptionHandler.CRASH_EXCEPTION_KEY);
        }
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            errorMessageVisibility =
                    savedInstanceState.getInt(ERROR_VISIBLE, View.GONE);
        }
    }

    private void setupUi() {
        setupButtons();
        setupText();
    }

    private void setupButtons() {
        Button closeButton = (Button)findViewById(R.id.RestartCommCare);
        closeButton.setText(Localization.get("crash.warning.button"));
        closeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                restartCommCare();
            }
        });

        infoButton = (ImageButton)findViewById(R.id.InfoButton);
        infoButton.setOnClickListener(new View.OnClickListener() {
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

    private void setupText() {
        TextView simpleWarningView = (TextView)findViewById(R.id.SimpleWarningMessage);
        simpleWarningView.setText(Localization.get("crash.warning.header"));

        TextView errorMessageView = (TextView)findViewById(R.id.ErrorText);
        errorMessageView.setText(Localization.get("crash.warning.detail") +
                "\n" + exception.getMessage());

        errorView = (LinearLayout)findViewById(R.id.Error);
        errorView.setVisibility(errorMessageVisibility);
        updateButtonState();
    }

    private void toggleErrorMessageVisibility() {
        if (errorView.getVisibility() == View.GONE) {
            errorView.setVisibility(View.VISIBLE);
        } else {
            errorView.setVisibility(View.GONE);
        }
        updateButtonState();
    }

    private void updateButtonState() {
        if (errorView.getVisibility() == View.GONE) {
            infoButton.setImageResource(R.drawable.icon_info_outline_neutral);
        } else {
            infoButton.setImageResource(R.drawable.icon_info_fill_neutral);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(ERROR_VISIBLE, errorView.getVisibility());
    }
}

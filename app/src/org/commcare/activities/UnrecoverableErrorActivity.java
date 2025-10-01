package org.commcare.activities;

import android.content.DialogInterface;
import android.os.Bundle;

import org.commcare.utils.CommCareLifecycleUtils;
import org.commcare.views.dialogs.AlertDialogFragment;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

/**
 * @author ctsims
 */
public class UnrecoverableErrorActivity extends CommonBaseActivity {

    public static final String EXTRA_ERROR_TITLE = "UnrecoverableErrorActivity_Title";
    public static final String EXTRA_ERROR_MESSAGE = "UnrecoverableErrorActivity_Message";
    public static final String EXTRA_USE_MESSAGE = "use_extra_message";
    public static final String EXTRA_RESTART = "extra_restart";

    private String title;
    private String message;
    private boolean useExtraMessage;
    private boolean restart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = this.getIntent().getStringExtra(EXTRA_ERROR_TITLE);
        message = this.getIntent().getStringExtra(EXTRA_ERROR_MESSAGE);
        useExtraMessage = this.getIntent().getBooleanExtra(EXTRA_USE_MESSAGE, true);
        restart = this.getIntent().getBooleanExtra(EXTRA_RESTART, false);
        createAlertDialog().show(getSupportFragmentManager(), "error-dialog");
    }

    private AlertDialogFragment createAlertDialog() {
        if (useExtraMessage) {
            message = message + "\n\n" + Localization.get("app.handled.error.explanation");
        }
        StandardAlertDialog d = new StandardAlertDialog(title, message);
        DialogInterface.OnClickListener buttonListener = (dialog, i) -> {
            if (restart) {
                CommCareLifecycleUtils.restartCommCare(UnrecoverableErrorActivity.this, true);
            } else {
                finish();
            }
        };

        d.setPositiveButton(restart ? Localization.get("commcare.restart") : Localization.get("app.storage.missing.button"), buttonListener);
        return AlertDialogFragment.fromCommCareAlertDialog(d);
    }
}

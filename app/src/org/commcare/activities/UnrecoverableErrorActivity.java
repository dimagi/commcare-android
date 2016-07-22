package org.commcare.activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import org.commcare.CommCareApplication;
import org.commcare.views.dialogs.AlertDialogFragment;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

/**
 * @author ctsims
 */
public class UnrecoverableErrorActivity extends FragmentActivity {

    public static final String EXTRA_ERROR_TITLE = "UnrecoverableErrorActivity_Title";
    public static final String EXTRA_ERROR_MESSAGE = "UnrecoverableErrorActivity_Message";
    public static final String EXTRA_USE_MESSAGE = "use_extra_message";

    private String title;
    private String message;
    private boolean useExtraMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        title = this.getIntent().getStringExtra(EXTRA_ERROR_TITLE);
        message = this.getIntent().getStringExtra(EXTRA_ERROR_MESSAGE);
        useExtraMessage = this.getIntent().getBooleanExtra(EXTRA_USE_MESSAGE, true);
        createAlertDialog().show(getSupportFragmentManager(), "error-dialog");
    }

    private AlertDialogFragment createAlertDialog() {
        if (useExtraMessage) {
            message = message + "\n\n" + Localization.get("app.handled.error.explanation");
        }
        StandardAlertDialog d = new StandardAlertDialog(this, title, message);
        DialogInterface.OnClickListener buttonListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                CommCareApplication.restartCommCare(UnrecoverableErrorActivity.this, true);
            }
        };
        d.setPositiveButton(Localization.get("app.storage.missing.button"), buttonListener);
        return AlertDialogFragment.fromCommCareAlertDialog(d);
    }
}

/**
 * 
 */
package org.commcare.dalvik.activities;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import org.commcare.dalvik.dialogs.AlertDialogFactory;
import org.javarosa.core.services.locale.Localization;

/**
 * @author ctsims
 *
 */
public class UnrecoverableErrorActivity extends Activity {
    
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
        this.showDialog(0);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (useExtraMessage) {
            message = message + "\n\n" + Localization.get("app.handled.error.explanation");
        }
        AlertDialogFactory factory = new AlertDialogFactory(this, title, message);
        DialogInterface.OnClickListener buttonListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
               Intent intent = new Intent(UnrecoverableErrorActivity.this, CommCareHomeActivity.class);

                //Make sure that the new stack starts with a home activity, and clear everything between.
               intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET |
                       Intent.FLAG_ACTIVITY_CLEAR_TOP |
                       Intent.FLAG_ACTIVITY_SINGLE_TOP |
                       Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
               UnrecoverableErrorActivity.this.startActivity(intent);
               UnrecoverableErrorActivity.this.moveTaskToBack(true);

               System.runFinalizersOnExit(true);
               System.exit(0);
            }
        };
        factory.setPositiveButton(Localization.get("app.storage.missing.button"), buttonListener);
        return factory.getDialog();
    }
}

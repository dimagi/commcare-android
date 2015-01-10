/**
 * 
 */
package org.commcare.dalvik.activities;

import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

/**
 * @author ctsims
 *
 */
public class ManagerRebootActivity extends Activity {
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /* (non-Javadoc)
     * @see android.app.Activity#onCreateDialog(int)
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        String title = "App uninstalled";
        String message = "Your app has been uninstalled, CommCare will now reboot to save changes.";
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        dialog.setTitle(title);
        dialog.setMessage(message);
        
        DialogInterface.OnClickListener button = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
               
               Intent intent = new Intent(ManagerRebootActivity.this, CommCareHomeActivity.class);

               //Make sure that the new stack starts with a home activity, and clear everything between.
               intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
               ManagerRebootActivity.this.startActivity(intent);
               ManagerRebootActivity.this.moveTaskToBack(true);

               System.runFinalizersOnExit(true);
               System.exit(0);
            }
        };
        dialog.setCancelable(true);
        dialog.setButton("OK", button);
        return dialog;
    }
}
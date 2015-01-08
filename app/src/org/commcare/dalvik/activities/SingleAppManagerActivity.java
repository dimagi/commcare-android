package org.commcare.dalvik.activities;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class SingleAppManagerActivity extends CommCareActivity {
    
    private ApplicationRecord appRecord;
    private AlertDialog dialog;
    
    @Override
    public void onCreate(Bundle savedInstanceState) { 
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        int position = i.getIntExtra("position", -1);
        appRecord = CommCareApplication._().getAppAtIndex(position);
        String appName = appRecord.getDisplayName();
        boolean resourcesValidated = appRecord.resourcesValidated();
        boolean isArchived = appRecord.isArchived();
        
        setContentView(R.layout.single_app_view);
        
        //Set app name
        TextView tv = (TextView) findViewById(R.id.app_name);
        tv.setText(appName);
        
        //Validate button only clickable if resources are not yet validated
        refreshValidateButton();
        
        //Change text for archive button depending on archive status
        Button archiveButton = (Button) findViewById(R.id.archive_button);
        if (isArchived) {
            System.out.println("Setting button to 'Unarchive'");
            archiveButton.setText("Unarchive");
        } else {
            archiveButton.setText("Archive");
            System.out.println("Setting button to 'Archive'");
        }
    }
    
    private void refreshValidateButton() {        
        boolean resourcesValidated = appRecord.resourcesValidated();
        Button validateButton = (Button) findViewById(R.id.verify_button);
        if (resourcesValidated) {
            validateButton.setEnabled(false);
        } else {
            validateButton.setEnabled(true);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
        case CommCareHomeActivity.UPGRADE_APP:
            if(resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Your update did not complete", Toast.LENGTH_LONG).show();
            } else if(resultCode == RESULT_OK) {
                //Set flag that we should autoupdate on next login
                SharedPreferences preferences = CommCareApplication._().getCurrentApp().getAppPreferences();
                preferences.edit().putBoolean(CommCarePreferences.AUTO_TRIGGER_UPDATE,true);
            }
            break;
        case CommCareHomeActivity.MISSING_MEDIA_ACTIVITY:
            refreshValidateButton();
            if (resultCode == RESULT_CANCELED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Media Not Verified");
                builder.setMessage(R.string.skipped_verification_warning_2)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                            }
                            
                        });
                dialog = builder.create();
                dialog.show();
            }
            else if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
            }
            break;
        case CommCareHomeActivity.RESTART_APP:
            if (dialog != null) {
                dialog.dismiss();
            }
        }
    }
    
    /** Uninstalls the selected app **/
    public void uninstall(View v) {
        CommCareApplication._().initializeAppResources(new CommCareApp(appRecord));
        CommCareApp app = CommCareApplication._().getCurrentApp();
        
        /*String title = "Uninstalling your app";
        String message = "Please wait while CommCare uninstalls your app and reboots to save changes";
        showAlertDialog(title, message);*/
        
        //1) Teardown the sandbox for this app
        app.teardownSandbox();
        //2) Delete all the user databases associated with this app
        SqlStorage<UserKeyRecord> userDatabase = CommCareApplication._().getAppStorage(UserKeyRecord.class);
        for (UserKeyRecord user : userDatabase) {
            this.getDatabasePath(CommCareUserOpenHelper.getDbName(user.getUuid())).delete();
        }
        //3) Delete the app database
        this.getDatabasePath(DatabaseAppOpenHelper.getDbName(app.getAppRecord().getApplicationId())).delete();
        //4) Delete the app record
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).remove(appRecord.getID());
        //5) Reset the dbState and appResourceState in CCApplication
        CommCareApplication._().setAppResourceState(CommCareApplication.STATE_UNINSTALLED);
        CommCareApplication._().setDatabaseState(CommCareApplication.STATE_UNINSTALLED);
        
        rebootCommCare();
    }
    
    /** If the app is not archived, sets it to archived (i.e. still installed but 
     * not visible to users); If it is archived, sets it to unarchived **/
    public void toggleArchived(View v) {
        System.out.println("toggleArchiveSelected called");
        appRecord.setArchiveStatus(!appRecord.isArchived());
        Button b = (Button) v;
        if (appRecord.isArchived()) {
            System.out.println("AppManagerActivity setting button to 'Unarchive'");
            b.setText("Unarchive");
        } else {
            System.out.println("AppManagerAdapter setting button to 'Archive'");
            b.setText("Archive");
        }
    }
    
    /** Opens the MM verification activity for the selected app **/
    public void verifyResources(View v) {
        CommCareApplication._().initializeAppResources(new CommCareApp(appRecord));
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
        this.startActivityForResult(i, CommCareHomeActivity.MISSING_MEDIA_ACTIVITY);
    }
    
    /** Conducts an update for the selected app **/
    public void update(View v) {
        CommCareApplication._().initializeAppResources(new CommCareApp(appRecord));
        Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String ref = prefs.getString("default_app_server", null);
        i.putExtra(CommCareSetupActivity.KEY_PROFILE_REF, ref);
        i.putExtra(CommCareSetupActivity.KEY_UPGRADE_MODE, true);
        i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
        startActivityForResult(i,CommCareHomeActivity.UPGRADE_APP);
    }
    
    public void rebootCommCare() {
        Intent i = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage( getBaseContext().getPackageName() );
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        this.startActivityForResult(i, CommCareHomeActivity.RESTART_APP);
    }
    
    public void showAlertDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        dialog = builder.create();
        dialog.show();
        SystemClock.sleep(2000);
    }

}

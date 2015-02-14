package org.commcare.dalvik.activities;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.app.DatabaseAppOpenHelper;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.services.CommCareSessionService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class SingleAppManagerActivity extends Activity {
    
    private ApplicationRecord appRecord;
    private AlertDialog dialog;
    public static final int LOGOUT_FOR_UPDATE = 0;
    public static final int LOGOUT_FOR_VERIFY_MM = 1;
    
    @Override
    public void onCreate(Bundle savedInstanceState) { 
        super.onCreate(savedInstanceState);
        setContentView(R.layout.single_app_view);

        int position = getIntent().getIntExtra("position", -1);
        appRecord = CommCareApplication._().getAppAtIndex(position);
        // Implies that this appRecord has been uninstalled since last we launched SingleAppManagerActivity,
        // so redirect to AppManagerActivity
        if (appRecord == null) {
            Intent i = new Intent(getApplicationContext(), AppManagerActivity.class);
            startActivity(i);
            finish();
        }
        //Set app name
        String appName = appRecord.getDisplayName();                
        TextView tv = (TextView) findViewById(R.id.app_name);
        tv.setText(appName);
        
    }
    
    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }
    
    private void refresh() {    
        //refresh old profile warning 
        TextView warning = (TextView) findViewById(R.id.profile_warning);
        if (appRecord.fromOldProfileFile()) {
            warning.setVisibility(View.VISIBLE);
        } else {
            warning.setVisibility(View.GONE);
        }
        
        //refresh validate button
        Button validateButton = (Button) findViewById(R.id.verify_button);
        if (appRecord.resourcesValidated()) {
            validateButton.setVisibility(View.INVISIBLE);
        } else {
            validateButton.setVisibility(View.VISIBLE);
        }
        
        //Change text for archive button depending on archive status
        boolean isArchived = appRecord.isArchived();
        Button archiveButton = (Button) findViewById(R.id.archive_button);
        if (isArchived) {
            archiveButton.setText("Unarchive");
        } else {
            archiveButton.setText("Archive");
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
            refresh();
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
            Intent i = new Intent(getApplicationContext(), AppManagerActivity.class);
            startActivity(i);
        }
    }
    
    /** Uninstalls the selected app **/
    public void uninstall() {
        CommCareApplication._().logout();
        CommCareApplication._().initializeAppResources(new CommCareApp(appRecord));
        CommCareApp app = CommCareApplication._().getCurrentApp();
        
        //1) Set states to delete requested so we know if we have left the app in a bad state
        CommCareApplication._().setAppResourceState(CommCareApplication.STATE_DELETE_REQUESTED);
        appRecord.setStatus(ApplicationRecord.STATUS_DELETE_REQUESTED);
        //2) Teardown the sandbox for this app
        app.teardownSandbox();
        //3) Delete all the user databases associated with this app
        SqlStorage<UserKeyRecord> userDatabase = CommCareApplication._().getAppStorage(UserKeyRecord.class);
        for (UserKeyRecord user : userDatabase) {
            this.getDatabasePath(CommCareUserOpenHelper.getDbName(user.getUuid())).delete();
        }
        //4) Delete the app database
        this.getDatabasePath(DatabaseAppOpenHelper.getDbName(app.getAppRecord().getApplicationId())).delete();
        //5) Delete the app record
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).remove(appRecord.getID());
        //6) Reset the appResourceState in CCApplication
        CommCareApplication._().setAppResourceState(CommCareApplication.STATE_UNINSTALLED);
        
        rebootCommCare();
   }
    
    /** If the app is not archived, sets it to archived (i.e. still installed but 
     * not visible to users); If it is archived, sets it to unarchived **/
    public void toggleArchived(View v) {
        appRecord.setArchiveStatus(!appRecord.isArchived());
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(appRecord);
        refresh();
    }
    
    //triggered when verify MM button is clicked
    public void verifyResourcesClicked(View v) {
        try {
            CommCareSessionService s = CommCareApplication._().getSession();
            if (s.isLoggedIn()) {
                triggerLogoutWarning(LOGOUT_FOR_VERIFY_MM);
            } else {
                verifyResources();
            }
        } catch (SessionUnavailableException e) {
            verifyResources();
        }
    }
    
    /** Opens the MM verification activity for the selected app **/
    public void verifyResources() {
        CommCareApplication._().initializeAppResources(new CommCareApp(appRecord));
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
        this.startActivityForResult(i, CommCareHomeActivity.MISSING_MEDIA_ACTIVITY);
    }
    
    //Triggered when update button is clicked
    public void updateClicked(View v) {
        try {
            CommCareSessionService s = CommCareApplication._().getSession();
            if (s.isLoggedIn()) {
                triggerLogoutWarning(LOGOUT_FOR_UPDATE);
            } else {
                update();
            }
        }
        catch (SessionUnavailableException e) {
            update();
        }
    }
    
    /** Conducts an update for the selected app **/
    public void update() {
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
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivityForResult(i, CommCareHomeActivity.RESTART_APP);
    }
        
    public void rebootAlertDialog(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Uninstalling your app");
        builder.setMessage(R.string.uninstall_reboot_warning)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog,
                        int which) {
                    dialog.dismiss();
                    uninstall();
                }
                    
            })
            .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            
        dialog = builder.create();
        dialog.show();
    }
    
    public void triggerLogoutWarning(final int actionKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logging out your app");
        builder.setMessage(R.string.logout_warning)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog,
                            int which) {
                        dialog.dismiss();
                        CommCareApplication._().logout();
                        switch (actionKey) {
                        case LOGOUT_FOR_UPDATE:
                            update();
                            break;
                        case LOGOUT_FOR_VERIFY_MM:
                            verifyResources();
                            break;
                        } 
                    }
                    
                })
            .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
                
            });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

}

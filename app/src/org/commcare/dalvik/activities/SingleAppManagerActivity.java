package org.commcare.dalvik.activities;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.services.CommCareSessionService;
import org.javarosa.core.services.locale.Localization;

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

/**
 * The activity launched when the user clicks on a specific app within the app manager. From
 * this screen, the selected app can be archived/unarchived, uninstalled, updated, or have its
 * multimedia verified if needed.
 *
 * @author amstone
 */

public class SingleAppManagerActivity extends Activity {

    private ApplicationRecord appRecord;
    private AlertDialog dialog;
    private static final int LOGOUT_FOR_UPDATE = 0;
    private static final int LOGOUT_FOR_VERIFY_MM = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.single_app_view);
        // Try to retrieve the app record at the indicated position
        int position = getIntent().getIntExtra("position", -1);
        appRecord = CommCareApplication._().getAppAtIndex(position);
        // Implies that this appRecord has been uninstalled since last we launched
        // SingleAppManagerActivity, so redirect to AppManagerActivity
        if (appRecord == null) {
            Intent i = new Intent(getApplicationContext(), AppManagerActivity.class);
            startActivity(i);
            finish();
        }
        //Set app name
        String appName = appRecord.getDisplayName();
        TextView tv = (TextView)findViewById(R.id.app_name);
        tv.setText(appName);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    /**
     * Refresh all button appearances based on the current state of the app
     */
    private void refresh() {
        // Warns the user that this app came from an old version of the profile file, if necessary
        TextView warning = (TextView)findViewById(R.id.profile_warning);
        if (appRecord.preMultipleAppsProfile()) {
            warning.setVisibility(View.VISIBLE);
        } else {
            warning.setVisibility(View.GONE);
        }

        // Updates text of the validate button based on the current state of the app's resources
        Button validateButton = (Button)findViewById(R.id.verify_button);
        if (appRecord.resourcesValidated()) {
            validateButton.setVisibility(View.INVISIBLE);
        } else {
            validateButton.setVisibility(View.VISIBLE);
        }

        // Updates text of the archive button based on app's archive status
        boolean isArchived = appRecord.isArchived();
        Button archiveButton = (Button)findViewById(R.id.archive_button);
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
                if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "Your update did not complete", Toast.LENGTH_LONG).show();
                } else if (resultCode == RESULT_OK) {
                    if (intent.getBooleanExtra(CommCareSetupActivity.KEY_REQUIRE_REFRESH, true)) {
                        Toast.makeText(this, Localization.get("update.success.refresh"), Toast.LENGTH_LONG).show();
                        try {
                            CommCareApplication._().getSession().closeSession(false);
                        } catch (SessionUnavailableException e) {
                            // If the session isn't available, we don't need to logout
                        }
                    }
                    return;
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
                } else if (resultCode == RESULT_OK) {
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

    /**
     * Uninstalls the selected app
     */
    private void uninstall() {
        try {
            CommCareApplication._().getSession().closeSession(false);
        } catch (SessionUnavailableException e) {
            // if the session isn't available, we don't need to logout
        }
        CommCareApplication._().uninstall(appRecord);
        rebootCommCare();
    }

    /**
     * onClick method for Archive button. If the app is not archived, sets it to
     * archived (i.e. still installed but not visible to users).
     * If it is archived, sets it to unarchived
     *
     * @param v linter sees this as unused, but is required for a button to find its onClick method
     */
    public void toggleArchived(View v) {
        appRecord.setArchiveStatus(!appRecord.isArchived());
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(appRecord);
        refresh();
    }

    /**
     * onClick method for Validate Multimedia button
     *
     * @param v linter sees this as unused, but is required for a button to find its onClick method
     */
    public void verifyResourcesClicked(View v) {
        try {
            CommCareSessionService s = CommCareApplication._().getSession();
            if (s.isActive()) {
                triggerLogoutWarning(LOGOUT_FOR_VERIFY_MM);
            } else {
                verifyResources();
            }
        } catch (SessionUnavailableException e) {
            verifyResources();
        }
    }

    /**
     * Opens the MM verification activity for the selected app
     */
    private void verifyResources() {
        CommCareApplication._().initializeAppResources(new CommCareApp(appRecord));
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
        this.startActivityForResult(i, CommCareHomeActivity.MISSING_MEDIA_ACTIVITY);
    }

    /**
     * onClick method for Update button
     *
     * @param v linter sees this as unused, but is required for a button to find its onClick method
     */
    public void updateClicked(View v) {
        try {
            CommCareSessionService s = CommCareApplication._().getSession();
            if (s.isActive()) {
                triggerLogoutWarning(LOGOUT_FOR_UPDATE);
            } else {
                update();
            }
        } catch (SessionUnavailableException e) {
            update();
        }
    }

    /**
     * Conducts an update for the selected app
     */
    private void update() {
        CommCareApplication._().initializeAppResources(new CommCareApp(appRecord));
        Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String ref = prefs.getString("default_app_server", null);
        i.putExtra(CommCareSetupActivity.KEY_PROFILE_REF, ref);
        i.putExtra(CommCareSetupActivity.KEY_UPGRADE_MODE, true);
        i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
        startActivityForResult(i, CommCareHomeActivity.UPGRADE_APP);
    }

    /**
     * Relaunches CommCare after an app has been uninstalled
     */
    private void rebootCommCare() {
        Intent i = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivityForResult(i, CommCareHomeActivity.RESTART_APP);
    }

    /**
     * onClick method for Uninstall button. Before actually conducting the uninstall, warns
     * the user that it will also result in a reboot of CC
     *
     * @param v linter sees this as unused, but is required for a button to find its onClick method
     */
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

    /**
     * Warns a user that the action they are trying to conduct will result in the current
     * session being logged out
     */
    private void triggerLogoutWarning(final int actionKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Logging out your app");
        builder.setMessage(R.string.logout_warning)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        dialog.dismiss();
                        try {
                            CommCareApplication._().getSession().closeSession(false);
                        } catch (SessionUnavailableException e) {
                            // If the session isn't available, we don't need to logout
                        }
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
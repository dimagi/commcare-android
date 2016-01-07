package org.commcare.dalvik.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.AlertDialogFactory;
import org.commcare.dalvik.services.CommCareSessionService;
import org.javarosa.core.services.locale.Localization;


/**
 * The activity launched when the user clicks on a specific app within the app manager. From
 * this screen, the selected app can be archived/unarchived, uninstalled, updated, or have its
 * multimedia verified if needed.
 *
 * @author amstone
 */

public class SingleAppManagerActivity extends Activity {

    private ApplicationRecord appRecord;
    private static final int LOGOUT_FOR_UPDATE = 0;
    private static final int LOGOUT_FOR_VERIFY_MM = 1;
    private static final int LOGOUT_FOR_ARCHIVE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.single_app_view);

        // Retrieve the app record that should be represented by this activity
        int position = getIntent().getIntExtra("position", -1);
        appRecord = getAppForPosition(position);
        if (appRecord == null) {
            // Implies that this appRecord has been uninstalled since last we launched
            // SingleAppManagerActivity, so redirect to AppManagerActivity
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
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /**
     *
     * @param position the position in AppManagerActivity's list view that was clicked to trigger
     *                 this activity
     * @return the ApplicationRecord corresponding to the app that should be displayed in this
     * activity, based upon the position
     */
    private ApplicationRecord getAppForPosition(int position) {
        ApplicationRecord[] currentApps = CommCareApplication._().appRecordArray();
        if (position < 0 || position >= currentApps.length) {
            return null;
        } else {
            return currentApps[position];
        }
    }

    /**
     * Refresh all button appearances based on the current state of the app
     */
    private void refresh() {
        // Warns the user that this app came from an old version of the profile file, if necessary
        TextView warning = (TextView)findViewById(R.id.profile_warning);
        if (appRecord.isPreMultipleAppsProfile()) {
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
            archiveButton.setText(R.string.unarchive_app);
        } else {
            archiveButton.setText(R.string.archive_app);
        }

        // Sets the app version
        int appVersion = appRecord.getVersionNumber();
        TextView tv = (TextView)findViewById(R.id.app_version);
        tv.setText("App Version: " + appVersion);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case CommCareHomeActivity.UPGRADE_APP:
                if (resultCode == RESULT_CANCELED) {
                    UpdateTask task = UpdateTask.getRunningInstance();
                    if (task != null) {
                        Toast.makeText(this, R.string.update_canceled, Toast.LENGTH_LONG).show();
                        task.cancel(true);
                    }
                } else if (resultCode == RESULT_OK) {
                    if (intent.getBooleanExtra(CommCareSetupActivity.KEY_REQUIRE_REFRESH, true)) {
                        Toast.makeText(this, Localization.get("update.success"), Toast.LENGTH_LONG).show();
                        CommCareApplication._().expireUserSession();
                    }
                    return;
                }
                break;
            case DispatchActivity.MISSING_MEDIA_ACTIVITY:
                refresh();
                if (resultCode == RESULT_CANCELED) {
                    String title = getString(R.string.media_not_verified);
                    String msg = getString(R.string.skipped_verification_warning_2);
                    AlertDialogFactory.getBasicAlertFactory(this, title, msg, null).showDialog();
                } else if (resultCode == RESULT_OK) {
                    Toast.makeText(this, R.string.media_verified, Toast.LENGTH_LONG).show();
                }
        }
    }

    /**
     * Uninstalls the selected app
     */
    private void uninstall() {
        CommCareApplication._().expireUserSession();
        CommCareApplication._().uninstall(appRecord);
        CommCareApplication._().restartCommCare(SingleAppManagerActivity.this);
    }

    /**
     * onClick method for Archive button. If the app is not archived, sets it to archived
     * (i.e. still installed but not visible to users). If it is archived, sets it to unarchived
     *
     * @param v linter sees this as unused, but is required for a button to find its onClick method
     */
    public void toggleArchiveClicked(View v) {
        if (CommCareApplication._().isSeated(appRecord)) {
            try {
                CommCareSessionService s = CommCareApplication._().getSession();
                if (s.isActive()) {
                    triggerLogoutWarning(LOGOUT_FOR_ARCHIVE);
                } else {
                    toggleArchived();
                }
            } catch (SessionUnavailableException e) {
                toggleArchived();
            }
        } else {
            toggleArchived();
        }

    }

    private void toggleArchived() {
        appRecord.setArchiveStatus(!appRecord.isArchived());
        CommCareApplication._().getGlobalStorage(ApplicationRecord.class).write(appRecord);
        if (CommCareApplication._().isSeated(appRecord)) {
            CommCareApplication._().getCurrentApp().refreshAppRecord();
        }
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
        this.startActivityForResult(i, DispatchActivity.MISSING_MEDIA_ACTIVITY);
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
        Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
        startActivityForResult(i, CommCareHomeActivity.UPGRADE_APP);
    }

    /**
     * onClick method for Uninstall button. Before actually conducting the uninstall, warns
     * the user that it will also result in a reboot of CC
     *
     * @param v linter sees this as unused, but is required for a button to find its onClick method
     */
    public void rebootAlertDialog(View v) {
        AlertDialogFactory factory = new AlertDialogFactory(this, getString(R.string.uninstalling),
                getString(R.string.uninstall_reboot_warning));
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    uninstall();
                }
            }
        };
        factory.setPositiveButton(getString(R.string.ok), listener);
        factory.setNegativeButton(getString(R.string.cancel), listener);
        factory.showDialog();
    }

    /**
     * Warns a user that the action they are trying to conduct will result in the current
     * session being logged out
     */
    private void triggerLogoutWarning(final int actionKey) {
        AlertDialogFactory factory = new AlertDialogFactory(this, getString(R.string.logging_out),
                getString(R.string.logout_warning));
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                dialog.dismiss();
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    CommCareApplication._().expireUserSession();
                    switch (actionKey) {
                        case LOGOUT_FOR_UPDATE:
                            update();
                            break;
                        case LOGOUT_FOR_VERIFY_MM:
                            verifyResources();
                            break;
                        case LOGOUT_FOR_ARCHIVE:
                            toggleArchived();
                    }
                }
            }

        };
        factory.setPositiveButton(getString(R.string.ok), listener);
        factory.setNegativeButton(getString(R.string.cancel), listener);
        factory.showDialog();
    }
}

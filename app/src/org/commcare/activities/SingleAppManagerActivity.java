package org.commcare.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.services.CommCareSessionService;
import org.commcare.tasks.UpdateTask;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.StandardAlertDialog;


/**
 * The activity launched when the user clicks on a specific app within the app manager. From
 * this screen, the selected app can be archived/unarchived, uninstalled, updated, or have its
 * multimedia verified if needed.
 *
 * @author amstone
 */

public class SingleAppManagerActivity extends CommCareActivity {

    private ApplicationRecord appRecord;
    private static final int LOGOUT_FOR_UPDATE = 0;
    private static final int LOGOUT_FOR_VERIFY_MM = 1;
    private static final int LOGOUT_FOR_ARCHIVE = 2;

    private static final int UPGRADE_APP = 0;
    private static final int MISSING_MEDIA_ACTIVITY = 1;
    private static final int SEAT_APP_ACTIVITY = 2;

    private static final String KEY_LAUNCH_UPDATE_AFTER_SEATING = "launch-update-after-seating";
    private boolean launchUpdateAfterSeating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.single_app_view);

        if (savedInstanceState != null) {
            launchUpdateAfterSeating = savedInstanceState.getBoolean(KEY_LAUNCH_UPDATE_AFTER_SEATING);
        }

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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_LAUNCH_UPDATE_AFTER_SEATING, launchUpdateAfterSeating);
    }

    /**
     * @param position the position in AppManagerActivity's list view that was clicked to trigger
     *                 this activity
     * @return the ApplicationRecord corresponding to the app that should be displayed in this
     * activity, based upon the position
     */
    private static ApplicationRecord getAppForPosition(int position) {
        ApplicationRecord[] currentApps = MultipleAppsUtil.appRecordArray();
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
            case UPGRADE_APP:
                if (resultCode == RESULT_CANCELED) {
                    UpdateTask task = UpdateTask.getRunningInstance();
                    if (task != null) {
                        Toast.makeText(this, R.string.update_canceled, Toast.LENGTH_LONG).show();
                        task.cancel(true);
                    }
                }
                return;
            case MISSING_MEDIA_ACTIVITY:
                refresh();
                if (resultCode == RESULT_CANCELED) {
                    String title = getString(R.string.media_not_verified);
                    String msg = getString(R.string.skipped_verification_warning_2);
                    showAlertDialog(StandardAlertDialog.getBasicAlertDialog(this, title, msg, null));
                } else if (resultCode == RESULT_OK) {
                    Toast.makeText(this, R.string.media_verified, Toast.LENGTH_LONG).show();
                }
                return;
            case SEAT_APP_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    if (launchUpdateAfterSeating) {
                        launchUpdateActivity();
                    } else {
                        launchVerificationActivity();
                    }
                }
                return;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    /**
     * Uninstalls the selected app
     */
    private void uninstall() {
        GoogleAnalyticsUtils.reportAppManagerAction(GoogleAnalyticsFields.ACTION_UNINSTALL_APP);
        CommCareApplication._().expireUserSession();
        CommCareApplication._().uninstall(appRecord);
        CommCareApplication.restartCommCare(
                SingleAppManagerActivity.this, AppManagerActivity.class, false);
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
        if (!appRecord.isArchived()) {
            GoogleAnalyticsUtils.reportAppManagerAction(GoogleAnalyticsFields.ACTION_ARCHIVE_APP);
        }
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
        if (!CommCareApplication._().isSeated(appRecord)) {
            launchUpdateAfterSeating = false;
            seatApp();
        } else {
            launchVerificationActivity();
        }
    }

    private void launchVerificationActivity() {
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
        this.startActivityForResult(i, MISSING_MEDIA_ACTIVITY);
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
        if (!CommCareApplication._().isSeated(appRecord)) {
            launchUpdateAfterSeating = true;
            seatApp();
        } else {
            launchUpdateActivity();
        }
    }

    private void seatApp() {
        Intent i = new Intent(this, SeatAppActivity.class);
        i.putExtra(SeatAppActivity.KEY_APP_TO_SEAT, appRecord.getUniqueId());
        this.startActivityForResult(i, SEAT_APP_ACTIVITY);
    }

    private void launchUpdateActivity() {
        Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
        i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
        startActivityForResult(i, UPGRADE_APP);
    }

    /**
     * onClick method for Uninstall button. Before actually conducting the uninstall, warns
     * the user that it will also result in a reboot of CC
     *
     * @param v linter sees this as unused, but is required for a button to find its onClick method
     */
    public void rebootAlertDialog(View v) {
        StandardAlertDialog d = new StandardAlertDialog(this, getString(R.string.uninstalling),
                getString(R.string.uninstall_reboot_warning));
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissAlertDialog();
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    uninstall();
                }
            }
        };
        d.setPositiveButton(getString(R.string.ok), listener);
        d.setNegativeButton(getString(R.string.cancel), listener);
        showAlertDialog(d);
    }

    /**
     * Warns a user that the action they are trying to conduct will result in the current
     * session being logged out
     */
    private void triggerLogoutWarning(final int actionKey) {
        StandardAlertDialog d = new StandardAlertDialog(this, getString(R.string.logging_out),
                getString(R.string.logout_warning));
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                dismissAlertDialog();
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
        d.setPositiveButton(getString(R.string.ok), listener);
        d.setNegativeButton(getString(R.string.cancel), listener);
        showAlertDialog(d);
    }
}

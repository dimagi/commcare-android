package org.commcare.activities;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogger;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.utils.AndroidShortcuts;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * Dispatches install, login, and home screen activities.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DispatchActivity extends FragmentActivity {
    private static final String SESSION_REQUEST = "ccodk_session_request";
    public static final String WAS_EXTERNAL = "launch_from_external";
    public static final String WAS_SHORTCUT_LAUNCH = "launch_from_shortcut";
    public static final String START_FROM_LOGIN = "process_successful_login";

    private static final int DIALOG_CORRUPTED = 1;

    private static final int LOGIN_USER = 0;
    private static final int HOME_SCREEN = 1;
    public static final int INIT_APP = 2;

    /**
     * Request code for automatically validating media.
     * Should signal a return from CommCareVerificationActivity.
     */
    public static final int MISSING_MEDIA_ACTIVITY = 4;

    private boolean startFromLogin;
    private LoginMode lastLoginMode;
    private boolean userManuallyEnteredPasswordMode;

    private boolean shouldFinish;
    private boolean userTriggeredLogout;
    private boolean shortcutExtraWasConsumed;

    private static final String EXTRA_CONSUMED_KEY = "shortcut_extra_was_consumed";

    // Used for soft assert for login redirection bug
    private boolean waitingForActivityResultFromLogin;

    boolean shouldCheckForLocalAppFilesChange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (finishIfNotRoot()) {
            return;
        }

        if (savedInstanceState != null) {
            shortcutExtraWasConsumed = savedInstanceState.getBoolean(EXTRA_CONSUMED_KEY);
        }

        // Note: It is important that this check happens in onCreate, and not at the time that
        // the value is actually used, because we only want to check for changed app files on a
        // new creation of the DispatchActivity, not every time it resumes
        shouldCheckForLocalAppFilesChange = CommCareApplication._().isConsumerApp();
    }

    /**
     * A workaround required by Android Bug #2373 -- An app launched from the Google Play store
     * has different intent flags than one launched from the App launcher, which ruins the back
     * stack and prevents the app from launching a high affinity task.
     *
     * @return if finish() was called
     */
    private boolean finishIfNotRoot() {
        if (!isTaskRoot()) {
            Intent intent = getIntent();
            String action = intent.getAction();
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && action != null && action.equals(Intent.ACTION_MAIN)) {
                finish();
                return true;
            }
        }
        return false;
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (shouldFinish) {
            finish();
        } else {
            dispatch();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EXTRA_CONSUMED_KEY, shortcutExtraWasConsumed);
    }

    private void checkForChangedCCZ() {
        Intent i = new Intent(getApplicationContext(), UpdateActivity.class);
        startActivity(i);
    }

    private void checkForChangedRestoreFile() {

    }

    private void dispatch() {
        if (isDbInBadState()) {
            // appropriate error dialog has been triggered, don't continue w/ dispatch
            return;
        }

        if (shouldCheckForLocalAppFilesChange) {
            checkForChangedCCZ();
            checkForChangedRestoreFile();
            shouldCheckForLocalAppFilesChange = false;
        }

        CommCareApp currentApp = CommCareApplication._().getCurrentApp();

        if (currentApp == null) {
            if (CommCareApplication._().usableAppsPresent()) {
                CommCareApplication._().initFirstUsableAppRecord();
                // Recurse in order to make the correct decision based on the new state
                dispatch();
            } else {
                Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
                this.startActivityForResult(i, INIT_APP);
            }
        } else {
            // Note that the order in which these conditions are checked matters!!
            try {
                ApplicationRecord currentRecord = currentApp.getAppRecord();
                if (currentApp.getAppResourceState() == CommCareApplication.STATE_CORRUPTED) {
                    // The seated app is damaged or corrupted
                    handleDamagedApp();
                } else if (!currentRecord.isUsable()) {
                    // The seated app is unusable (means either it is archived or is
                    // missing its MM or both)
                    boolean unseated = handleUnusableApp(currentRecord);
                    if (unseated) {
                        // Recurse in order to make the correct decision based on the new state
                        dispatch();
                    }
                } else if (!CommCareApplication._().getSession().isActive()) {
                    // The user is not logged in
                    launchLoginScreen();
                } else if (this.getIntent().hasExtra(SESSION_REQUEST)) {
                    // CommCare was launched from an external app, with a session descriptor
                    handleExternalLaunch();
                } else if (this.getIntent().hasExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT) &&
                        !shortcutExtraWasConsumed) {
                    // CommCare was launched from a shortcut
                    handleShortcutLaunch();
                } else {
                    launchHomeScreen();
                }
            } catch (SessionUnavailableException sue) {
                launchLoginScreen();
            }
        }
    }

    private boolean isDbInBadState() {
        int dbState = CommCareApplication._().getDatabaseState();
        if (dbState == CommCareApplication.STATE_MIGRATION_FAILED) {
            CommCareApplication._().triggerHandledAppExit(this,
                    getString(R.string.migration_definite_failure),
                    getString(R.string.migration_failure_title), false);
            return true;
        } else if (dbState == CommCareApplication.STATE_MIGRATION_QUESTIONABLE) {
            CommCareApplication._().triggerHandledAppExit(this,
                    getString(R.string.migration_possible_failure),
                    getString(R.string.migration_failure_title), false);
            return true;
        } else if (dbState == CommCareApplication.STATE_CORRUPTED) {
            handleDamagedApp();
            return true;
        }
        return false;
    }

    private void handleDamagedApp() {
        if (!CommCareApplication._().isStorageAvailable()) {
            createNoStorageDialog();
        } else {
            // See if we're logged in. If so, prompt for recovery.
            try {
                CommCareApplication._().getSession();
                showDialog(DIALOG_CORRUPTED);
            } catch (SessionUnavailableException e) {
                // Otherwise, log in first
                launchLoginScreen();
            }
        }
    }

    private void createNoStorageDialog() {
        CommCareApplication._().triggerHandledAppExit(this,
                Localization.get("app.storage.missing.message"),
                Localization.get("app.storage.missing.title"));
    }

    private void launchLoginScreen() {
        if (waitingForActivityResultFromLogin) {
            Logger.log(AndroidLogger.SOFT_ASSERT, "Login redirection bug occurred; " +
                    "DispatchActivity is attempting to launch a new LoginActivity while it is " +
                    "still waiting for a result from another one.");
        }
        Intent i = new Intent(this, LoginActivity.class);
        i.putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, userTriggeredLogout);
        startActivityForResult(i, LOGIN_USER);
        waitingForActivityResultFromLogin = true;
    }

    private void launchHomeScreen() {
        Intent i = new Intent(this, CommCareHomeActivity.class);
        i.putExtra(START_FROM_LOGIN, startFromLogin);
        i.putExtra(LoginActivity.LOGIN_MODE, lastLoginMode);
        i.putExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, userManuallyEnteredPasswordMode);
        startFromLogin = false;
        startActivityForResult(i, HOME_SCREEN);
    }

    /**
     * @param record the ApplicationRecord corresponding to the seated, unusable app
     * @return if the unusable app was unseated by this method
     */
    private boolean handleUnusableApp(ApplicationRecord record) {
        if (record.isArchived()) {
            // If the app is archived, unseat it and try to seat another one
            CommCareApplication._().unseat(record);
            CommCareApplication._().initFirstUsableAppRecord();
            return true;
        } else {
            // This app has unvalidated MM
            if (CommCareApplication._().usableAppsPresent()) {
                // If there are other usable apps, unseat it and seat another one
                CommCareApplication._().unseat(record);
                CommCareApplication._().initFirstUsableAppRecord();
                return true;
            } else {
                handleUnvalidatedApp();
                return false;
            }
        }
    }

    /**
     * Handles the case where the seated app is unvalidated and there are no other usable apps
     * to seat instead -- Either calls out to verification activity or quits out of the app
     */
    private void handleUnvalidatedApp() {
        if (CommCareApplication._().shouldSeeMMVerification()) {
            Intent i = new Intent(this, CommCareVerificationActivity.class);
            this.startActivityForResult(i, MISSING_MEDIA_ACTIVITY);
        } else {
            // Means that there are no usable apps, but there are multiple apps who all don't have
            // MM verified -- show an error message and shut down
            CommCareApplication._().triggerHandledAppExit(this,
                    Localization.get("multiple.apps.unverified.message"),
                    Localization.get("multiple.apps.unverified.title"));
        }
    }

    private void handleExternalLaunch() {
        String sessionRequest = this.getIntent().getStringExtra(SESSION_REQUEST);
        SessionStateDescriptor ssd = new SessionStateDescriptor();
        ssd.fromBundle(sessionRequest);
        CommCareApplication._().getCurrentSessionWrapper().loadFromStateDescription(ssd);
        Intent i = new Intent(this, CommCareHomeActivity.class);
        i.putExtra(WAS_EXTERNAL, true);
        startActivityForResult(i, HOME_SCREEN);
    }

    private void handleShortcutLaunch() {
        if (!triggerLoginIfNeeded()) {
            //We were launched in shortcut mode. Get the command and load us up.
            CommCareApplication._().getCurrentSession().setCommand(
                    this.getIntent().getStringExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT));

            getIntent().removeExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT);
            shortcutExtraWasConsumed = true;
            Intent i = new Intent(this, CommCareHomeActivity.class);
            i.putExtra(WAS_SHORTCUT_LAUNCH, true);
            startActivityForResult(i, HOME_SCREEN);
        }
    }

    private boolean triggerLoginIfNeeded() {
        try {
            if (!CommCareApplication._().getSession().isActive()) {
                launchLoginScreen();
                return true;
            }
        } catch (SessionUnavailableException e) {
            launchLoginScreen();
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // if handling new return code (want to return to home screen) but a return at the end of your statement
        switch (requestCode) {
            case INIT_APP:
                if (resultCode == RESULT_CANCELED) {
                    // User pressed back button from install screen, so take them out of CommCare
                    shouldFinish = true;
                }
                return;
            case MISSING_MEDIA_ACTIVITY:
                if (resultCode == RESULT_CANCELED) {
                    // exit the app if media wasn't validated on automatic
                    // validation check.
                    shouldFinish = true;
                } else if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Media Validated!", Toast.LENGTH_LONG).show();
                }
                return;
            case LOGIN_USER:
                waitingForActivityResultFromLogin = false;
                if (resultCode == RESULT_CANCELED) {
                    shouldFinish = true;
                } else if (intent != null) {
                    lastLoginMode = (LoginMode)intent.getSerializableExtra(LoginActivity.LOGIN_MODE);
                    userManuallyEnteredPasswordMode =
                            intent.getBooleanExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, false);
                    startFromLogin = true;
                }
                return;
            case HOME_SCREEN:
                if (resultCode == RESULT_CANCELED) {
                    shouldFinish = true;
                    return;
                } else {
                    userTriggeredLogout = true;
                }
                return;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_CORRUPTED) {
            return createAskFixDialog();
        } else {
            return null;
        }
    }

    private Dialog createAskFixDialog() {
        //TODO: Localize this in theory, but really shift it to the upgrade/management state
        String title = "Storage is Corrupt :/";
        String message = "Sorry, something really bad has happened, and the app can't start up. " +
                "With your permission CommCare can try to repair itself if you have network access.";
        StandardAlertDialog d = new StandardAlertDialog(this, title, message);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE: // attempt repair
                        Intent intent = new Intent(DispatchActivity.this, RecoveryActivity.class);
                        startActivity(intent);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE: // Shut down
                        DispatchActivity.this.finish();
                        break;
                }
            }
        };
        d.setPositiveButton("Enter Recovery Mode", listener);
        d.setNegativeButton("Shut Down", listener);
        return d.getDialog();
    }
}

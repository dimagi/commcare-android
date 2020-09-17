package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.dalvik.R;
import org.commcare.heartbeat.UpdatePromptHelper;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.recovery.measures.ExecuteRecoveryMeasuresActivity;
import org.commcare.recovery.measures.RecoveryMeasuresHelper;
import org.commcare.utils.AndroidShortcuts;
import org.commcare.utils.CommCareLifecycleUtils;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.services.locale.Localization;

import androidx.fragment.app.FragmentActivity;

/**
 * Dispatches install, login, and home screen activities.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DispatchActivity extends FragmentActivity {
    private static final String TAG = DispatchActivity.class.getSimpleName();
    private static final String SESSION_REQUEST = "ccodk_session_request";
    public static final String WAS_EXTERNAL = "launch_from_external";
    public static final String WAS_SHORTCUT_LAUNCH = "launch_from_shortcut";
    public static final String START_FROM_LOGIN = "process_successful_login";
    public static final String EXECUTE_RECOVERY_MEASURES = "execute_recovery_measures";

    private static final int LOGIN_USER = 0;
    private static final int HOME_SCREEN = 1;
    public static final int INIT_APP = 2;
    public static final int RECOVERY_MEASURES = 3;


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
    private boolean needToExecuteRecoveryMeasures = false;

    private static final String EXTRA_CONSUMED_KEY = "shortcut_extra_was_consumed";
    private static final String KEY_APP_FILES_CHECK_OCCURRED = "check-for-changed-app-files-occurred";
    private static final String KEY_WAITING_FOR_ACTIVITY_RESULT = "waiting-for-login-activity-result";

    private boolean waitingForActivityResultFromLogin;

    boolean alreadyCheckedForAppFilesChange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (finishIfNotRoot()) {
            return;
        }

        if (savedInstanceState != null) {
            shortcutExtraWasConsumed = savedInstanceState.getBoolean(EXTRA_CONSUMED_KEY);
            alreadyCheckedForAppFilesChange = savedInstanceState.getBoolean(KEY_APP_FILES_CHECK_OCCURRED);
            waitingForActivityResultFromLogin = savedInstanceState.getBoolean(KEY_WAITING_FOR_ACTIVITY_RESULT);
        }
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
        outState.putBoolean(KEY_APP_FILES_CHECK_OCCURRED, alreadyCheckedForAppFilesChange);
        outState.putBoolean(KEY_WAITING_FOR_ACTIVITY_RESULT, waitingForActivityResultFromLogin);
    }

    private void checkForChangedCCZ() {
        alreadyCheckedForAppFilesChange = true;
        Intent i = new Intent(this, UpdateActivity.class);
        startActivity(i);
    }

    private void dispatch() {
        if (isDbInBadState()) {
            // appropriate error dialog has been triggered, don't continue w/ dispatch
            return;
        }

        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp == null) {
            if (MultipleAppsUtil.usableAppsPresent()) {
                AppUtils.initFirstUsableAppRecord();
                // Recurse in order to make the correct decision based on the new state
                dispatch();
            } else {
                Intent i = new Intent(getApplicationContext(), CommCareSetupActivity.class);
                this.startActivityForResult(i, INIT_APP);
            }
        } else {
            if (needToExecuteRecoveryMeasures) {
                needToExecuteRecoveryMeasures = false;
                startRecoveryExecutionActivity();
                return;
            }

            // Send this off at the earliest possible point where we know we have a seated app.
            // Result will be stored for later use
            RecoveryMeasuresHelper.requestRecoveryMeasures();

            // Note that the order in which these conditions are checked matters!!
            if (CommCareApplication.instance().isConsumerApp() && !alreadyCheckedForAppFilesChange) {
                checkForChangedCCZ();
                return;
            }

            ApplicationRecord currentRecord = currentApp.getAppRecord();
            try {
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
                } else if (!CommCareApplication.instance().getSession().isActive()) {
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
        int dbState = CommCareApplication.instance().getDatabaseState();
        if (dbState == CommCareApplication.STATE_LEGACY_DETECTED) {
            // Starting from CommCare 2.44, we don't supoort upgrading from Legacy DB
            CommCareLifecycleUtils.triggerHandledAppExit(this,
                    getString(R.string.legacy_failure),
                    getString(R.string.legacy_failure_title), false, false);
            return true;
        } else if (dbState == CommCareApplication.STATE_MIGRATION_FAILED) {
            CommCareLifecycleUtils.triggerHandledAppExit(this,
                    getString(R.string.migration_definite_failure),
                    getString(R.string.migration_failure_title), false, false);
            return true;
        } else if (dbState == CommCareApplication.STATE_MIGRATION_QUESTIONABLE) {
            CommCareLifecycleUtils.triggerHandledAppExit(this,
                    getString(R.string.migration_possible_failure),
                    getString(R.string.migration_failure_title), false, true);
            return true;
        } else if (dbState == CommCareApplication.STATE_CORRUPTED) {
            handleDamagedApp();
            return true;
        }
        return false;
    }

    private void handleDamagedApp() {
        if (!CommCareApplication.instance().isStorageAvailable()) {
            createNoStorageDialog();
        } else {
            // See if we're logged in. If so, show recovery screen
            try {
                CommCareApplication.instance().getSession();
                Intent intent = new Intent(DispatchActivity.this, RecoveryActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (SessionUnavailableException e) {
                // Otherwise, log in first
                launchLoginScreen();
            }
        }
    }

    private  void startRecoveryExecutionActivity() {
        startActivityForResult(
                new Intent(this, ExecuteRecoveryMeasuresActivity.class),
                RECOVERY_MEASURES);
    }

    private void createNoStorageDialog() {
        CommCareLifecycleUtils.triggerHandledAppExit(this,
                Localization.get("app.storage.missing.message"),
                Localization.get("app.storage.missing.title"));
    }

    private void launchLoginScreen() {
        if (!waitingForActivityResultFromLogin) {
            // AMS 06/09/16: This check is needed due to what we believe is a bug in the Android platform
            Intent i = new Intent(this, LoginActivity.class);
            i.putExtra(LoginActivity.USER_TRIGGERED_LOGOUT, userTriggeredLogout);
            startActivityForResult(i, LOGIN_USER);
            waitingForActivityResultFromLogin = true;
        } else {
            Log.w(TAG,
                    "Login redirection bug occurred; DispatchActivity is attempting to launch " +
                            "a new LoginActivity while it is still waiting for a result from " +
                            "another one.");
        }
    }

    private void launchHomeScreen() {
        if (startFromLogin && UpdatePromptHelper.promptForUpdateIfNeeded(this)) {
            return;
        }
        Intent i;
        if (useRootMenuHomeActivity()) {
            i = new Intent(this, RootMenuHomeActivity.class);
            // Since we are entering a menu list, the session state will expect this later
            HomeScreenBaseActivity.addPendingDataExtra(i,
                    CommCareApplication.instance().getCurrentSessionWrapper().getSession());
        } else {
            i = new Intent(this, StandardHomeActivity.class);
        }
        i.putExtra(START_FROM_LOGIN, startFromLogin);
        i.putExtra(LoginActivity.LOGIN_MODE, lastLoginMode);
        i.putExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, userManuallyEnteredPasswordMode);
        startFromLogin = false;
        startActivityForResult(i, HOME_SCREEN);
    }

    public static boolean useRootMenuHomeActivity() {
        return DeveloperPreferences.useRootModuleMenuAsHomeScreen() ||
                CommCareApplication.instance().isConsumerApp();
    }

    /**
     * @param record the ApplicationRecord corresponding to the seated, unusable app
     * @return if the unusable app was unseated by this method
     */
    private boolean handleUnusableApp(ApplicationRecord record) {
        if (record.isArchived()) {
            // If the app is archived, unseat it and try to seat another one
            CommCareApplication.instance().unseat(record);
            AppUtils.initFirstUsableAppRecord();
            return true;
        } else {
            // This app has unvalidated MM
            if (MultipleAppsUtil.usableAppsPresent()) {
                // If there are other usable apps, unseat it and seat another one
                CommCareApplication.instance().unseat(record);
                AppUtils.initFirstUsableAppRecord();
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
        if (MultipleAppsUtil.shouldSeeMMVerification()) {
            Intent i = new Intent(this, CommCareVerificationActivity.class);
            this.startActivityForResult(i, MISSING_MEDIA_ACTIVITY);
        } else {
            // Means that there are no usable apps, but there are multiple apps who all don't have
            // MM verified -- show an error message and shut down
            CommCareLifecycleUtils.triggerHandledAppExit(this,
                    Localization.get("multiple.apps.unverified.message"),
                    Localization.get("multiple.apps.unverified.title"));
        }
    }

    private void handleExternalLaunch() {
        //First off, make sure the incoming session is clear
        CommCareApplication.instance().getSession().proceedWithSavedSessionIfNeeded(() -> {
                    String sessionRequest = this.getIntent().getStringExtra(SESSION_REQUEST);
                    SessionStateDescriptor ssd = new SessionStateDescriptor();
                    ssd.fromBundle(sessionRequest);
                    CommCareApplication.instance().getCurrentSessionWrapper().loadFromStateDescription(ssd);
                    Intent i = new Intent(this, StandardHomeActivity.class);
                    i.putExtra(WAS_EXTERNAL, true);
                    startActivityForResult(i, HOME_SCREEN);
        }
        );
    }

    private void handleShortcutLaunch() {
        if (!triggerLoginIfNeeded()) {
            //We were launched in shortcut mode. Get the command and load us up.
            CommCareApplication.instance().getCurrentSession().setCommand(
                    this.getIntent().getStringExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT));

            getIntent().removeExtra(AndroidShortcuts.EXTRA_KEY_SHORTCUT);
            shortcutExtraWasConsumed = true;
            Intent i = new Intent(this, StandardHomeActivity.class);
            i.putExtra(WAS_SHORTCUT_LAUNCH, true);
            startActivityForResult(i, HOME_SCREEN);
        }
    }

    private boolean triggerLoginIfNeeded() {
        try {
            if (!CommCareApplication.instance().getSession().isActive()) {
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
        if (intent != null && intent.getBooleanExtra(EXECUTE_RECOVERY_MEASURES, false)) {
            this.needToExecuteRecoveryMeasures = true;
        }

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
                } else if (resultCode == RESULT_OK && !CommCareApplication.instance().isConsumerApp()) {
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
            case RECOVERY_MEASURES:
                RecoveryMeasuresHelper.handleExecutionActivityResult(this, intent);
                return;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }
}

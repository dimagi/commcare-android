package org.commcare.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Toast;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.BuildConfig;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.interfaces.WithUIController;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.models.database.user.DemoUserBuilder;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.suite.model.UserRestore;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.tasks.ManageKeyRecordTask;
import org.commcare.tasks.PullTaskReceiver;
import org.commcare.tasks.ResultAndError;

import org.commcare.utils.ACRAUtil;
import org.commcare.utils.ConsumerAppsUtil;
import org.commcare.utils.Permissions;
import org.commcare.views.ViewUtil;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;

/**
 * @author ctsims
 */
public class LoginActivity extends CommCareActivity<LoginActivity>
        implements OnItemSelectedListener, DataPullController,
        RuntimePermissionRequester, WithUIController, PullTaskReceiver {

    private static final String TAG = LoginActivity.class.getSimpleName();

    public static final int MENU_DEMO = Menu.FIRST;
    private static final int MENU_ABOUT = Menu.FIRST + 1;
    private static final int MENU_PERMISSIONS = Menu.FIRST + 2;
    private static final int MENU_PASSWORD_MODE = Menu.FIRST + 3;
    private static final int MENU_APP_MANAGER = Menu.FIRST + 4;

    public static final String NOTIFICATION_MESSAGE_LOGIN = "login_message";
    public final static String KEY_LAST_APP = "id-last-seated-app";
    public final static String KEY_ENTERED_USER = "entered-username";
    public final static String KEY_ENTERED_PW_OR_PIN = "entered-password-or-pin";

    private static final int SEAT_APP_ACTIVITY = 0;
    public final static String USER_TRIGGERED_LOGOUT = "user-triggered-logout";

    public final static String LOGIN_MODE = "login-mode";
    public final static String MANUAL_SWITCH_TO_PW_MODE = "manually-swithced-to-password-mode";

    private static final int TASK_KEY_EXCHANGE = 1;
    private static final int TASK_UPGRADE_INSTALL = 2;

    private final ArrayList<String> appIdDropdownList = new ArrayList<>();

    private String usernameBeforeRotation;
    private String passwordOrPinBeforeRotation;

    private LoginActivityUIController uiController;
    private FormAndDataSyncer formAndDataSyncer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (shouldFinish()) {
            // If we're going to finish in onResume() because there is no usable seated app,
            // don't bother with all of the setup here
            return;
        }

        uiController.setupUI();
        formAndDataSyncer = new FormAndDataSyncer();

        if (savedInstanceState == null) {
            // Only restore last user on the initial creation
            uiController.restoreLastUser();
        } else {
            // If the screen was rotated with entered text present, we will want to restore it
            // in onResume (can't do it here b/c will get overriden by logic in refreshForNewApp())
            usernameBeforeRotation = savedInstanceState.getString(KEY_ENTERED_USER);
            passwordOrPinBeforeRotation = savedInstanceState.getString(KEY_ENTERED_PW_OR_PIN);
        }

        Permissions.acquireAllAppPermissions(this, this, Permissions.ALL_PERMISSIONS_REQUEST);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void requestNeededPermissions(int requestCode) {
        ActivityCompat.requestPermissions(this, Permissions.getAppPermissions(),
                requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        String[] requiredPerms = Permissions.getRequiredPerms();

        if (requestCode == Permissions.ALL_PERMISSIONS_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                for (String requiredPerm : requiredPerms) {
                    if (requiredPerm.equals(permissions[i]) &&
                            grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        uiController.setPermissionDeniedState();
                        return;
                    }
                }
            }
        }
        uiController.setPermissionsGrantedState();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        String enteredUsername = uiController.getEnteredUsername();
        if (!"".equals(enteredUsername) && enteredUsername != null) {
            savedInstanceState.putString(KEY_ENTERED_USER, enteredUsername);
        }
        String enteredPasswordOrPin = uiController.getEnteredPasswordOrPin();
        if (!"".equals(enteredPasswordOrPin) && enteredPasswordOrPin != null) {
            savedInstanceState.putString(KEY_ENTERED_PW_OR_PIN, enteredPasswordOrPin);
        }
    }

    /**
     * @param restoreSession Indicates if CommCare should attempt to restore the saved session
     *                       upon successful login
     */
    protected void initiateLoginAttempt(boolean restoreSession) {
        LoginMode loginMode = uiController.getLoginMode();

        if ("".equals(uiController.getEnteredPasswordOrPin()) &&
                loginMode != LoginMode.PRIMED) {
            if (loginMode == LoginMode.PASSWORD) {
                raiseLoginMessage(StockMessages.Auth_EmptyPassword, false);
            } else {
                raiseLoginMessage(StockMessages.Auth_EmptyPin, false);
            }
            return;
        }

        uiController.clearErrorMessage();
        ViewUtil.hideVirtualKeyboard(LoginActivity.this);

        if (loginMode == LoginMode.PASSWORD) {
            DevSessionRestorer.tryAutoLoginPasswordSave(uiController.getEnteredPasswordOrPin(), false);
        }

        if (ResourceInstallUtils.isUpdateReadyToInstall()) {
            // install update, which triggers login upon completion
            installPendingUpdate();
        } else {
            localLoginOrPullAndLogin(restoreSession);
        }
    }

    @Override
    public String getActivityTitle() {
        return null;
    }

    @Override
    public void startDataPull() {
        if (CommCareApplication._().isConsumerApp()) {
            formAndDataSyncer.performLocalRestore(this, getUniformUsername(), uiController.getEnteredPasswordOrPin());
        } else {
            formAndDataSyncer.performOtaRestore(this, getUniformUsername(), uiController.getEnteredPasswordOrPin());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (shouldFinish()) {
            return;
        }

        // Otherwise, refresh the activity for current conditions
        uiController.refreshView();
    }

    protected boolean checkForSeatedAppChange() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String lastSeatedId = prefs.getString(KEY_LAST_APP, "");
        String currentSeatedId = CommCareApplication._().getCurrentApp().getUniqueId();
        if (!lastSeatedId.equals(currentSeatedId)) {
            prefs.edit().putString(KEY_LAST_APP, currentSeatedId).commit();
            return true;
        }
        return false;
    }

    private static boolean shouldFinish() {
        CommCareApp currentApp = CommCareApplication._().getCurrentApp();
        return currentApp == null || !currentApp.getAppRecord().isUsable();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        // It is possible that we left off at the LoginActivity last time we were on the main CC
        // screen, but have since done something in the app manager to either leave no seated app
        // at all, or to render the seated app unusable. Redirect to dispatch activity if we
        // encounter either case
        if (shouldFinish()) {
            setResult(RESULT_OK);
            this.finish();
            return;
        }

        if (CommCareApplication._().isConsumerApp()) {
            uiController.setUsername(BuildConfig.CONSUMER_APP_USERNAME);
            uiController.setPasswordOrPin(BuildConfig.CONSUMER_APP_PASSWORD);
            localLoginOrPullAndLogin(false);
        } else {
            tryAutoLogin();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SEAT_APP_ACTIVITY && resultCode == RESULT_OK) {
            uiController.refreshForNewApp();
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void tryAutoLogin() {
        Pair<String, String> userAndPass =
                DevSessionRestorer.getAutoLoginCreds(forceAutoLogin());
        if (userAndPass != null) {
            uiController.setUsername(userAndPass.first);
            uiController.setPasswordOrPin(userAndPass.second);
            // If we're doing auto-login, means we're using a password so switch UI to pw mode
            uiController.setNormalPasswordMode();

            if (!getIntent().getBooleanExtra(USER_TRIGGERED_LOGOUT, false)) {
                // If we are attempting auto-login, assume that we want to restore a saved session
                initiateLoginAttempt(true);
            }
        }
    }

    private boolean forceAutoLogin() {
        return CommCareApplication._().checkPendingBuildRefresh();
    }

    private String getUniformUsername() {
        return uiController.getEnteredUsername().toLowerCase().trim();
    }

    private boolean tryLocalLogin(final boolean warnMultipleAccounts, boolean restoreSession) {
        //TODO: check username/password for emptiness
        return tryLocalLogin(getUniformUsername(), uiController.getEnteredPasswordOrPin(),
                warnMultipleAccounts, restoreSession, uiController.getLoginMode());
    }

    private boolean tryLocalLogin(final String username, String passwordOrPin,
                                  final boolean warnMultipleAccounts, final boolean restoreSession,
                                  LoginMode loginMode) {
        try {
            final boolean triggerMultipleUsersWarning = getMatchingUsersCount(username) > 1
                    && warnMultipleAccounts;

            ManageKeyRecordTask<LoginActivity> task =
                    new ManageKeyRecordTask<LoginActivity>(this, TASK_KEY_EXCHANGE, username,
                            passwordOrPin, loginMode,
                            CommCareApplication._().getCurrentApp(), restoreSession,
                            triggerMultipleUsersWarning) {

                        @Override
                        protected void deliverUpdate(LoginActivity receiver, String... update) {
                            receiver.updateProgress(update[0], TASK_KEY_EXCHANGE);
                        }

                    };

            task.connect(this);
            task.executeParallel();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private int getMatchingUsersCount(String username) {
        int count = 0;
        for (UserKeyRecord record : CommCareApplication._().getAppStorage(UserKeyRecord.class)) {
            if (record.getUsername().equals(username)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void dataPullCompleted() {
        ACRAUtil.registerUserData();
        CommCareApplication._().clearNotifications(NOTIFICATION_MESSAGE_LOGIN);

        Intent i = new Intent();
        i.putExtra(LOGIN_MODE, uiController.getLoginMode());
        i.putExtra(MANUAL_SWITCH_TO_PW_MODE, uiController.userManuallySwitchedToPasswordMode());
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_DEMO, 0, Localization.get("login.menu.demo")).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_ABOUT, 1, Localization.get("home.menu.about")).setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, MENU_PERMISSIONS, 1, Localization.get("permission.acquire.required")).setIcon(android.R.drawable.ic_menu_manage);
        menu.add(0, MENU_PASSWORD_MODE, 1, Localization.get("login.menu.password.mode"));
        menu.add(0, MENU_APP_MANAGER, 1, Localization.get("login.menu.app.manager"));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(MENU_PERMISSIONS).setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
        menu.findItem(MENU_PASSWORD_MODE).setVisible(uiController.getLoginMode() == LoginMode.PIN);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean otherResult = super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case MENU_DEMO:
                loginDemoUser();
                return true;
            case MENU_ABOUT:
                DialogCreationHelpers.buildAboutCommCareDialog(this).showNonPersistentDialog();
                return true;
            case MENU_PERMISSIONS:
                Permissions.acquireAllAppPermissions(this, this, Permissions.ALL_PERMISSIONS_REQUEST);
                return true;
            case MENU_PASSWORD_MODE:
                uiController.manualSwitchToPasswordMode();
                return true;
            case MENU_APP_MANAGER:
                Intent i = new Intent(this, AppManagerActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            default:
                return otherResult;
        }
    }

    private void loginDemoUser() {
        UserRestore userRestore = CommCareApplication._().getCommCarePlatform().getDemoUserRestore();
        if (userRestore != null) {
            formAndDataSyncer.performDemoUserRestore(this, userRestore);
        } else {
            DemoUserBuilder.build(this, CommCareApplication._().getCurrentApp());
            tryLocalLogin(DemoUserBuilder.DEMO_USERNAME, DemoUserBuilder.DEMO_PASSWORD, false,
                    false, LoginMode.PASSWORD);
        }
    }

    @Override
    public void raiseLoginMessageWithInfo(MessageTag messageTag, String additionalInfo, boolean showTop) {
        NotificationMessage message =
                NotificationMessageFactory.message(messageTag,
                        new String[]{null, null, additionalInfo},
                        NOTIFICATION_MESSAGE_LOGIN);
        raiseMessage(message, showTop);
    }

    @Override
    public void raiseLoginMessage(MessageTag messageTag, boolean showTop) {
        NotificationMessage message = NotificationMessageFactory.message(messageTag,
                NOTIFICATION_MESSAGE_LOGIN);
        raiseMessage(message, showTop);
    }

    @Override
    public void raiseMessage(NotificationMessage message, boolean showTop) {
        String toastText = message.getTitle();
        if (showTop) {
            CommCareApplication._().reportNotificationMessage(message);
            toastText = Localization.get("notification.for.details.wrapper",
                    new String[]{toastText});
        }
        uiController.setErrorMessageUI(toastText);
    }

    /**
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (CommCareApplication._().isConsumerApp()) {
            return ConsumerAppsUtil.getGenericConsumerAppsProgressDialog(taskId, false);
        }

        CustomProgressDialog dialog;
        switch (taskId) {
            case TASK_KEY_EXCHANGE:
                dialog = CustomProgressDialog.newInstance(Localization.get("key.manage.title"),
                        Localization.get("key.manage.start"), taskId);
                break;
            case DataPullTask.DATA_PULL_TASK_ID:
                dialog = CustomProgressDialog.newInstance(Localization.get("sync.communicating.title"),
                        Localization.get("sync.progress.starting"), taskId);
                dialog.addCancelButton();
                dialog.addProgressBar();
                break;
            case TASK_UPGRADE_INSTALL:
                dialog = CustomProgressDialog.newInstance(Localization.get("updates.installing.title"),
                        Localization.get("updates.installing.message"), taskId);
                break;
            default:
                Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                        + "any valid possibilities in LoginActivity");
                return null;
        }
        return dialog;
    }

    protected void restoreEnteredTextFromRotation() {
        if (usernameBeforeRotation != null) {
            uiController.setUsername(usernameBeforeRotation);
            usernameBeforeRotation = null;
        }
        if (passwordOrPinBeforeRotation != null) {
            uiController.setPasswordOrPin(passwordOrPinBeforeRotation);
            passwordOrPinBeforeRotation = null;
        }
    }

    protected void populateAppSpinner(ArrayList<ApplicationRecord> readyApps) {
        ArrayList<String> appNames = new ArrayList<>();
        appIdDropdownList.clear();
        for (ApplicationRecord r : readyApps) {
            appNames.add(r.getDisplayName());
            appIdDropdownList.add(r.getUniqueId());
        }

        // Want to set the spinner's selection to match whatever the currently seated app is
        String currAppId = CommCareApplication._().getCurrentApp().getUniqueId();
        int position = appIdDropdownList.indexOf(currAppId);
        uiController.setMultipleAppsUIState(appNames, position);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // Retrieve the app record corresponding to the app selected
        String appId = appIdDropdownList.get(position);

        boolean selectedNewApp = !appId.equals(CommCareApplication._().getCurrentApp().getUniqueId());
        if (selectedNewApp) {
            // Set the id of the last selected app
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString(KEY_LAST_APP, appId).commit();

            // Launch the activity to seat the new app
            Intent i = new Intent(this, SeatAppActivity.class);
            i.putExtra(SeatAppActivity.KEY_APP_TO_SEAT, appId);
            this.startActivityForResult(i, SEAT_APP_ACTIVITY);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /**
     * Block the user with a dialog while downloaded update is installed.
     */
    private void installPendingUpdate() {
        InstallStagedUpdateTask<LoginActivity> task =
                new InstallStagedUpdateTask<LoginActivity>(TASK_UPGRADE_INSTALL) {
                    @Override
                    protected void deliverResult(LoginActivity receiver,
                                                 AppInstallStatus result) {
                        if (result == AppInstallStatus.Installed) {
                            Toast.makeText(receiver,
                                    Localization.get("login.update.install.success"),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(result));
                        }

                        localLoginOrPullAndLogin(uiController.isRestoreSessionChecked());
                    }

                    @Override
                    protected void deliverUpdate(LoginActivity receiver,
                                                 int[]... update) {
                    }

                    @Override
                    protected void deliverError(LoginActivity receiver,
                                                Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "update installation on login failed: " + e.getMessage());
                        Toast.makeText(receiver,
                                Localization.get("login.update.install.failure"),
                                Toast.LENGTH_LONG).show();

                        localLoginOrPullAndLogin(uiController.isRestoreSessionChecked());
                    }
                };
        task.connect(this);
        task.executeParallel();
    }

    private void localLoginOrPullAndLogin(boolean restoreSession) {
        if (tryLocalLogin(false, restoreSession)) {
            return;
        }

        // If local login was not successful
        startDataPull();
    }

    @Override
    public void initUIController() {
        if (CommCareApplication._().isConsumerApp()) {
            uiController = new BlankLoginActivityUIController(this);
        } else {
            uiController = new LoginActivityUIController(this);
        }
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndErrorMessage, boolean userTriggeredSync, boolean formsToSend) {
        DataPullTask.PullTaskResult result = resultAndErrorMessage.data;
        if (result == null) {
            // The task crashed unexpectedly
            raiseLoginMessage(StockMessages.Restore_Unknown, true);
            return;
        }

        switch (result) {
            case AUTH_FAILED:
                raiseLoginMessage(StockMessages.Auth_BadCredentials, false);
                break;
            case BAD_DATA_REQUIRES_INTERVENTION:
                raiseLoginMessageWithInfo(StockMessages.Remote_BadRestoreRequiresIntervention, resultAndErrorMessage.errorMessage, true);
                break;
            case BAD_DATA:
                raiseLoginMessageWithInfo(StockMessages.Remote_BadRestore, resultAndErrorMessage.errorMessage, true);
                break;
            case STORAGE_FULL:
                raiseLoginMessage(StockMessages.Storage_Full, true);
                break;
            case DOWNLOAD_SUCCESS:
                if (!tryLocalLogin(true, uiController.isRestoreSessionChecked())) {
                    raiseLoginMessage(StockMessages.Auth_CredentialMismatch, true);
                }
                break;
            case UNREACHABLE_HOST:
                raiseLoginMessage(StockMessages.Remote_NoNetwork, true);
                break;
            case CONNECTION_TIMEOUT:
                raiseLoginMessage(StockMessages.Remote_Timeout, true);
                break;
            case SERVER_ERROR:
                raiseLoginMessage(StockMessages.Remote_ServerError, true);
                break;
            case UNKNOWN_FAILURE:
                raiseLoginMessageWithInfo(StockMessages.Restore_Unknown, resultAndErrorMessage.errorMessage, true);
                break;
        }
    }

    @Override
    public void handlePullTaskUpdate(Integer... update) {
        if (CommCareApplication._().isConsumerApp()) {
            return;
        }
        SyncUIHandling.handleSyncUpdate(this, update);
    }

    @Override
    public void handlePullTaskError() {
        raiseLoginMessage(StockMessages.Restore_Unknown, true);
    }
}

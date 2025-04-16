package org.commcare.activities;

import static org.commcare.activities.DispatchActivity.REDIRECT_TO_CONNECT_OPPORTUNITY_INFO;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.util.Pair;
import androidx.preference.PreferenceManager;
import androidx.work.WorkManager;

import com.scottyab.rootbeer.RootBeer;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.interfaces.WithUIController;
import org.commcare.models.database.user.DemoUserBuilder;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.recovery.measures.RecoveryMeasuresHelper;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.tasks.ManageKeyRecordTask;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.ConsumerAppsUtil;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.Permissions;
import org.commcare.utils.StringUtils;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.ViewUtil;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationActionButtonInfo;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.Date;

/**
 * @author ctsims
 */
public class LoginActivity extends CommCareActivity<LoginActivity>
        implements OnItemSelectedListener, DataPullController,
        RuntimePermissionRequester, WithUIController, PullTaskResultReceiver {

    public static final String EXTRA_APP_ID = "extra_app_id";
    private static final String TAG = LoginActivity.class.getSimpleName();

    public static final int MENU_DEMO = Menu.FIRST;
    private static final int MENU_ABOUT = Menu.FIRST + 1;
    private static final int MENU_PERMISSIONS = Menu.FIRST + 2;
    private static final int MENU_PASSWORD_MODE = Menu.FIRST + 3;
    private static final int MENU_APP_MANAGER = Menu.FIRST + 4;
    private static final int MENU_CONNECT_SIGN_IN = Menu.FIRST + 5;
    private static final int MENU_CONNECT_FORGET = Menu.FIRST + 6;
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
    private int selectedAppIndex = -1;
    private boolean appLaunchedFromConnect;
    private String presetAppId;
    public static final String CONNECTID_MANAGED_LOGIN = "connectid-managed-login";
    public static final String CONNECT_MANAGED_LOGIN = "connect-managed-login";
    private ConnectIDManager connectIDManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkManagedConfiguration();

        if (shouldFinish()) {
            // If we're going to finish in onResume() because there is no usable seated app,
            // don't bother with all of the setup here
            return;
        }

        uiController.setupUI();
        formAndDataSyncer = new FormAndDataSyncer();
        connectIDManager = ConnectIDManager.getInstance();

        connectIDManager.init(this);
        presetAppId = getIntent().getStringExtra(EXTRA_APP_ID);
        appLaunchedFromConnect = connectIDManager.wasAppLaunchedFromConnect(presetAppId);
        if (savedInstanceState == null) {
            // Only restore last user on the initial creation
            uiController.restoreLastUser();
        } else {
            // If the screen was rotated with entered text present, we will want to restore it
            // in onResume (can't do it here b/c will get overriden by logic in refreshForNewApp())
            usernameBeforeRotation = savedInstanceState.getString(KEY_ENTERED_USER);
            passwordOrPinBeforeRotation = savedInstanceState.getString(KEY_ENTERED_PW_OR_PIN);
        }

        if (!HiddenPreferences.allowRunOnRootedDevice()
                && new RootBeer(this).isRooted()) {
            new UserfacingErrorHandling<>().createErrorDialog(this,
                    StringUtils.getStringRobust(this, R.string.root_detected_message),
                    StringUtils.getStringRobust(this, R.string.root_detected_title),
                    true);
        } else {
            Permissions.acquireAllAppPermissions(this, this, Permissions.ALL_PERMISSIONS_REQUEST);
        }
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
        //See whether login is managed by ConnectID
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        String username = uiController.getEnteredUsername();

        if (appLaunchedFromConnect) {
            //Auto login
            doLogin(loginMode, restoreSession, "AUTO");
        } else if (uiController.loginManagedByConnectId()) {
            //Unlock and then auto login
            connectIDManager.unlockConnect(this, success -> {
                if (success) {
                    String pass = connectIDManager.getStoredPasswordForApp(seatedAppId, username);
                    doLogin(loginMode, restoreSession, pass);
                }
            });
        } else {
            //Manual login
            String passwordOrPin = uiController.getEnteredPasswordOrPin();
            doLogin(loginMode, restoreSession, passwordOrPin);
        }

    }

    @Override
    public String getActivityTitle() {
        return null;
    }

    private void doLogin(LoginMode loginMode, boolean restoreSession, String passwordOrPin) {
        if ("".equals(passwordOrPin) && loginMode != LoginMode.PRIMED) {
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
            DevSessionRestorer.tryAutoLoginPasswordSave(passwordOrPin, false);
        }

        if (ResourceInstallUtils.isUpdateReadyToInstall() && !UpdateActivity.isUpdateBlockedOnSync(
                uiController.getEnteredUsername())) {
            // install update, which triggers login upon completion
            installPendingUpdate();
        } else {
            localLoginOrPullAndLogin(restoreSession);
        }
    }

    @Override
    public void startDataPull(DataPullMode mode, String password) {
        switch (mode) {
            case CONSUMER_APP:
                formAndDataSyncer.performLocalRestore(this, getUniformUsername(),
                        uiController.getEnteredPasswordOrPin());
                break;
            case CCZ_DEMO:
                OfflineUserRestore offlineUserRestore = CommCareApplication.instance().getCommCarePlatform().getDemoUserRestore();
                uiController.setUsername(offlineUserRestore.getUsername());
                uiController.setPasswordOrPin(OfflineUserRestore.DEMO_USER_PASSWORD);
                formAndDataSyncer.performDemoUserRestore(this, offlineUserRestore);
                break;
            case NORMAL:
                formAndDataSyncer.performOtaRestore(this, getUniformUsername(), password);
                break;
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
        String currentSeatedId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        if (!lastSeatedId.equals(currentSeatedId)) {
            disableWorkForLastSeatedApp();
            prefs.edit().putString(KEY_LAST_APP, currentSeatedId).commit();
            return true;
        }
        return false;
    }


    // cancels all worker tasks for previously seated app
    private static void disableWorkForLastSeatedApp() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance());
        String lastSeatedId = prefs.getString(KEY_LAST_APP, "");
        if (!lastSeatedId.isEmpty()) {
            WorkManager.getInstance(CommCareApplication.instance()).cancelAllWorkByTag(lastSeatedId);
        }
    }

    private static boolean shouldFinish() {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
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

        if (RecoveryMeasuresHelper.recoveryMeasuresPending()) {
            Intent i = new Intent();
            i.putExtra(DispatchActivity.EXECUTE_RECOVERY_MEASURES, true);
            setResult(RESULT_OK, i);
            finish();
        } else if (CommCareApplication.instance().isConsumerApp()) {
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
            invalidateOptionsMenu();
            usernameBeforeRotation = passwordOrPinBeforeRotation = null;
        } else if (resultCode == ConnectConstants.LOGIN_CONNECT_LAUNCH_REQUEST_CODE) {
            connectIDManager.handleFinishedActivity(this, resultCode);
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
        return CommCareApplication.instance().checkPendingBuildRefresh();
    }

    private String getUniformUsername() {
        String username = uiController.getEnteredUsername();
        if (connectIDManager.isLoggedIN() && appLaunchedFromConnect) {
            username = connectIDManager.getConnectUsername(this);
        }
        return username.toLowerCase().trim();
    }

    private boolean tryLocalLogin(final boolean warnMultipleAccounts, boolean restoreSession,
                                  boolean blockRemoteKeyManagement) {
        //TODO: check username/password for emptiness
        return tryLocalLogin(getUniformUsername(), uiController.getEnteredPasswordOrPin(),
                warnMultipleAccounts, restoreSession, uiController.getLoginMode(),
                blockRemoteKeyManagement, DataPullMode.NORMAL);
    }

    private boolean tryLocalLogin(final String username, String passwordOrPin,
                                  final boolean warnMultipleAccounts, final boolean restoreSession,
                                  LoginMode loginMode, boolean blockRemoteKeyManagement,
                                  DataPullMode pullModeToUse) {
        try {
            final boolean triggerMultipleUsersWarning = getMatchingUsersCount(username) > 1
                    && warnMultipleAccounts;

            ManageKeyRecordTask<LoginActivity> task =
                    new ManageKeyRecordTask<LoginActivity>(this, TASK_KEY_EXCHANGE, username,
                            passwordOrPin, loginMode,
                            CommCareApplication.instance().getCurrentApp(), restoreSession,
                            triggerMultipleUsersWarning, blockRemoteKeyManagement, pullModeToUse) {

                        @Override
                        protected void deliverUpdate(LoginActivity receiver, String... update) {
                            receiver.updateProgress(update[0], TASK_KEY_EXCHANGE);
                        }

                    };

            task.connect(this);
            task.executeParallel();

            return true;
        } catch (Exception e) {
            Logger.exception("Error while local login", e);
            return false;
        }
    }

    private int getMatchingUsersCount(String username) {
        int count = 0;
        for (UserKeyRecord record : CommCareApplication.instance().getAppStorage(UserKeyRecord.class)) {
            if (record.getUsername().equals(username)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void dataPullCompleted() {
        CrashUtil.registerUserData();
        ViewUtil.hideVirtualKeyboard(LoginActivity.this);
        CommCareApplication.notificationManager().clearNotifications(NOTIFICATION_MESSAGE_LOGIN);
        if(handleConnectSignIn(this, getUniformUsername(),
                uiController.getEnteredPasswordOrPin())){
            setResultAndFinish(false);
        }
    }

    /**
     * Handles sign in related ops for Connect
     * @param context Android activity we are signing in from
     * @param username Username for user signing in
     * @param enteredPasswordPin user entered password or pin for non-connect apps
     * @return if we should finish after calling this method
     */
    private boolean handleConnectSignIn(CommCareActivity<?> context, String username, String enteredPasswordPin) {
        if (connectIDManager.isLoggedIN()) {
            connectIDManager.completeSignin();
            String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
            ConnectJobRecord job = connectIDManager.setConnectJobForApp(context, appId);

            if (job != null) {
                connectIDManager.updateAppAccess(context, appId, username);
                connectIDManager.updateJobProgress(context, job, success -> setResultAndFinish(job.getIsUserSuspended()));
            } else {
                //Possibly offer to link or de-link ConnectId-managed login
                connectIDManager.checkConnectIdLink(context,
                        uiController.loginManagedByConnectId(),
                        appId,
                        username,
                        enteredPasswordPin,
                        success -> {
                            connectIDManager.updateAppAccess(context, appId, username);
                            setResultAndFinish(success);
                        }
                );
            }
            return false;
        }

        return true;
    }



    private void setResultAndFinish(boolean navigateToConnectJobs) {
        Intent i = new Intent();
        i.putExtra(REDIRECT_TO_CONNECT_OPPORTUNITY_INFO, navigateToConnectJobs);
        i.putExtra(LOGIN_MODE, uiController.getLoginMode());
        i.putExtra(MANUAL_SWITCH_TO_PW_MODE, uiController.userManuallySwitchedToPasswordMode());
        i.putExtra(CONNECTID_MANAGED_LOGIN, appLaunchedFromConnect || uiController.loginManagedByConnectId());
        i.putExtra(CONNECT_MANAGED_LOGIN, appLaunchedFromConnect);
        setResult(RESULT_OK, i);
        finish();
    }

    public void handleConnectButtonPress() {
        selectedAppIndex = -1;
        connectIDManager.unlockConnect(this, success -> {
            if(success) {
                connectIDManager.goToConnectJobsList(this);
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private void handleFailedConnectSignIn() {
        if (uiController.loginManagedByConnectId()) {
            ApplicationRecord record = CommCareApplication.instance().getCurrentApp().getAppRecord();
            ConnectIDManager.ConnectAppMangement appState = connectIDManager.evaluateAppState(this,
                    record.getUniqueId(), getUniformUsername());
            switch (appState) {
                case Connect -> {
                    FirebaseAnalyticsUtil.reportCccAppFailedAutoLogin(record.getApplicationId());
                }
                case ConnectId -> {
                    uiController.setErrorMessageUI(getString(R.string.failed_to_login_with_connectid), false);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_DEMO, 0, Localization.get("login.menu.demo")).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_ABOUT, 1, Localization.get("home.menu.about")).setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, MENU_PERMISSIONS, 1, Localization.get("permission.acquire.required")).setIcon(android.R.drawable.ic_menu_manage);
        menu.add(0, MENU_PASSWORD_MODE, 1, Localization.get("login.menu.password.mode"));
        menu.add(0, MENU_APP_MANAGER, 1, Localization.get("login.menu.app.manager"));
        menu.add(0, MENU_CONNECT_SIGN_IN, 1, getString(R.string.login_menu_connect_sign_in));
        menu.add(0, MENU_CONNECT_FORGET, 1, getString(R.string.login_menu_connect_forget));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(MENU_PERMISSIONS).setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
        menu.findItem(MENU_PASSWORD_MODE).setVisible(uiController.getLoginMode() == LoginMode.PIN);
        menu.findItem(MENU_CONNECT_SIGN_IN).setVisible(!connectIDManager.isLoggedIN());
        menu.findItem(MENU_CONNECT_FORGET).setVisible(connectIDManager.isLoggedIN());
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
            case MENU_CONNECT_SIGN_IN:
                registerConnectIdUser();
                return true;
            case MENU_CONNECT_FORGET:
                connectIDManager.forgetUser(AnalyticsParamValue.CCC_FORGOT_USER_LOGIN_PAGE);
                uiController.setPasswordOrPin("");
                uiController.refreshView();
                uiController.setConnectIdLoginState(ConnectIDManager.ConnectAppMangement.Unmanaged);
                return true;
            default:
                return otherResult;
        }
    }

    private void loginDemoUser() {
        OfflineUserRestore offlineUserRestore = CommCareApplication.instance().getCommCarePlatform().getDemoUserRestore();
        FirebaseAnalyticsUtil.reportPracticeModeUsage(offlineUserRestore);

        if (offlineUserRestore != null) {
            tryLocalLogin(offlineUserRestore.getUsername(), OfflineUserRestore.DEMO_USER_PASSWORD,
                    false, false, LoginMode.PASSWORD, true, DataPullMode.CCZ_DEMO);
        } else {
            DemoUserBuilder.build(this, CommCareApplication.instance().getCurrentApp());
            tryLocalLogin(DemoUserBuilder.DEMO_USERNAME, DemoUserBuilder.DEMO_PASSWORD, false,
                    false, LoginMode.PASSWORD, false, DataPullMode.NORMAL);
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

    public void raiseLoginMessage(MessageTag messageTag, boolean showTop) {
        raiseLoginMessage(messageTag, showTop, NotificationActionButtonInfo.ButtonAction.NONE);
    }

    @Override
    public void raiseLoginMessage(MessageTag messageTag, boolean showTop, NotificationActionButtonInfo.ButtonAction buttonAction) {
        NotificationMessage message = NotificationMessageFactory.message(messageTag,
                NOTIFICATION_MESSAGE_LOGIN, buttonAction);
        raiseMessage(message, showTop);
    }

    @Override
    public void raiseMessage(NotificationMessage message, boolean showTop) {
        String toastText = message.getTitle();
        if (showTop) {
            CommCareApplication.notificationManager().reportNotificationMessage(message);
        }
        uiController.setErrorMessageUI(toastText, showTop);
        handleFailedConnectSignIn();

    }

    /**
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (CommCareApplication.instance().isConsumerApp()) {
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
        String currAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        int position = 0;
        if (selectedAppIndex >= 0) {
            position = selectedAppIndex;

            if (position >= appNames.size()) {
                //Special case when user forgets ConnectID account and last app in the list is selected
                position = appNames.size() - 1;
            }
        } else {
            position = appIdDropdownList.indexOf(currAppId);
        }

        uiController.setMultipleAppsUiState(appNames, position);
        selectedAppIndex = -1;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // Retrieve the app record corresponding to the app selected
        selectedAppIndex = position;
        String appId = appIdDropdownList.get(selectedAppIndex);
        seatAppIfNeeded(appId);
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
                            UpdateActivity.OnSuccessfulUpdate(false, false);
                        } else {
                            CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(result));
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
                        Logger.exception("Auto update on login failed", e);
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
        if (tryLocalLogin(false, restoreSession, false)) {
            return;
        }

        // If local login was not successful
        startDataPull(CommCareApplication.instance().isConsumerApp() ? DataPullMode.CONSUMER_APP : DataPullMode.NORMAL,
                uiController.getEnteredPasswordOrPin());
    }

    @Override
    public void initUIController() {
        if (CommCareApplication.instance().isConsumerApp()) {
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
    public boolean isBackEnabled() {
        return false;
    }

    @Override
    public void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndErrorMessage,
                                     boolean userTriggeredSync, boolean formsToSend,
                                     boolean usingRemoteKeyManagement) {
        DataPullTask.PullTaskResult result = resultAndErrorMessage.data;
        if (result == null) {
            // The task crashed unexpectedly
            raiseLoginMessage(StockMessages.Restore_Unknown, true);
            return;
        }

        switch (result) {
            case EMPTY_URL:
                raiseLoginMessage(StockMessages.Empty_Url, true);
                break;
            case AUTH_FAILED:
                if (connectIDManager.isSeatedAppLinkedToConnectId(uiController.getEnteredUsername())) {
                    Logger.exception("Token auth error for connect managed app",
                            new Throwable("Token Auth failed during login for a ConnectID managed app"));
                }
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
                if (!tryLocalLogin(true, uiController.isRestoreSessionChecked(), !usingRemoteKeyManagement)) {
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
            case RATE_LIMITED_SERVER_ERROR:
                raiseLoginMessage(StockMessages.Remote_RateLimitedServerError, true);
                break;
            case UNKNOWN_FAILURE:
                raiseLoginMessageWithInfo(StockMessages.Restore_Unknown, resultAndErrorMessage.errorMessage, true);
                break;
            case CANCELLED:
                raiseLoginMessage(StockMessages.Cancelled, true);
                break;
            case ENCRYPTION_FAILURE:
                raiseLoginMessageWithInfo(StockMessages.Encryption_Error, resultAndErrorMessage.errorMessage, true);
                break;
            case SESSION_EXPIRE:
                raiseLoginMessage(StockMessages.Session_Expire, true);
                break;
            case RECOVERY_FAILURE:
                raiseLoginMessageWithInfo(StockMessages.Recovery_Error, resultAndErrorMessage.errorMessage, true);
                break;
            case ACTIONABLE_FAILURE:
                NotificationMessage message = new NotificationMessage(NOTIFICATION_MESSAGE_LOGIN,
                        resultAndErrorMessage.errorMessage, "", null, new Date());
                raiseMessage(message, true);
                break;
            case AUTH_OVER_HTTP:
                raiseLoginMessage(StockMessages.Auth_Over_HTTP, true);
                break;
            case CAPTIVE_PORTAL:
                raiseLoginMessage(StockMessages.Sync_CaptivePortal, true);
                break;
        }
    }

    @Override
    public void handlePullTaskUpdate(Integer... update) {
        if (CommCareApplication.instance().isConsumerApp()) {
            return;
        }
        SyncCapableCommCareActivity.handleSyncUpdate(this, update);
    }

    @Override
    public void handlePullTaskError() {
        raiseLoginMessage(StockMessages.Restore_Unknown, true);
    }

    private void checkManagedConfiguration() {
        // Check for managed configuration
        RestrictionsManager restrictionsManager =
                (RestrictionsManager)getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsManager == null) {
            return;
        }
        Bundle appRestrictions = restrictionsManager.getApplicationRestrictions();
        if (appRestrictions != null && appRestrictions.containsKey("username") &&
                appRestrictions.containsKey("password")) {
            uiController.setUsername(appRestrictions.getString("username"));
            uiController.setPasswordOrPin(appRestrictions.getString("password"));
            initiateLoginAttempt(false);
        }
    }

    protected String getPresetAppID() {
        return presetAppId;
    }

    private void registerConnectIdUser() {
        connectIDManager.launchConnectId(this, ConnectConstants.LOGIN_CONNECT_LAUNCH_REQUEST_CODE);
    }

    protected boolean seatAppIfNeeded(String appId) {
        boolean selectedNewApp = !appId.equals(CommCareApplication.instance().getCurrentApp().getUniqueId());
        if (selectedNewApp) {
            // Set the id of the last selected app
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putString(KEY_LAST_APP, appId).commit();

            // Launch the activity to seat the new app
            Intent i = new Intent(this, SeatAppActivity.class);
            i.putExtra(SeatAppActivity.KEY_APP_TO_SEAT, appId);
            this.startActivityForResult(i, SEAT_APP_ACTIVITY);
        }
        return selectedNewApp;
    }

    protected void evaluateConnectAppState() {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectIDManager.ConnectAppMangement appState = connectIDManager.evaluateAppState(this,
                seatedAppId, uiController.getEnteredUsername());

        if (appLaunchedFromConnect && presetAppId != null) {
            appState = ConnectIDManager.ConnectAppMangement.Connect;
            if (!seatAppIfNeeded(presetAppId)) {
                initiateLoginAttempt(uiController.isRestoreSessionChecked());
            }
        }

        if (appState == ConnectIDManager.ConnectAppMangement.ConnectId) {
            int selectorIndex = uiController.getSelectedAppIndex();
            String selectedAppId = !appIdDropdownList.isEmpty() ? appIdDropdownList.get(selectorIndex) : "";
            if (uiController.isAppSelectorVisible() && !selectedAppId.equals(seatedAppId)) {
                appState = ConnectIDManager.ConnectAppMangement.Unmanaged;
            }
        }

        uiController.setConnectIdLoginState(appState);
    }

}

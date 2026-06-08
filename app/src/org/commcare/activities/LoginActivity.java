package org.commcare.activities;

import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import org.commcare.activities.DataPullController.DataPullMode;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectNavHelper;
import org.commcare.connect.PersonalIdManager;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.interfaces.WithUIController;
import org.commcare.login.AuthSource;
import org.commcare.login.LoginController;
import org.commcare.login.LoginError;
import org.commcare.login.LoginPhase;
import org.commcare.login.LoginProgress;
import org.commcare.login.LoginProgressListener;
import org.commcare.login.LoginRequest;
import org.commcare.login.LoginResult;
import org.commcare.models.database.user.DemoUserBuilder;
import org.commcare.navdrawer.BaseDrawerActivity;
import org.commcare.navdrawer.BaseDrawerController;
import org.commcare.personalId.PersonalIdUnlocker;
import org.commcare.personalId.UnlockPolicy;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.recovery.measures.RecoveryMeasuresHelper;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.utils.ConsumerAppsUtil;
import org.commcare.utils.Permissions;
import org.commcare.utils.StringUtils;
import org.commcare.views.UserfacingErrorHandling;
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
import java.util.Map;

import static org.commcare.activities.DispatchActivity.REDIRECT_TO_CONNECT_OPPORTUNITY_INFO;
import static org.commcare.connect.ConnectAppUtils.IS_LAUNCH_FROM_CONNECT;
import static org.commcare.connect.ConnectConstants.CONNECT_MANAGED_LOGIN;
import static org.commcare.connect.ConnectConstants.PERSONALID_MANAGED_LOGIN;
import static org.commcare.connect.PersonalIdManager.ConnectAppMangement.Connect;
import static org.commcare.connect.PersonalIdManager.ConnectAppMangement.PersonalId;
import static org.commcare.connect.PersonalIdManager.ConnectAppMangement.Unmanaged;
import static org.commcare.utils.KeyboardHelper.hideVirtualKeyboard;

/**
 * @author ctsims
 */
public class LoginActivity extends BaseDrawerActivity<LoginActivity>
        implements OnItemSelectedListener, RuntimePermissionRequester, WithUIController {

    public static final String EXTRA_APP_ID = "extra_app_id";
    public static final String EXTRA_FORCE_SINGLE_APP_MODE = "extra_force_single_app_mode";
    private static final String TAG = LoginActivity.class.getSimpleName();

    public static final int MENU_PRACTICE_MODE = Menu.FIRST;
    private static final int MENU_ABOUT_COMMCARE = Menu.FIRST + 1;
    private static final int MENU_ACQUIRE_PERMISSIONS = Menu.FIRST + 2;
    private static final int MENU_FORGOT_PIN = Menu.FIRST + 3;
    private static final int MENU_APP_MANAGER = Menu.FIRST + 4;
    private static final int MENU_PERSONAL_ID_SIGN_IN = Menu.FIRST + 5;
    private static final int MENU_PERSONAL_ID_FORGET = Menu.FIRST + 6;
    public static final String NOTIFICATION_MESSAGE_LOGIN = "login_message";
    public static final String KEY_LAST_APP = "id-last-seated-app";
    public static final String KEY_ENTERED_USER = "entered-username";
    public static final String KEY_ENTERED_PW_OR_PIN = "entered-password-or-pin";

    private static final int SEAT_APP_ACTIVITY = 0;
    public static final String USER_TRIGGERED_LOGOUT = "user-triggered-logout";

    public static final String LOGIN_MODE = "login-mode";
    public static final String MANUAL_SWITCH_TO_PW_MODE = "manually-swithced-to-password-mode";

    private static final int TASK_KEY_EXCHANGE = 1;
    private static final int TASK_UPGRADE_INSTALL = 2;

    private final ArrayList<String> appIdDropdownList = new ArrayList<>();

    private String usernameBeforeRotation;
    private String passwordOrPinBeforeRotation;

    private LoginActivityUIController uiController;
    private int selectedAppIndex = -1;
    private boolean appLaunchedFromConnect = false;

    /**
     * This lets us launch CommCare in a single app mode from external applications
     * and should only be used internally when we don't want user to have the option to switch to other apps
     * (e.g. for launch from Connect Opportunities)
     */
    private String presetAppId;
    private PersonalIdManager personalIdManager;
    private PersonalIdManager.ConnectAppMangement connectAppState = Unmanaged;
    private boolean connectLaunchPerformed;
    private Map<Integer, String> menuIdToAnalyticsParam;

    private LoginPhase currentLoginPhase;

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
        initPersonaIdManager();
        presetAppId = getIntent().getStringExtra(EXTRA_APP_ID);
        appLaunchedFromConnect = getIntent().getBooleanExtra(IS_LAUNCH_FROM_CONNECT, false);
        connectLaunchPerformed = false;
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
            new UserfacingErrorHandling<>().createErrorDialog(
                    this,
                    StringUtils.getStringRobust(this, R.string.root_detected_message),
                    StringUtils.getStringRobust(this, R.string.root_detected_title),
                    true
            );
        } else {
            Permissions.acquireAllAppPermissions(this, this, Permissions.ALL_PERMISSIONS_REQUEST);
        }
    }

    private void initPersonaIdManager() {
        if (personalIdManager == null) {
            personalIdManager = PersonalIdManager.getInstance();
            personalIdManager.init(this);
        }
    }

    private boolean shouldDoConnectLogin() {
        return appLaunchedFromConnect && !connectLaunchPerformed;
    }

    @Override
    public void requestNeededPermissions(int requestCode) {
        ActivityCompat.requestPermissions(
                this,
                Permissions.getAppPermissions(),
                requestCode
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
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
        if (appLaunchedFromConnect) {
            connectLaunchPerformed = true;
            //Auto login
            doLogin(loginMode, restoreSession, "AUTO");
        } else if (loginManagedByPersonalId()) {
            //Unlock and then auto login
            PersonalIdUnlocker.INSTANCE.unlock(
                    this, UnlockPolicy.ALWAYS, success -> {
                        if (success) {
                            String username = uiController.getEnteredUsername();
                            String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                            String pass = personalIdManager.getStoredPasswordForApp(
                                    seatedAppId,
                                    username
                            );
                            doLogin(loginMode, restoreSession, pass);
                        }
                    }
            );
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
        if (!isUsernameValid(getUniformUsername())) {
            raiseLoginMessage(StockMessages.Auth_BadCredentials, false);
            return;
        }

        if ("".equals(passwordOrPin) && loginMode != LoginMode.PRIMED) {
            if (loginMode == LoginMode.PASSWORD) {
                raiseLoginMessage(StockMessages.Auth_EmptyPassword, false);
            } else {
                raiseLoginMessage(StockMessages.Auth_EmptyPin, false);
            }
            return;
        }

        uiController.clearErrorMessage();
        hideVirtualKeyboard(LoginActivity.this);

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

    private void runLoginPipeline(
            String username,
            String passwordOrPin,
            LoginMode loginMode,
            AuthSource authSource,
            boolean restoreSession,
            boolean blockRemoteKeyManagement,
            DataPullMode dataPullMode
    ) {
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        LoginRequest request = new LoginRequest(
                appId,
                username,
                passwordOrPin,
                loginMode,
                authSource,
                restoreSession,
                getMatchingUsersCount(username) > 1,
                blockRemoteKeyManagement,
                dataPullMode
        );

        LoginController controller = new LoginController(this);
        controller.start(this, request, createLoginProgressListener(), this::handleLoginResult);
    }

    private LoginProgressListener createLoginProgressListener() {
        return progress -> runOnUiThread(() -> updateLoginProgressUi(progress));
    }

    private void updateLoginProgressUi(LoginProgress progress) {
        LoginPhase phase = progress.getPhase();
        int taskId = taskIdForPhase(phase);

        if (phase != currentLoginPhase) {
            dismissLoginProgressDialog();
            currentLoginPhase = phase;
            showProgressDialog(taskId);
        }

        if (progress.getMessage() != null) {
            updateProgress(progress.getMessage(), taskId);
        }

        Integer percent = progress.getPercent();
        if (percent != null) {
            updateProgressBar(percent, 100, taskId);
        }
    }

    private int taskIdForPhase(LoginPhase phase) {
        if (phase == LoginPhase.Syncing) {
            return DataPullTask.DATA_PULL_TASK_ID;
        }

        return TASK_KEY_EXCHANGE;
    }

    private void dismissLoginProgressDialog() {
        if (currentLoginPhase != null) {
            dismissProgressDialogForTask(taskIdForPhase(currentLoginPhase));
            currentLoginPhase = null;
        }
    }

    private AuthSource determineAuthSource() {
        if (appLaunchedFromConnect || (personalIdManager.isloggedIn() && loginManagedByPersonalId())) {
            return AuthSource.PersonalId;
        }

        if (uiController.getLoginMode() == LoginMode.PRIMED) {
            return AuthSource.MdmManaged;
        }

        return AuthSource.Manual;
    }

    private boolean isUsernameValid(String username) {
        return (username != null && !username.isEmpty()) &&
                (!username.contains("@") || username.endsWith("@" + HiddenPreferences.getUserDomain()));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (shouldFinish()) {
            return;
        }

        // Otherwise, refresh the activity for current conditions
        selectedAppIndex = -1;
        uiController.refreshView();

        // if the app is already seated, we can login immediately
        if (isAppSeated(presetAppId)) {
            if (shouldDoConnectLogin() || loginManagedByPersonalId()) {
                initiateLoginAttempt(uiController.isRestoreSessionChecked());
            }
        }
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
        } else if (requestCode == ConnectConstants.PERSONAL_ID_SIGN_UP_LAUNCH) {
            personalIdManager.handleFinishedActivity(this, resultCode);
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
        if (personalIdManager.isloggedIn() && appLaunchedFromConnect) {
            username = personalIdManager.getConnectUsername(this);
        }
        return username.toLowerCase().trim();
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

    private void setLoginResultAndFinish(
            LoginMode loginMode,
            boolean navigateToConnectJobs,
            boolean personalIdManagedLoginExtra,
            boolean connectManagedLoginExtra
    ) {
        hideVirtualKeyboard(LoginActivity.this);
        Intent i = new Intent();
        i.putExtra(REDIRECT_TO_CONNECT_OPPORTUNITY_INFO, navigateToConnectJobs);
        i.putExtra(LOGIN_MODE, loginMode);
        i.putExtra(MANUAL_SWITCH_TO_PW_MODE, uiController.userManuallySwitchedToPasswordMode());
        i.putExtra(PERSONALID_MANAGED_LOGIN, personalIdManagedLoginExtra);
        i.putExtra(CONNECT_MANAGED_LOGIN, connectManagedLoginExtra);
        setResult(RESULT_OK, i);
        finish();
    }

    private void handleLoginResult(LoginResult result) {
        dismissLoginProgressDialog();

        if (result instanceof LoginResult.Success) {
            onLoginSuccess((LoginResult.Success) result);
        } else {
            onLoginError(((LoginResult.Failed) result).getError());
        }
    }

    private void onLoginSuccess(LoginResult.Success success) {
        if (success.getPostLoginOutcome().getNeedsPersonalIdLinkCheck()) {
            personalIdManager.checkPersonalIdLink(
                    this,
                    success.getPersonalIdManagedLogin(),
                    success.getAppId(),
                    success.getUsername(),
                    success.getLinkPassword(),
                    linkSuccess -> finishWithSuccess(success, linkSuccess)
            );

            return;
        }

        finishWithSuccess(
                success,
                success.getPostLoginOutcome().getRedirectToConnectOpportunityInfo()
        );
    }

    private void finishWithSuccess(LoginResult.Success success, boolean navigateToConnectJobs) {
        setLoginResultAndFinish(
                success.getLoginMode(),
                navigateToConnectJobs,
                success.getPersonalIdManagedLogin(),
                appLaunchedFromConnect
        );
    }

    private void onLoginError(LoginError error) {
        if (error instanceof LoginError.BadCredentials) {
            raiseLoginMessage(StockMessages.Auth_BadCredentials, false);
        } else if (error instanceof LoginError.TokenDenied) {
            raiseLoginMessage(StockMessages.TokenDenied, false);
        } else if (error instanceof LoginError.NetworkUnavailable) {
            raiseLoginMessage(StockMessages.Remote_NoNetwork, true);
        } else if (error instanceof LoginError.AuthOverHttpBlocked) {
            raiseLoginMessage(StockMessages.Auth_Over_HTTP, true);
        } else if (error instanceof LoginError.BadData badData) {
            raiseLoginMessageWithInfo(StockMessages.Remote_BadRestore, badData.getMessage(), true);
        } else if (error instanceof LoginError.BadDataRequiresIntervention badDataRequiresIntervention) {
            raiseLoginMessageWithInfo(
                    StockMessages.Remote_BadRestoreRequiresIntervention,
                    badDataRequiresIntervention.getMessage(),
                    true
            );
        } else if (error instanceof LoginError.BadResponse) {
            raiseLoginMessage(StockMessages.Remote_BadRestore, true);
        } else if (error instanceof LoginError.BadSslCertificate) {
            raiseLoginMessage(
                    StockMessages.BadSslCertificate,
                    true,
                    NotificationActionButtonInfo.ButtonAction.LAUNCH_DATE_SETTINGS
            );
        } else if (error instanceof LoginError.StorageFull) {
            raiseLoginMessage(StockMessages.Storage_Full, true);
        } else if (error instanceof LoginError.ServerError) {
            raiseLoginMessage(StockMessages.Remote_ServerError, true);
        } else if (error instanceof LoginError.RateLimitedServerError) {
            raiseLoginMessage(StockMessages.Remote_RateLimitedServerError, true);
        } else if (error instanceof LoginError.EncryptionFailure encryptionFailure) {
            raiseLoginMessageWithInfo(
                    StockMessages.Encryption_Error,
                    encryptionFailure.getMessage(),
                    true
            );
        } else if (error instanceof LoginError.RecoveryFailure recoveryFailure) {
            raiseLoginMessageWithInfo(
                    StockMessages.Recovery_Error,
                    recoveryFailure.getMessage(),
                    true
            );
        } else if (error instanceof LoginError.ActionableFailure actionableFailure) {
            raiseMessage(
                    new NotificationMessage(
                            NOTIFICATION_MESSAGE_LOGIN,
                            actionableFailure.getMessage(),
                            "",
                            null,
                            new Date()
                    ),
                    true
            );
        } else if (error instanceof LoginError.SessionExpire) {
            raiseLoginMessage(StockMessages.Session_Expire, true);
        } else if (error instanceof LoginError.Cancelled) {
            raiseLoginMessage(StockMessages.Cancelled, true);
        } else if (error instanceof LoginError.EmptyUrl) {
            raiseLoginMessage(StockMessages.Empty_Url, true);
        } else if (error instanceof LoginError.InsufficientRolePermission) {
            raiseLoginMessage(StockMessages.Auth_InsufficientRolePermission, true);
        } else if (error instanceof LoginError.UnknownFailure unknownFailure) {
            if (unknownFailure.getMessage() != null) {
                raiseLoginMessageWithInfo(
                        StockMessages.Restore_Unknown,
                        unknownFailure.getMessage(),
                        true
                );
            } else {
                raiseLoginMessage(StockMessages.Restore_Unknown, true);
            }
        }
    }

    public void handleConnectButtonPress() {
        selectedAppIndex = -1;
        ConnectNavHelper.INSTANCE.unlockAndGoToConnectJobsList(
                this, UnlockPolicy.SESSION_WITH_TIME_THRESHOLD, (success, error) -> {
                    setResult(RESULT_OK);
                    finish();
                }
        );
    }

    private void handleFailedConnectSignIn() {
        if (loginManagedByPersonalId()) {
            ApplicationRecord record = CommCareApplication.instance().getCurrentApp().getAppRecord();
            PersonalIdManager.ConnectAppMangement appState = personalIdManager.evaluateAppState(
                    this,
                    record.getUniqueId(),
                    getUniformUsername()
            );
            switch (appState) {
                case Connect -> FirebaseAnalyticsUtil.reportCccAppFailedAutoLogin(record.getApplicationId());
                case PersonalId -> uiController.setErrorMessageUI(
                        getString(R.string.personalid_failed_to_login_with_connectid),
                        false
                );
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menuIdToAnalyticsParam = createMenuItemToAnalyticsParamMapping();
        menu.add(
                0,
                MENU_PRACTICE_MODE,
                0,
                Localization.get("login.menu.demo")
        ).setIcon(android.R.drawable.ic_menu_preferences);
        menu.add(
                0,
                MENU_ABOUT_COMMCARE,
                1,
                Localization.get("home.menu.about")
        ).setIcon(android.R.drawable.ic_menu_help);
        menu.add(
                0,
                MENU_ACQUIRE_PERMISSIONS,
                1,
                Localization.get("permission.acquire.required")
        ).setIcon(android.R.drawable.ic_menu_manage);
        menu.add(0, MENU_FORGOT_PIN, 1, Localization.get("login.menu.password.mode"));
        menu.add(0, MENU_APP_MANAGER, 1, Localization.get("login.menu.app.manager"));
        menu.add(0, MENU_PERSONAL_ID_SIGN_IN, 1, getString(R.string.personalid_signup));
        menu.add(0, MENU_PERSONAL_ID_FORGET, 1, getString(R.string.personalid_forget_user));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(MENU_ACQUIRE_PERMISSIONS).setVisible(true);
        menu.findItem(MENU_FORGOT_PIN).setVisible(uiController.getLoginMode() == LoginMode.PIN);
        menu.findItem(MENU_PERSONAL_ID_SIGN_IN).setVisible(
                !personalIdManager.isloggedIn() && personalIdManager.checkDeviceCompability());
        menu.findItem(MENU_PERSONAL_ID_FORGET).setVisible(personalIdManager.isloggedIn());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FirebaseAnalyticsUtil.reportOptionsMenuItemClick(
                this.getClass(),
                menuIdToAnalyticsParam.get(item.getItemId())
        );
        switch (item.getItemId()) {
            case MENU_PRACTICE_MODE:
                loginDemoUser();
                return true;
            case MENU_ABOUT_COMMCARE:
                DialogCreationHelpers.showAboutCommCareDialog(this);
                return true;
            case MENU_ACQUIRE_PERMISSIONS:
                Permissions.acquireAllAppPermissions(
                        this,
                        this,
                        Permissions.ALL_PERMISSIONS_REQUEST
                );
                return true;
            case MENU_FORGOT_PIN:
                uiController.manualSwitchToPasswordMode();
                return true;
            case MENU_APP_MANAGER:
                Intent i = new Intent(this, AppManagerActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            case MENU_PERSONAL_ID_SIGN_IN:
                registerPersonalIdUser();
                return true;
            case MENU_PERSONAL_ID_FORGET:
                personalIdManager.forgetUser(AnalyticsParamValue.PERSONAL_ID_FORGOT_USER_LOGIN_PAGE);
                uiController.setPasswordOrPin("");
                setConnectAppState(Unmanaged);
                uiController.refreshView();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private Map<Integer, String> createMenuItemToAnalyticsParamMapping() {
        return Map.of(
                MENU_PRACTICE_MODE, AnalyticsParamValue.LOGIN_MENU_PRACTICE_MODE,
                MENU_ABOUT_COMMCARE, AnalyticsParamValue.LOGIN_MENU_ABOUT_COMMCARE,
                MENU_ACQUIRE_PERMISSIONS, AnalyticsParamValue.LOGIN_MENU_ACQUIRE_PERMISSIONS,
                MENU_FORGOT_PIN, AnalyticsParamValue.LOGIN_MENU_FORGOT_PIN,
                MENU_APP_MANAGER, AnalyticsParamValue.LOGIN_MENU_APP_MANAGER,
                MENU_PERSONAL_ID_SIGN_IN, AnalyticsParamValue.LOGIN_MENU_PERSONAL_ID_SIGN_IN,
                MENU_PERSONAL_ID_FORGET, AnalyticsParamValue.LOGIN_MENU_PERSONAL_ID_FORGET
        );
    }

    private void loginDemoUser() {
        OfflineUserRestore offlineUserRestore = CommCareApplication.instance().getCommCarePlatform()
                .getDemoUserRestore();
        FirebaseAnalyticsUtil.reportPracticeModeUsage(offlineUserRestore);

        if (offlineUserRestore != null) {
            runLoginPipeline(
                    offlineUserRestore.getUsername(),
                    OfflineUserRestore.DEMO_USER_PASSWORD,
                    LoginMode.PASSWORD,
                    AuthSource.Manual,
                    false,
                    true,
                    DataPullMode.CCZ_DEMO
            );
        } else {
            DemoUserBuilder.build(this, CommCareApplication.instance().getCurrentApp());
            runLoginPipeline(
                    DemoUserBuilder.DEMO_USERNAME,
                    DemoUserBuilder.DEMO_PASSWORD,
                    LoginMode.PASSWORD,
                    AuthSource.Manual,
                    false,
                    false,
                    DataPullMode.NORMAL
            );
        }
    }

    public void raiseLoginMessageWithInfo(
            MessageTag messageTag,
            String additionalInfo,
            boolean showTop
    ) {
        NotificationMessage message =
                NotificationMessageFactory.message(
                        messageTag,
                        new String[]{null, null, additionalInfo},
                        NOTIFICATION_MESSAGE_LOGIN
                );
        raiseMessage(message, showTop);
    }

    public void raiseLoginMessage(MessageTag messageTag, boolean showTop) {
        raiseLoginMessage(messageTag, showTop, NotificationActionButtonInfo.ButtonAction.NONE);
    }

    public void raiseLoginMessage(
            MessageTag messageTag,
            boolean showTop,
            NotificationActionButtonInfo.ButtonAction buttonAction
    ) {
        NotificationMessage message = NotificationMessageFactory.message(
                messageTag,
                NOTIFICATION_MESSAGE_LOGIN,
                buttonAction
        );
        raiseMessage(message, showTop);
    }

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
                dialog = CustomProgressDialog.newInstance(
                        Localization.get("key.manage.title"),
                        Localization.get("key.manage.start"),
                        taskId
                );
                break;
            case DataPullTask.DATA_PULL_TASK_ID:
                dialog = CustomProgressDialog.newInstance(
                        Localization.get("sync.communicating.title"),
                        Localization.get("sync.progress.starting"),
                        taskId
                );
                dialog.addCancelButton();
                dialog.addProgressBar();
                break;
            case TASK_UPGRADE_INSTALL:
                dialog = CustomProgressDialog.newInstance(
                        Localization.get("updates.installing.title"),
                        Localization.get("updates.installing.message"),
                        taskId
                );
                break;
            default:
                Log.w(
                        TAG, "taskId passed to generateProgressDialog does not match "
                                + "any valid possibilities in LoginActivity"
                );
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
                //Special case when user forgets PersonalId account and last app in the list is selected
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
                new InstallStagedUpdateTask<>(TASK_UPGRADE_INSTALL) {
                    @Override
                    protected void deliverResult(
                            LoginActivity receiver,
                            AppInstallStatus result
                    ) {
                        if (result == AppInstallStatus.Installed) {
                            Toast.makeText(
                                    receiver,
                                    Localization.get("login.update.install.success"),
                                    Toast.LENGTH_LONG
                            ).show();
                            UpdateActivity.OnSuccessfulUpdate(false, false);
                        } else {
                            CommCareApplication.notificationManager()
                                    .reportNotificationMessage(
                                            NotificationMessageFactory.message(result)
                                    );
                        }

                        localLoginOrPullAndLogin(uiController.isRestoreSessionChecked());
                    }

                    @Override
                    protected void deliverUpdate(
                            LoginActivity receiver,
                            int[]... update
                    ) {
                    }

                    @Override
                    protected void deliverError(
                            LoginActivity receiver,
                            Exception e
                    ) {
                        e.printStackTrace();
                        Logger.exception("Auto update on login failed", e);
                        Toast.makeText(
                                receiver,
                                Localization.get("login.update.install.failure"),
                                Toast.LENGTH_LONG
                        ).show();

                        localLoginOrPullAndLogin(uiController.isRestoreSessionChecked());
                    }
                };
        task.connect(this);
        task.executeParallel();
    }

    private void localLoginOrPullAndLogin(boolean restoreSession) {
        DataPullMode dataPullMode = CommCareApplication.instance().isConsumerApp()
                ? DataPullMode.CONSUMER_APP
                : DataPullMode.NORMAL;

        runLoginPipeline(
                getUniformUsername(),
                uiController.getEnteredPasswordOrPin(),
                uiController.getLoginMode(),
                determineAuthSource(),
                restoreSession,
                false,
                dataPullMode
        );
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

    private void registerPersonalIdUser() {
        personalIdManager.launchPersonalId(this, ConnectConstants.PERSONAL_ID_SIGN_UP_LAUNCH);
    }

    protected void seatAppIfNeeded(String appId) {
        boolean selectedNewApp = !isAppSeated(appId);
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

    private boolean isAppSeated(String appId) {
        return appId != null && appId.equals(CommCareApplication.instance().getCurrentApp().getUniqueId());
    }

    protected void evaluateConnectAppState() {
        if (personalIdManager.isloggedIn()) {
            String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
            PersonalIdManager.ConnectAppMangement appState = personalIdManager.evaluateAppState(
                    this,
                    seatedAppId,
                    uiController.getEnteredUsername()
            );

            if (appLaunchedFromConnect && presetAppId != null) {
                appState = Connect;
            }

            if (appState == PersonalId) {
                int selectorIndex = uiController.getSelectedAppIndex();
                String selectedAppId = !appIdDropdownList.isEmpty()
                        ? appIdDropdownList.get(selectorIndex) : "";
                if (uiController.isAppSelectorVisible() && !selectedAppId.equals(seatedAppId)) {
                    appState = Unmanaged;
                }
            }
            setConnectAppState(appState);
        } else {
            setConnectAppState(Unmanaged);
        }
    }

    protected boolean loginManagedByPersonalId() {
        return connectAppState == PersonalId || connectAppState == Connect;
    }

    public void setConnectAppState(PersonalIdManager.ConnectAppMangement connectAppState) {
        this.connectAppState = connectAppState;
    }

    @Override
    protected boolean shouldShowDrawer() {
        return shouldShowDrawerAfterCheck(true);
    }

    @Override
    protected boolean shouldHighlightSeatedApp() {
        return true;
    }

    protected PersonalIdManager.ConnectAppMangement getConnectAppState() {
        return connectAppState;
    }

    @Override
    protected void handleDrawerItemClick(
            @NonNull BaseDrawerController.NavItemType itemType,
            String recordId
    ) {
        if (itemType == BaseDrawerController.NavItemType.COMMCARE_APPS) {
            if (recordId != null) {
                if (!appIdDropdownList.isEmpty()) {
                    selectedAppIndex = appIdDropdownList.indexOf(recordId);
                }
                presetAppId = null;
                seatAppIfNeeded(recordId);
                closeDrawer();
            }
        } else {
            super.handleDrawerItemClick(itemType, recordId);
        }
    }
}

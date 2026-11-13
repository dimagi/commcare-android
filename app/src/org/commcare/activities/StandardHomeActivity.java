package org.commcare.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.lang3.StringUtils;
import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.connect.ConnectNavHelper;
import org.commcare.connect.EmailOfferHelper;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.repository.ConnectRepository;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.home.HomeActionsDelegate;
import org.commcare.home.HomeActivityCoordinator;
import org.commcare.home.HomeActivityHost;
import org.commcare.home.SyncDelegate;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.navdrawer.BaseDrawerController;
import org.commcare.suite.model.Endpoint;
import org.commcare.tasks.DataPullTask;
import org.commcare.utils.ApkDependenciesUtils;
import org.commcare.utils.AppLogoutHelper;
import org.commcare.utils.SessionRegistrationHelper;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xpath.XPathException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;

import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ARGUMENTS_BUNDLE;
import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ARGUMENTS_LIST;
import static org.commcare.activities.DispatchActivity.SESSION_ENDPOINT_ID;
import static org.commcare.connect.ConnectConstants.PERSONALID_MANAGED_LOGIN;

/**
 * Normal CommCare home screen: the menu, drawer and home buttons, and the external, endpoint and
 * shortcut launches. Home behavior any host shares lives in {@link HomeActivityCoordinator}.
 */
public class StandardHomeActivity extends SessionAwareCommCareActivity<SyncDelegate>
        implements HomeActivityHost, WithUIController {

    private static final String CC_APP_HOME_ENDPOINT = "cc_app_home";

    private final HomeActivityCoordinator coordinator = new HomeActivityCoordinator(this);

    private static final String TAG = StandardHomeActivity.class.getSimpleName();


    private StandardHomeActivityUIController uiController;
    private Map<Integer, String> menuIdToAnalyticsParam;
    private boolean personalIdManagedLogin = false;

    /**
     * The coordinator this home activity composes. Public so tests can assert on the launch/nav
     * state it owns
     */
    public HomeActivityCoordinator getCoordinator() {
        return coordinator;
    }

    @NonNull
    @Override
    public CommCareActivity<?> getSyncTaskActivity() {
        return this;
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return getReceiver().generateProgressDialog(taskId);
    }

    @Override
    public SyncDelegate getReceiver() {
        return coordinator.getSync();
    }

    @Override
    protected SessionExpirationHandler getSessionExpirationHandler() {
        return coordinator.getSessionExpiration();
    }

    @Override
    public void onSessionLost() {
        SessionRegistrationHelper.redirectToLogin(this);
    }

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        processFromExternalLaunch(savedInstanceState);
        processFromShortcutLaunch();
        processFromLoginLaunch();

        uiController.setupUI();
        personalIdManagedLogin = getIntent()
                .getBooleanExtra(PERSONALID_MANAGED_LOGIN, false);
        EmailOfferHelper.checkEmailCollection(this);
    }

    /**
     * Set state that signifies activity was launch from external app
     */
    private void processFromExternalLaunch(Bundle savedInstanceState) {
        if (savedInstanceState == null && getIntent().hasExtra(DispatchActivity.WAS_EXTERNAL)) {
            coordinator.getLaunchState().setWasExternal(true);
            if (getIntent().getBooleanExtra(DispatchActivity.CC_LAUNCH_REQUIRE_SYNC, false)) {
                getIntent().removeExtra(DispatchActivity.CC_LAUNCH_REQUIRE_SYNC);
                coordinator.getLaunchState().setPendingEndpointNavigationAfterSync(true);
                coordinator.setRedirectedInOnCreate(true);
                coordinator.getSync().sendFormsOrSync(false);
            } else if (processSessionEndpoint()) {
                coordinator.getSessionNavigation().startNextSessionStep();
            }
        }
    }

    /**
     * @return Whether we should proceed with next step in session navigation after processing the session endpoint
     */
    private boolean processSessionEndpoint() {
        if (getIntent().hasExtra(SESSION_ENDPOINT_ID)) {
            String sessionEndpointId = getIntent().getStringExtra(SESSION_ENDPOINT_ID);
            if (CC_APP_HOME_ENDPOINT.equals(sessionEndpointId)) {
                return false;
            }
            Endpoint endpoint = validateIntentForSessionEndpoint(sessionEndpointId);
            if (endpoint != null) {
                Bundle intentArgumentsAsBundle = getIntent().getBundleExtra(
                        SESSION_ENDPOINT_ARGUMENTS_BUNDLE);
                ArrayList<String> intentArgumentsAsList = getIntent().getStringArrayListExtra(
                        SESSION_ENDPOINT_ARGUMENTS_LIST);

                // Reset the Session to make sure we don't carry forward any session state to the
                // endpoint launch
                CommCareApplication.instance().getCurrentSessionWrapper().reset();

                try {
                    CommCareApplication.instance().getCurrentSessionWrapper()
                            .executeEndpointStack(
                                    endpoint,
                                    intentArgumentsAsBundle,
                                    intentArgumentsAsList
                            );
                    return true;
                } catch (Endpoint.InvalidEndpointArgumentsException e) {
                    String invalidEndpointArgsError =
                            org.commcare.utils.StringUtils.getStringRobust(
                                    this,
                                    R.string.session_endpoint_invalid_arguments,
                                    new String[]{
                                            endpoint.getId(),
                                            intentArgumentsAsBundle != null ?
                                                    StringUtils.join(intentArgumentsAsBundle, ",") :
                                                    String.valueOf(intentArgumentsAsList.size()),
                                            StringUtils.join(endpoint.getArguments(), ",")
                                    }
                            );
                    new UserfacingErrorHandling<>().createErrorDialog(
                            this,
                            invalidEndpointArgsError,
                            true
                    );
                }
            }
            return false;
        }
        return true;
    }

    private Endpoint validateIntentForSessionEndpoint(String sessionEndpointId) {
        Endpoint endpoint = CommCareApplication.instance().getCommCarePlatform().getEndpoint(
                sessionEndpointId);
        if (endpoint == null) {
            Hashtable<String, Endpoint> allEndpoints =
                    CommCareApplication.instance().getCommCarePlatform().getAllEndpoints();
            String invalidEndpointError = org.commcare.utils.StringUtils.getStringRobust(
                    this,
                    R.string.session_endpoint_unavailable,
                    new String[]{
                            sessionEndpointId,
                            StringUtils.join(allEndpoints.keySet(), ",")
                    }
            );
            new UserfacingErrorHandling<>().createErrorDialog(this, invalidEndpointError, true);
            return null;
        }
        return endpoint;
    }

    private void processFromShortcutLaunch() {
        if (getIntent().getBooleanExtra(DispatchActivity.WAS_SHORTCUT_LAUNCH, false)) {
            coordinator.getSessionNavigation().startNextSessionStep();
        }
    }

    private void processFromLoginLaunch() {
        if (getIntent().getBooleanExtra(DispatchActivity.START_FROM_LOGIN, false) &&
                !coordinator.getLaunchState().getLoginExtraWasConsumed()) {
            getIntent().removeExtra(DispatchActivity.START_FROM_LOGIN);
            coordinator.getLaunchState().setLoginExtraWasConsumed(true);
            coordinator.setRedirectedInOnCreate(coordinator.getLaunchChecks().run());
        }
    }

    void goToFormArchive(boolean incomplete) {
        coordinator.getActions().showFormArchive(incomplete);
    }

    void userTriggeredLogout() {
        if (AppLogoutHelper.isBlockedByActiveSync(this)) {
            return;
        }
        CommCareApplication.instance().closeUserSession();
        deliverResultAndFinish(new Intent());
    }

    @Override
    public void deliverResultAndFinish(@NonNull Intent result) {
        HomeResultDelivery.deliverResultAndFinish(this, result);
    }

    protected void userPressedOpportunityStatus() {
        // Launch the seated app's job status page on top of this (still-live) Home so the app
        // session is preserved and backing out of the status page returns here.
        ConnectJobRecord job = Objects.requireNonNull(
                ConnectJobUtils.getJobForSeatedApp(this),
                "View Job Status pressed but no Connect job was found for the seated app"
        );
        ConnectNavHelper.INSTANCE.goToActiveInfoForJob(this, job);
    }

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
        coordinator.onActivityResult(requestCode, resultCode, intent);
    }

    void enterTrainingModule() {
        CommCareApplication.instance().getCurrentSession().setCommand(
                org.commcare.suite.model.Menu.TRAINING_MENU_ROOT);
        coordinator.getSessionNavigation().startNextSessionStepSafe();
    }

    static Intent buildHomeIntent(
            Context context,
            LoginMode loginMode,
            boolean startFromLogin,
            boolean manualSwitchToPwMode,
            boolean personalIdManagedLogin
    ) {
        Intent intent = new Intent(context, StandardHomeActivity.class);
        intent.putExtra(DispatchActivity.START_FROM_LOGIN, startFromLogin);
        intent.putExtra(LoginActivity.LOGIN_MODE, loginMode);
        intent.putExtra(LoginActivity.MANUAL_SWITCH_TO_PW_MODE, manualSwitchToPwMode);
        intent.putExtra(PERSONALID_MANAGED_LOGIN, personalIdManagedLogin);
        return intent;
    }

    /**
     * The Home intent used by Connect app launches that bypass LoginActivity. Exposed so callers can
     * start it via {@code startActivityForResult} and react to the Home result (e.g. a back-out).
     */
    public static Intent buildHomeLaunchIntent(Context context) {
        return buildHomeIntent(context, LoginMode.PASSWORD, true, false, true);
    }

    @Override
    public void onResumeSessionSafe() {
        coordinator.onHostResumed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        uiController.clearSyncButtonMessage();
    }

    @Override
    public void onPullTaskFinished(@NonNull DataPullTask.PullTaskResult result) {
        if (coordinator.getLaunchState().getPendingEndpointNavigationAfterSync() && processSessionEndpoint()) {
            coordinator.getSessionNavigation().startNextSessionStep();
            coordinator.getLaunchState().setPendingEndpointNavigationAfterSync(false);
        } else {
            coordinator.getAppHealth().onPullTaskFinished(result);
        }
        uiController.refreshView();
    }

    @Override
    public boolean isDemoUser() {
        try {
            User u = CommCareApplication.instance().getSession().getLoggedInUser();
            return (User.TYPE_DEMO.equals(u.getUserType()));
        } catch (SessionUnavailableException e) {
            // Default to a normal user: this should only happen if session
            // expires and hasn't redirected to login.
            return false;
        }
    }

    /**
     * Per-action availability for the home activity of CC apps
     */
    @Override
    public boolean areAppActionsAvailable() {
        return !isDemoUser();
    }

    @NonNull
    @Override
    public Context getHostContext() {
        return this;
    }

    @NonNull
    @Override
    public AppCompatActivity getHostActivity() {
        return this;
    }

    @Override
    public void refreshHostUi() {
        refreshUI();
    }

    @Override
    public Intent getLaunchIntent() {
        return getIntent();
    }

    @Override
    public String getFormEntryTitle() {
        return CommCareActivity.getTitle(this, null);
    }

    @Override
    public void showFormArchive(boolean incomplete, FormRecord record) {
        coordinator.getActions().showFormArchive(incomplete, record);
    }

    @Override
    public void finishWithResult(int resultCode, Intent data) {
        setResult(resultCode, data);
        finish();
    }

    @Override
    public void showXPathError(@NonNull XPathException exception, boolean shouldExit) {
        new UserfacingErrorHandling<>().logErrorAndShowDialog(this, exception, shouldExit);
    }

    @Override
    public void showErrorDialog(@NonNull String message, boolean shouldExit) {
        new UserfacingErrorHandling<>().createErrorDialog(this, message, shouldExit);
    }

    @Override
    public boolean isBackEnabled() {
        return false;
    }

    void enterRootModule() {
        if (doPreStartChecks()) {
            coordinator.getSessionNavigation().launchRootMenu();
        }
    }

    private boolean doPreStartChecks() {
        return ApkDependenciesUtils.performDependencyCheckFlow(this);
    }

    @Override
    public String getActivityTitle() {
        String userName;
        try {
            userName = CommCareApplication.instance().getSession().getLoggedInUser().getUsername();
            if (userName != null) {
                return Localization.get("home.logged.in.message", new String[]{userName});
            }
        } catch (Exception e) {
            //TODO: Better catch, here
        }
        return "";
    }

    @Override
    protected boolean isTopNavEnabled() {
        return false;
    }

    /**
     * Triggered by a user manually clicking the sync button
     */
    void syncButtonPressed() {
        if (coordinator.getActions().syncIfOnline()) {
            fetchJobProgressOverNetwork();
        }
    }

    void syncSubTextPressed() {
        if (CommCareApplication.notificationManager().messagesForCommCareArePending()) {
            CommCareNoficationManager.performIntentCalloutToNotificationsView(this);
        }
    }

    @Override
    public void updateUiAfterDataPullOrSend(@NonNull String message, boolean success) {
        displayToast(message);
        uiController.updateSyncButtonMessage(message);
        uiController.updateConnectJobProgress();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_app_home, menu);
        menuIdToAnalyticsParam = createMenuItemToAnalyticsParamMapping();
        menu.findItem(R.id.action_update).setTitle(Localization.get("home.menu.update"));
        menu.findItem(R.id.action_saved_forms).setTitle(Localization.get("home.menu.saved.forms"));
        menu.findItem(R.id.action_change_language).setTitle(Localization.get("home.menu.locale.change"));
        menu.findItem(R.id.action_about).setTitle(Localization.get("home.menu.about"));
        menu.findItem(R.id.action_advanced).setTitle(Localization.get("home.menu.advanced"));
        menu.findItem(R.id.action_preferences).setTitle(Localization.get("home.menu.settings"));
        menu.findItem(R.id.action_set_pin).setTitle(Localization.get("home.menu.pin.set"));
        menu.findItem(R.id.action_update_commcare).setTitle(Localization.get("home.menu.update.commcare"));

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //In Holo theme this gets called on startup
        HomeActionsDelegate actions = coordinator.getActions();
        menu.findItem(R.id.action_update).setVisible(actions.canUpdateApp());
        menu.findItem(R.id.action_saved_forms).setVisible(actions.canViewSavedForms());
        menu.findItem(R.id.action_change_language).setVisible(actions.canChangeLanguage());
        menu.findItem(R.id.action_preferences).setVisible(actions.canOpenSettings());
        menu.findItem(R.id.action_advanced).setVisible(actions.canOpenAdvanced());
        menu.findItem(R.id.action_about).setVisible(actions.canShowAbout());
        menu.findItem(R.id.action_update_commcare).setVisible(actions.canUpdateCommCare());
        menu.findItem(R.id.action_set_pin).setVisible(actions.canSetPin());
        setPinMenuTitle(menu);
        return true;
    }

    private static void setPinMenuTitle(Menu menu) {
        boolean hasPinSet = false;

        try {
            hasPinSet = CommCareApplication.instance().getRecordForCurrentUser().hasPinSet();
        } catch (SessionUnavailableException e) {
            Log.d(TAG, "Session expired and menu is being created before redirect to login screen");
        }

        if (hasPinSet) {
            menu.findItem(R.id.action_set_pin).setTitle(Localization.get("home.menu.pin.change"));
        } else {
            menu.findItem(R.id.action_set_pin).setTitle(Localization.get("home.menu.pin.set"));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FirebaseAnalyticsUtil.reportOptionsMenuItemClick(this.getClass(),
                menuIdToAnalyticsParam.get(item.getItemId()));
        int itemId = item.getItemId();
        HomeActionsDelegate actions = coordinator.getActions();
        if (itemId == R.id.action_update) {
            actions.updateApp();
            return true;
        } else if (itemId == R.id.action_saved_forms) {
            actions.viewSavedForms();
            return true;
        } else if (itemId == R.id.action_change_language) {
            actions.changeLanguage();
            return true;
        } else if (itemId == R.id.action_preferences) {
            actions.openSettings();
            return true;
        } else if (itemId == R.id.action_advanced) {
            actions.openAdvanced();
            return true;
        } else if (itemId == R.id.action_about) {
            actions.showAbout();
            return true;
        } else if (itemId == R.id.action_set_pin) {
            actions.setPin();
            return true;
        } else if (itemId == R.id.action_update_commcare) {
            actions.updateCommCare();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static Map<Integer, String> createMenuItemToAnalyticsParamMapping() {
        Map<Integer, String> menuIdToAnalyticsEvent = new HashMap<>();
        menuIdToAnalyticsEvent.put(R.id.action_update,
                AnalyticsParamValue.ITEM_UPDATE_CC);
        menuIdToAnalyticsEvent.put(R.id.action_saved_forms,
                AnalyticsParamValue.ITEM_SAVED_FORMS);
        menuIdToAnalyticsEvent.put(R.id.action_change_language,
                AnalyticsParamValue.ITEM_CHANGE_LANGUAGE);
        menuIdToAnalyticsEvent.put(R.id.action_preferences,
                AnalyticsParamValue.ITEM_SETTINGS);
        menuIdToAnalyticsEvent.put(R.id.action_advanced,
                AnalyticsParamValue.ITEM_ADVANCED_ACTIONS);
        menuIdToAnalyticsEvent.put(R.id.action_about,
                AnalyticsParamValue.ITEM_ABOUT_CC);
        menuIdToAnalyticsEvent.put(R.id.action_update_commcare,
                AnalyticsParamValue.ITEM_UPDATE_CC_PLATFORM);
        return menuIdToAnalyticsEvent;
    }

    @Override
    protected void handleDrawerItemClick(@NonNull BaseDrawerController.NavItemType itemType) {
        switch (itemType) {
            case COMMCARE_APPS -> {
                closeDrawer();
                AppLogoutHelper.promptToReturnToLogin(
                        this,
                        R.string.nav_drawer_switch_app_dialog_title,
                        R.string.nav_drawer_switch_app_dialog_message
                );
            }
            case OPPORTUNITIES -> {
                if(personalIdManagedLogin) {
                    ConnectNavHelper.INSTANCE.goToConnectJobsList(this);
                    closeDrawer();
                } else {
                    navigateToConnectMenu();
                }
            }
            case MESSAGING -> {
                if(personalIdManagedLogin) {
                    ConnectNavHelper.INSTANCE.goToMessaging(this, null);
                    closeDrawer();
                } else {
                    navigateToMessaging();
                }
            }
            case WORK_HISTORY -> {
                if(personalIdManagedLogin) {
                    ConnectNavHelper.INSTANCE.goToWorkHistory(this);
                    closeDrawer();
                } else {
                    navigateToWorkHistory();
                }
            }
        }
    }

    @Override
    public void initUIController() {
        uiController = new StandardHomeActivityUIController(this);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    protected boolean shouldShowDrawer() {
        return shouldShowDrawerAfterCheck(true);
    }

    public void refreshUI() {
        uiController.refreshView();
    }

    @Override
    public void onLocaleChanged() {
        uiController.setupUI();
    }

    /**
     * The home actions live in this activity's options menu, so re-preparing the menu re-evaluates
     * all of them: the demo-user gating, the CommCare-update item and the PIN item's set/change title.
     */
    @Override
    public void refreshActionSurface() {
        invalidateOptionsMenu();
    }

    public void fetchJobProgressOverNetwork() {
        ConnectJobRecord job = getActiveJob();
        if(job != null && job.getStatus() == ConnectJobRecord.STATUS_DELIVERING) {
            ConnectRepository.getInstance().updateDeliveryProgressForJava(job, (success, error) -> {
                if (success) {
                    uiController.updateConnectJobProgress();
                }
            });
        }
    }

    public ConnectJobRecord getActiveJob() {
        return ConnectJobUtils.getJobForSeatedApp(this);
    }
}

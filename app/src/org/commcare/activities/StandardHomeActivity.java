package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import org.commcare.CommCareApplication;
import org.commcare.CommCareNoficationManager;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.android.database.connect.models.ConnectJobRecord;

import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.ApkDependenciesUtils;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

/**
 * Normal CommCare home screen
 */
public class StandardHomeActivity
        extends HomeScreenBaseActivity<StandardHomeActivity>
        implements WithUIController {

    private static final String TAG = StandardHomeActivity.class.getSimpleName();

    private static final String AIRPLANE_MODE_CATEGORY = "airplane-mode";

    private StandardHomeActivityUIController uiController;
    private Map<Integer, String> menuIdToAnalyticsParam;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        uiController.setupUI();
    }

    @Override
    public void onResumeSessionSafe() {
        super.onResumeSessionSafe();
    }

    void enterRootModule() {
        if (doPreStartChecks()) {
            Intent i = new Intent(this, MenuActivity.class);
            addPendingDataExtra(i, CommCareApplication.instance().getCurrentSessionWrapper().getSession());
            startActivityForResult(i, GET_COMMAND);
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
        if (!ConnectivityStatus.isNetworkAvailable(StandardHomeActivity.this)) {
            if (ConnectivityStatus.isAirplaneModeOn(StandardHomeActivity.this)) {
                handleSyncNotAttempted(Localization.get("notification.sync.airplane.action"));
                CommCareApplication.notificationManager().reportNotificationMessage(
                        NotificationMessageFactory.message(
                                NotificationMessageFactory.StockMessages.Sync_AirplaneMode,
                                AIRPLANE_MODE_CATEGORY));
            } else {
                handleSyncNotAttempted(Localization.get("notification.sync.connections.action"));
                CommCareApplication.notificationManager().reportNotificationMessage(
                        NotificationMessageFactory.message(
                                NotificationMessageFactory.StockMessages.Sync_NoConnections,
                                AIRPLANE_MODE_CATEGORY));
            }
            FirebaseAnalyticsUtil.reportSyncResult(
                    false,
                    AnalyticsParamValue.SYNC_TRIGGER_USER,
                    AnalyticsParamValue.SYNC_MODE_SEND_FORMS,
                    AnalyticsParamValue.SYNC_FAIL_NO_CONNECTION);
            return;
        }
        updateConnectJobProgress();
        CommCareApplication.notificationManager().clearNotifications(AIRPLANE_MODE_CATEGORY);
        sendFormsOrSync(true);
    }

    void syncSubTextPressed() {
        if (CommCareApplication.notificationManager().messagesForCommCareArePending()) {
            CommCareNoficationManager.performIntentCalloutToNotificationsView(this);
        }
    }

    @Override
    protected void updateUiAfterDataPullOrSend(String message, boolean success) {
        displayToast(message);
        uiController.updateSyncButtonMessage(message);
        uiController.updateConnectProgress();
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
        boolean enableMenus = !isDemoUser();
        menu.findItem(R.id.action_update).setVisible(enableMenus);
        menu.findItem(R.id.action_saved_forms).setVisible(enableMenus);
        menu.findItem(R.id.action_change_language).setVisible(true);
        menu.findItem(R.id.action_preferences).setVisible(enableMenus);
        menu.findItem(R.id.action_advanced).setVisible(enableMenus);
        menu.findItem(R.id.action_about).setVisible(enableMenus);
        menu.findItem(R.id.action_update_commcare).setVisible(enableMenus && showCommCareUpdateMenu);
        preparePinMenu(menu, enableMenus);
        return true;
    }

    private static void preparePinMenu(Menu menu, boolean enableMenus) {
        boolean pinEnabled = enableMenus && DeveloperPreferences.shouldOfferPinForLogin();
        menu.findItem(R.id.action_set_pin).setVisible(pinEnabled);
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
        if (itemId == R.id.action_update) {
            launchUpdateActivity(false);
            return true;
        } else if (itemId == R.id.action_saved_forms) {
            goToFormArchive(false);
            return true;
        } else if (itemId == R.id.action_change_language) {
            showLocaleChangeMenu(uiController);
            return true;
        } else if (itemId == R.id.action_preferences) {
            createPreferencesMenu(this);
            return true;
        } else if (itemId == R.id.action_advanced) {
            showAdvancedActionsPreferences();
            return true;
        } else if (itemId == R.id.action_about) {
            showAboutCommCareDialog();
            return true;
        } else if (itemId == R.id.action_set_pin) {
            launchPinAuthentication();
            return true;
        } else if (itemId == R.id.action_update_commcare) {
            startCommCareUpdate();
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
    public void initUIController() {
        uiController = new StandardHomeActivityUIController(this);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndErrorMessage,
                                     boolean userTriggeredSync, boolean formsToSend,
                                     boolean usingRemoteKeyManagement) {
        super.handlePullTaskResult(resultAndErrorMessage, userTriggeredSync, formsToSend,
                usingRemoteKeyManagement);
        uiController.refreshView();
    }

    @Override
    public boolean shouldShowSyncItemInActionBar() {
        return false;
    }

    @Override
    public boolean usesSubmissionProgressBar() {
        return false;
    }

    @Override
    public void refreshUI() {
        uiController.refreshView();
    }

    @Override
    void refreshCCUpdateOption() {
        invalidateOptionsMenu();
    }

    public void updateConnectJobProgress() {
        ConnectJobRecord job = getActiveJob();
        if(job != null && job.getStatus() == ConnectJobRecord.STATUS_DELIVERING) {
            ConnectJobHelper.INSTANCE.updateDeliveryProgress(this, job, success -> {
                if (success) {
                    uiController.updateConnectProgress();
                }
            });
        }
    }

    public ConnectJobRecord getActiveJob() {
        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectAppRecord appRecord = ConnectJobUtils.getAppRecord(this, appId);
        if (appRecord == null) {
            return null;
        }

        return ConnectJobUtils.getCompositeJob(this, appRecord.getJobId());
    }
}

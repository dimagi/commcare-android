package org.commcare.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import org.commcare.CommCareApplication;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.tasks.ConnectionDiagnosticTask;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

import static org.commcare.CommCareNoficationManager.AIRPLANE_MODE_CATEGORY;

/**
 * Normal CommCare home screen
 */
public class StandardHomeActivity
        extends HomeScreenBaseActivity<StandardHomeActivity>
        implements WithUIController {

    private static final String TAG = StandardHomeActivity.class.getSimpleName();

    // NOTE: Menu.FIRST is reserved for MENU_SYNC in SyncCapableCommCareActivity
    public static final int MENU_UPDATE = Menu.FIRST + 1;
    public static final int MENU_SAVED_FORMS = Menu.FIRST + 2;
    public static final int MENU_CHANGE_LANGUAGE = Menu.FIRST + 3;
    public static final int MENU_PREFERENCES = Menu.FIRST + 4;
    public static final int MENU_ADVANCED = Menu.FIRST + 5;
    public static final int MENU_ABOUT = Menu.FIRST + 6;
    public static final int MENU_PIN = Menu.FIRST + 7;

    private StandardHomeActivityUIController uiController;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        uiController.setupUI();
    }

    void enterRootModule() {
        Intent i = new Intent(this, MenuActivity.class);
        addPendingDataExtra(i, CommCareApplication.instance().getCurrentSessionWrapper().getSession());
        startActivityForResult(i, GET_COMMAND);
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
            FirebaseAnalyticsUtil.reportSyncFailure(
                    AnalyticsParamValue.SYNC_TRIGGER_USER,
                    AnalyticsParamValue.SYNC_MODE_SEND_FORMS,
                    AnalyticsParamValue.SYNC_FAIL_NO_CONNECTION);
            return;
        } else {
            ConnectionDiagnosticTaskImpl impl = new ConnectionDiagnosticTaskImpl(getApplicationContext());
            impl.connect(StandardHomeActivity.this);
            impl.executeParallel();
        }
    }

    static class ConnectionDiagnosticTaskImpl extends ConnectionDiagnosticTask<StandardHomeActivity> {

        public ConnectionDiagnosticTaskImpl(Context c) {
            super(c);
        }

        @Override
        protected void deliverResult(StandardHomeActivity receiver, NetworkState networkState) {
            String localizedTextId = null;
            MessageTag notificationStockMessage = null;
            String analyticsMessage = null;
            switch (networkState) {
                case CONNECTED:
                    // Network is available. Start syncing now.
                    CommCareApplication.notificationManager().clearNotifications(AIRPLANE_MODE_CATEGORY);
                    receiver.sendFormsOrSync(true);
                    return;
                case DISCONNECTED:
                    localizedTextId = "notification.sync.connections.action";
                    notificationStockMessage = NotificationMessageFactory.StockMessages.Sync_NoConnections;
                    analyticsMessage = AnalyticsParamValue.SYNC_FAIL_NO_CONNECTION;
                    break;
                case CAPTIVE_PORTAL:
                    localizedTextId = "connection.captive_portal.action";
                    notificationStockMessage = NotificationMessageFactory.StockMessages.Sync_CaptivePortal;
                    analyticsMessage = AnalyticsParamValue.SYNC_FAIL_CAPTIVE_PORTAL;
                    break;
                case COMMCARE_BLOCKED:
                    localizedTextId = "connection.commcare_blocked.action";
                    notificationStockMessage = NotificationMessageFactory.StockMessages.Sync_CommcareBlocked;
                    analyticsMessage = AnalyticsParamValue.SYNC_FAIL_COMMCARE_BLOCKED;
                    break;
            }
            receiver.handleSyncNotAttempted(Localization.get(localizedTextId));
            CommCareApplication.notificationManager().reportNotificationMessage(
                    NotificationMessageFactory.message(
                            notificationStockMessage,
                            AIRPLANE_MODE_CATEGORY));
            FirebaseAnalyticsUtil.reportSyncFailure(
                    AnalyticsParamValue.SYNC_TRIGGER_USER,
                    AnalyticsParamValue.SYNC_MODE_SEND_FORMS,
                    analyticsMessage);
        }

        @Override
        protected void deliverUpdate(StandardHomeActivity standardHomeActivity, String... update) {

        }

        @Override
        protected void deliverError(StandardHomeActivity standardHomeActivity, Exception e) {

        }
    }

    @Override
    protected void updateUiAfterDataPullOrSend(String message, boolean success) {
        displayToast(message);
        uiController.updateSyncButtonMessage(message);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_UPDATE, 0, Localization.get("home.menu.update")).setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, MENU_SAVED_FORMS, 0, Localization.get("home.menu.saved.forms")).setIcon(
                android.R.drawable.ic_menu_save);
        menu.add(0, MENU_CHANGE_LANGUAGE, 0, Localization.get("home.menu.locale.change")).setIcon(
                android.R.drawable.ic_menu_set_as);
        menu.add(0, MENU_ABOUT, 0, Localization.get("home.menu.about")).setIcon(
                android.R.drawable.ic_menu_help);
        menu.add(0, MENU_ADVANCED, 0, Localization.get("home.menu.advanced")).setIcon(
                android.R.drawable.ic_menu_edit);
        menu.add(0, MENU_PREFERENCES, 0, Localization.get("home.menu.settings")).setIcon(
                android.R.drawable.ic_menu_preferences);
        menu.add(0, MENU_PIN, 0, Localization.get("home.menu.pin.set"));
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        //In Holo theme this gets called on startup
        boolean enableMenus = !isDemoUser();
        menu.findItem(MENU_UPDATE).setVisible(enableMenus);
        menu.findItem(MENU_SAVED_FORMS).setVisible(enableMenus);
        menu.findItem(MENU_CHANGE_LANGUAGE).setVisible(true);
        menu.findItem(MENU_PREFERENCES).setVisible(enableMenus);
        menu.findItem(MENU_ADVANCED).setVisible(enableMenus);
        menu.findItem(MENU_ABOUT).setVisible(enableMenus);
        preparePinMenu(menu, enableMenus);
        return true;
    }

    private static void preparePinMenu(Menu menu, boolean enableMenus) {
        boolean pinEnabled = enableMenus && DeveloperPreferences.shouldOfferPinForLogin();
        menu.findItem(MENU_PIN).setVisible(pinEnabled);
        boolean hasPinSet = false;

        try {
            hasPinSet = CommCareApplication.instance().getRecordForCurrentUser().hasPinSet();
        } catch (SessionUnavailableException e) {
            Log.d(TAG, "Session expired and menu is being created before redirect to login screen");
        }

        if (hasPinSet) {
            menu.findItem(MENU_PIN).setTitle(Localization.get("home.menu.pin.change"));
        } else {
            menu.findItem(MENU_PIN).setTitle(Localization.get("home.menu.pin.set"));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Map<Integer, String> menuIdToAnalyticsParam = createMenuItemToAnalyticsParamMapping();

        FirebaseAnalyticsUtil.reportOptionsMenuItemClick(this.getClass(),
                menuIdToAnalyticsParam.get(item.getItemId()));

        switch (item.getItemId()) {
            case MENU_UPDATE:
                launchUpdateActivity(false);
                return true;
            case MENU_SAVED_FORMS:
                goToFormArchive(false);
                return true;
            case MENU_CHANGE_LANGUAGE:
                showLocaleChangeMenu(uiController);
                return true;
            case MENU_PREFERENCES:
                createPreferencesMenu(this);
                return true;
            case MENU_ADVANCED:
                showAdvancedActionsPreferences();
                return true;
            case MENU_ABOUT:
                showAboutCommCareDialog();
                return true;
            case MENU_PIN:
                launchPinAuthentication();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static Map<Integer, String> createMenuItemToAnalyticsParamMapping() {
        Map<Integer, String> menuIdToAnalyticsEvent = new HashMap<>();
        menuIdToAnalyticsEvent.put(MENU_UPDATE,
                AnalyticsParamValue.ITEM_UPDATE_CC);
        menuIdToAnalyticsEvent.put(MENU_SAVED_FORMS,
                AnalyticsParamValue.ITEM_SAVED_FORMS);
        menuIdToAnalyticsEvent.put(MENU_CHANGE_LANGUAGE,
                AnalyticsParamValue.ITEM_CHANGE_LANGUAGE);
        menuIdToAnalyticsEvent.put(MENU_PREFERENCES,
                AnalyticsParamValue.ITEM_SETTINGS);
        menuIdToAnalyticsEvent.put(MENU_ADVANCED,
                AnalyticsParamValue.ITEM_ADVANCED_ACTIONS);
        menuIdToAnalyticsEvent.put(MENU_ABOUT,
                AnalyticsParamValue.ITEM_ABOUT_CC);
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
        super.handlePullTaskResult(resultAndErrorMessage, userTriggeredSync, formsToSend, usingRemoteKeyManagement);
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
    
}

package org.commcare.activities.connect;

import static org.commcare.connect.ConnectConstants.GO_TO_JOB_STATUS;
import static org.commcare.connect.ConnectConstants.NOTIFICATION_ID;
import static org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID;
import static org.commcare.connect.ConnectConstants.PAYMENT_UUID;
import static org.commcare.connect.ConnectConstants.REDIRECT_ACTION;
import static org.commcare.utils.FirebaseMessagingUtil.getNotificationActionFromIntent;
import static org.commcare.utils.NotificationUtil.getNotificationIcon;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.base.Strings;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.HomeResultDelivery;
import org.commcare.activities.NavigationHostCommCareActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDialogCoordinator;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectNavHelper;
import org.commcare.connect.MessageManager;
import org.commcare.connect.OpportunityHomeAppActionsMenu;
import org.commcare.connect.PersonalIdSessionManager;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.NotificationRecordDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.home.HomeActivityCoordinator;
import org.commcare.home.HomeActivityHost;
import org.commcare.home.SeatedAppSessionKt;
import org.commcare.home.SyncDelegate;
import org.commcare.pn.helper.NotificationBroadcastHelper;
import org.commcare.tasks.DataPullTask;
import org.commcare.views.UserfacingErrorHandling;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.xpath.XPathException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import kotlin.Unit;

public class ConnectActivity extends NavigationHostCommCareActivity<SyncDelegate>
        implements HomeActivityHost {
    private boolean backButtonAndActionBarEnabled = true;
    private boolean waitDialogEnabled = true;
    private String redirectionAction = "";
    private ConnectJobRecord job;
    private String opportunityUuid;
    private MenuItem notificationsMenuItem = null;

    private static final int REQUEST_CODE_PERSONAL_ID_ACTIVITY = 1000;
    private Map<Integer, String> menuIdToAnalyticsParam;

    private final HomeActivityCoordinator coordinator = HomeActivityCoordinator.forSessionlessHost(this);
    private final OpportunityHomeAppActionsMenu appActionsMenu =
            new OpportunityHomeAppActionsMenu(coordinator.getActions());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            String savedJobUuid = savedInstanceState.getString(OPPORTUNITY_UUID);
            if (savedJobUuid != null) {
                job = ConnectJobUtils.getCompositeJob(savedJobUuid);
            }
        }

        super.onCreate(savedInstanceState);
        setTitle(R.string.connect_title);

        PersonalIdSessionManager personalIdSessionManager = PersonalIdSessionManager.getInstance();

        if(personalIdSessionManager.isloggedIn()){
            NotificationBroadcastHelper.INSTANCE.registerForNotifications(
                    this,
                    this,
                    () -> {
                        updateNotificationIcon();
                        return Unit.INSTANCE;
                    }
            );
            initStateFromExtras();
            updateBackButton();

            // Wait for fragment to attach
            getSupportFragmentManager().executePendingTransactions();

            NavInflater inflater = navController.getNavInflater();
            NavGraph graph = inflater.inflate(R.navigation.nav_graph_connect);
            Bundle startArgs = new Bundle();
            graph.setStartDestination(getStartDestinationId(startArgs));
            navController.setGraph(graph, startArgs);
            navController.addOnDestinationChangedListener(
                    (controller, destination, arguments) -> invalidateOptionsMenu());

            retrieveMessages();
        }else{
            Toast.makeText(this,R.string.personalid_not_login_from_fcm_error,Toast.LENGTH_LONG).show();
            ConnectNavHelper.launchPersonalId(this, REQUEST_CODE_PERSONAL_ID_ACTIVITY);
            finish();
        }


    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        setActionBarTitle(title);
    }

    @Override
    protected void onResume() {
        super.onResume();
        coordinator.getSessionExpiration().startListening();
        coordinator.getSessionExpiration().handlePendingExpiration();
        ConnectDialogCoordinator.showDueDialog(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        coordinator.getSessionExpiration().stopListening();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (job != null) {
            outState.putString(OPPORTUNITY_UUID, job.getJobUUID());
        }
    }

    private int getStartDestinationId(Bundle startArgs) {
        int startDestinationId = R.id.connect_jobs_list_fragment;
        if (getIntent().getBooleanExtra(GO_TO_JOB_STATUS, false)) {
            startDestinationId = handleInfoRedirect();
        } else if (!Strings.isNullOrEmpty(redirectionAction)) {
            startDestinationId = handleSecureRedirect(startArgs);
        }
        return startDestinationId;
    }

    private void initStateFromExtras() {
        redirectionAction = getIntent().getStringExtra(REDIRECT_ACTION);
        opportunityUuid = getIntent().getStringExtra(OPPORTUNITY_UUID);
        if (job == null && !TextUtils.isEmpty(opportunityUuid)) {
            job = ConnectJobUtils.getCompositeJob(opportunityUuid);
        }
    }

    private int handleInfoRedirect() {
        Objects.requireNonNull(job);
        return R.id.connect_opportunity_home_fragment;
    }

    private int handleSecureRedirect(Bundle startArgs) {
        //Entering from a notification, so we may need to initialize
        String notificationId = getIntent().getStringExtra(NOTIFICATION_ID);
        if (!TextUtils.isEmpty(notificationId)) {
            NotificationRecordDatabaseHelper.INSTANCE.updateReadStatus(this, notificationId, true);
            FirebaseAnalyticsUtil.reportNotificationEvent(
                    AnalyticsParamValue.NOTIFICATION_EVENT_TYPE_CLICK,
                    AnalyticsParamValue.REPORT_NOTIFICATION_CLICK_NOTIFICATION_TRAY,
                    getNotificationActionFromIntent(getIntent()),
                    notificationId
            );
        }

        redirectionAction = ConnectJobUtils.resolveGenericOpportunityDestination(
                redirectionAction,
                job,
                getIntent().getStringExtra(PAYMENT_UUID)
        );

        startArgs.putString(REDIRECT_ACTION, redirectionAction);
        startArgs.putBoolean(
                ConnectConstants.FROM_SMS_INVITE_LINK,
                getIntent().getBooleanExtra(ConnectConstants.FROM_SMS_INVITE_LINK, false)
        );
        startArgs.putBoolean(
                ConnectConstants.FROM_STARTUP_ROUTING,
                getIntent().getBooleanExtra(ConnectConstants.FROM_STARTUP_ROUTING, false)
        );

        if (!TextUtils.isEmpty(opportunityUuid)) {
            startArgs.putString(OPPORTUNITY_UUID, opportunityUuid);
        }

        return R.id.connect_unlock_fragment;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connect, menu);

        MenuItem notification = menu.findItem(R.id.action_sync);
        notification.getIcon().setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_ATOP);

        notificationsMenuItem = menu.findItem(R.id.action_bell);
        updateNotificationIcon();

        appActionsMenu.inflate(menu, getMenuInflater());

        menuIdToAnalyticsParam = createMenuItemToAnalyticsParamMapping();
        return super.onCreateOptionsMenu(menu);
    }
    private void updateNotificationIcon() {
        if (notificationsMenuItem == null) return;

        int iconRes = getNotificationIcon(this);
        notificationsMenuItem.setIcon(iconRes);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_sync).setVisible(backButtonAndActionBarEnabled);
        appActionsMenu.prepare(menu, backButtonAndActionBarEnabled && isShowingOpportunityHome());
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean isShowingOpportunityHome() {
        return navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.connect_opportunity_home_fragment;
    }

    private static Map<Integer, String> createMenuItemToAnalyticsParamMapping() {
        Map<Integer, String> menuIdToAnalyticsEvent = new HashMap<>();
        menuIdToAnalyticsEvent.put(R.id.action_sync,
                AnalyticsParamValue.ITEM_CONNECT_SYNC);
        menuIdToAnalyticsEvent.put(R.id.action_bell,
                AnalyticsParamValue.ITEM_NOTIFICATIONS_BELL);
        return menuIdToAnalyticsEvent;
    }

    public ConnectJobRecord getActiveJob() {
        return job;
    }
    public void setActiveJob(ConnectJobRecord job) {
        this.job = job;
    }

    private void retrieveMessages(){
        MessageManager.retrieveMessages(this, (success, error) -> {
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String analyticsParam = menuIdToAnalyticsParam.get(item.getItemId());
        if (analyticsParam == null) {
            analyticsParam = appActionsMenu.analyticsParam(item.getItemId());
        }
        FirebaseAnalyticsUtil.reportOptionsMenuItemClick(this.getClass(), analyticsParam);
        if (appActionsMenu.onItemSelected(item.getItemId())) {
            return true;
        }
        if (item.getItemId() == R.id.action_bell) {
            updateNotificationIcon();
            ConnectNavHelper.goToNotification(this);
            return true;
        }

        if(item.getItemId() == R.id.action_sync) {
            if (isShowingOpportunityHome() && areAppActionsAvailable()) {
                coordinator.getActions().syncIfOnline();
            }
            RefreshableFragment refreshable = getRefreshableFragment();
            if(refreshable != null) {
                refreshable.refresh(true);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (coordinator.onAppActionResult(requestCode, resultCode, intent)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onBackPressed() {
        if (!backButtonAndActionBarEnabled) {
            return;
        }
        super.onBackPressed();
    }

    /** Whether Opportunity Home is showing with nothing beneath it in the task. */
    public boolean isOpportunityHomeAtTaskBottom() {
        return isTaskRoot()
                && navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.connect_opportunity_home_fragment
                && navController.getPreviousBackStackEntry() == null;
    }

    /** Shows the active job's Opportunity Home as the only screen in the task. */
    public void openOpportunityHomeAtTaskBottom() {
        if (isTaskRoot()) {
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getId(), true)
                    .build();
            navController.navigate(R.id.connect_opportunity_home_fragment, null, options);
        } else {
            Intent opportunityHomeIntent =
                    ConnectNavHelper.buildOpportunityHomeTaskRootIntent(this, job.getJobUUID());
            startActivity(opportunityHomeIntent);
            finish();
        }
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.screen_connect;
    }

    @Override
    protected NavHostFragment getHostFragment() {
        return (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_connect);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        CustomProgressDialog syncDialog = coordinator.getSync().generateProgressDialog(taskId);
        if (syncDialog != null) {
            return syncDialog;
        }
        if (waitDialogEnabled) {
            return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
        }
        return null;
    }

    public void setBackButtonAndActionBarState(boolean enabled) {
        backButtonAndActionBarEnabled = enabled;
        invalidateOptionsMenu();
    }

    public void setWaitDialogEnabled(boolean enabled) {
        waitDialogEnabled = enabled;
    }

    private void updateBackButton() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(isBackEnabled());
            actionBar.setDisplayHomeAsUpEnabled(isBackEnabled());
        }
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    @Nullable
    private RefreshableFragment getRefreshableFragment() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect);
        Fragment currentFragment =
                navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
        if (currentFragment instanceof RefreshableFragment refreshable) {
            return refreshable;
        }
        return null;
    }

    /**
     * The coordinator this activity composes. Public so tests can reach the delegates it owns.
     */
    public HomeActivityCoordinator getCoordinator() {
        return coordinator;
    }

    @Override
    public SyncDelegate getReceiver() {
        return coordinator.getSync();
    }

    @NonNull
    @Override
    public CommCareActivity<?> getSyncTaskActivity() {
        return this;
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
    public void updateUiAfterDataPullOrSend(@NonNull String message, boolean success) {
        displayToast(message);
    }

    @Override
    public void onPullTaskFinished(@NonNull DataPullTask.PullTaskResult result) {
        coordinator.getAppHealth().onPullTaskFinished(result);
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
    public void deliverResultAndFinish(@NonNull Intent result) {
        HomeResultDelivery.deliverResultAndFinish(this, result);
    }

    @Override
    public void refreshActionSurface() {
        invalidateOptionsMenu();
    }

    @Override
    public void refreshHostUi() {
    }

    @Override
    public void onLocaleChanged() {
        refreshActionSurface();
    }

    @Override
    public boolean isDemoUser() {
        return false;
    }

    /**
     * App actions need a user logged in to the opportunity's app; without one the page still shows the
     * opportunity.
     */
    @Override
    public boolean areAppActionsAvailable() {
        return SeatedAppSessionKt.isUserSessionLive() && isSeatedAppForActiveJob();
    }

    private boolean isSeatedAppForActiveJob() {
        ConnectAppRecord seatedApp = ConnectJobUtils.getSeatedAppRecord();
        return job != null && seatedApp != null && job.getJobUUID().equals(seatedApp.getJobUUID());
    }

    /**
     * Unlike the session-gated homes, Connect doesn't need a session, so it stays open and drops the app
     * actions instead of redirecting to login.
     */
    @Override
    public void onSessionLost() {
        refreshActionSurface();
    }
}

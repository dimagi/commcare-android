package org.commcare.activities.connect;

import static org.commcare.connect.ConnectConstants.GO_TO_JOB_STATUS;
import static org.commcare.connect.ConnectConstants.NOTIFICATION_ID;
import static org.commcare.connect.ConnectConstants.REDIRECT_ACTION;
import static org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON;
import static org.commcare.personalId.PersonalIdFeatureFlagChecker.FeatureFlag.NOTIFICATIONS;
import static org.commcare.utils.NotificationUtil.getNotificationIcon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.base.Strings;

import org.apache.commons.lang3.StringUtils;
import org.commcare.activities.NavigationHostCommCareActivity;
import org.commcare.activities.PushNotificationActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectNavHelper;
import org.commcare.connect.MessageManager;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.connect.database.NotificationRecordDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.personalId.PersonalIdFeatureFlagChecker;
import org.commcare.pn.helper.NotificationBroadcastHelper;
import org.commcare.utils.FirebaseMessagingUtil;
import org.commcare.views.dialogs.CustomProgressDialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import kotlin.Unit;

public class ConnectActivity extends NavigationHostCommCareActivity<ConnectActivity> {
    private boolean backButtonAndActionBarEnabled = true;
    private boolean waitDialogEnabled = true;
    private String redirectionAction = "";
    private ConnectJobRecord job;
    private MenuItem messagingMenuItem = null;
    private MenuItem notificationsMenuItem = null;

    private static final int REQUEST_CODE_PERSONAL_ID_ACTIVITY = 1000;
    private Map<Integer, String> menuIdToAnalyticsParam;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.connect_title));

        PersonalIdManager personalIdManager = PersonalIdManager.getInstance();
        personalIdManager.init(this);

        if(personalIdManager.isloggedIn()){
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

            retrieveMessages();
        }else{
            Toast.makeText(this,R.string.personalid_not_login_from_fcm_error,Toast.LENGTH_LONG).show();
            personalIdManager.launchPersonalId(this,REQUEST_CODE_PERSONAL_ID_ACTIVITY);
            finish();
        }


    }

    private int getStartDestinationId(Bundle startArgs) {
        int startDestinationId = R.id.connect_jobs_list_fragment;
        if (getIntent().getBooleanExtra(GO_TO_JOB_STATUS, false)) {
            startDestinationId = handleInfoRedirect(startArgs);
        } else if (!Strings.isNullOrEmpty(redirectionAction)) {
            startDestinationId = handleSecureRedirect(startArgs);
        }
        return startDestinationId;
    }

    private void initStateFromExtras() {
        redirectionAction = getIntent().getStringExtra(REDIRECT_ACTION);
        int opportunityId = getIntent().getIntExtra(ConnectConstants.OPPORTUNITY_ID, -1);
        if (opportunityId == -1) {
            String opportunityIdStr = getIntent().getStringExtra(ConnectConstants.OPPORTUNITY_ID);
            if (!StringUtils.isEmpty(opportunityIdStr)) {
                opportunityId = Integer.parseInt(opportunityIdStr);
            }
        }
        if(opportunityId != -1) {
            job = ConnectJobUtils.getCompositeJob(this, opportunityId);
        }
    }

    private int handleInfoRedirect(Bundle startArgs) {
        Objects.requireNonNull(job);

        startArgs.putBoolean(SHOW_LAUNCH_BUTTON, getIntent().getBooleanExtra(SHOW_LAUNCH_BUTTON, true));

        return job.getStatus() == ConnectJobRecord.STATUS_DELIVERING
                ? R.id.connect_job_delivery_progress_fragment
                : R.id.connect_job_learning_progress_fragment;
    }

    private int handleSecureRedirect(Bundle startArgs) {
        //Entering from a notification, so we may need to initialize
        PersonalIdManager.getInstance().init(this);
        String notificationId = getIntent().getStringExtra(NOTIFICATION_ID);
        if (!TextUtils.isEmpty(notificationId)) {
            NotificationRecordDatabaseHelper.INSTANCE.updateReadStatus(this, notificationId, true);
            FirebaseAnalyticsUtil.reportNotificationEvent(
                    AnalyticsParamValue.NOTIFICATION_EVENT_TYPE_CLICK,
                    AnalyticsParamValue.REPORT_NOTIFICATION_CLICK_NOTIFICATION_TRAY,
                    redirectionAction,
                    notificationId
            );
        }
        startArgs.putString(REDIRECT_ACTION, redirectionAction);
        startArgs.putBoolean(SHOW_LAUNCH_BUTTON, getIntent().getBooleanExtra(SHOW_LAUNCH_BUTTON, true));

        return R.id.connect_unlock_fragment;
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        getSupportActionBar().setTitle(title);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connect, menu);

        MenuItem notification = menu.findItem(R.id.action_sync);
        notification.getIcon().setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_ATOP);

        notificationsMenuItem = menu.findItem(R.id.action_bell);
        notificationsMenuItem.setVisible(PersonalIdFeatureFlagChecker.isFeatureEnabled(NOTIFICATIONS));
        updateNotificationIcon();

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
        return super.onPrepareOptionsMenu(menu);
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
        MessageManager.retrieveMessages(this, success -> {
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FirebaseAnalyticsUtil.reportOptionsMenuItemClick(this.getClass(),
                menuIdToAnalyticsParam.get(item.getItemId()));
        if (item.getItemId() == R.id.action_bell) {
            updateNotificationIcon();
            ConnectNavHelper.goToNotification(this);
            return true;
        }

        if(item.getItemId() == R.id.action_sync) {
            RefreshableFragment refreshable = getRefreshableFragment();
            if(refreshable != null) {
                refreshable.refresh();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (backButtonAndActionBarEnabled) {
            super.onBackPressed();
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
}

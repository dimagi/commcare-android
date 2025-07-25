package org.commcare.activities.connect;

import static org.commcare.connect.ConnectConstants.GO_TO_JOB_STATUS;
import static org.commcare.connect.ConnectConstants.REDIRECT_ACTION;
import static org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.base.Strings;

import org.commcare.activities.NavigationHostCommCareActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectNavHelper;
import org.commcare.connect.MessageManager;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.utils.FirebaseMessagingUtil;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;

import java.util.Objects;

import javax.annotation.Nullable;

public class ConnectActivity extends NavigationHostCommCareActivity<ConnectActivity> {
    private boolean backButtonAndActionBarEnabled = true;
    private boolean waitDialogEnabled = true;
    private String redirectionAction = "";
    private ConnectJobRecord job;
    private MenuItem messagingMenuItem = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.connect_title));

        getIntentExtras();
        updateBackButton();

        // Wait for fragment to attach
        getSupportFragmentManager().executePendingTransactions();
        NavInflater inflater = navController.getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.nav_graph_connect);

        int startDestinationId = R.id.connect_jobs_list_fragment;
        Bundle startArgs = new Bundle();
        if (getIntent().getBooleanExtra(GO_TO_JOB_STATUS, false)) {
            startDestinationId = handleInfoRedirect(startArgs);
        } else if (!Strings.isNullOrEmpty(redirectionAction)) {
            startDestinationId = handleSecureRedirect(startArgs);
        }

        graph.setStartDestination(startDestinationId);
        navController.setGraph(graph, startArgs);

        retrieveMessages();
    }

    private void getIntentExtras() {
        redirectionAction = getIntent().getStringExtra(REDIRECT_ACTION);
        int opportunityId = getIntent().getIntExtra(ConnectConstants.OPPORTUNITY_ID, -1);
        if (opportunityId > 0) {
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
        Logger.log("ConnectActivity", "Redirecting to unlock fragment");

        //Entering from a notification, so we may need to initialize
        PersonalIdManager.getInstance().init(this);

        startArgs.putString(REDIRECT_ACTION, redirectionAction);
        startArgs.putBoolean(SHOW_LAUNCH_BUTTON, getIntent().getBooleanExtra(SHOW_LAUNCH_BUTTON, true));

        return R.id.connect_unlock_fragment;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMessagingIcon();

        LocalBroadcastManager.getInstance(this).registerReceiver(messagingUpdateReceiver,
                new IntentFilter(FirebaseMessagingUtil.MESSAGING_UPDATE_BROADCAST));
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messagingUpdateReceiver);
    }

    private final BroadcastReceiver messagingUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateMessagingIcon();
        }
    };

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

        messagingMenuItem = menu.findItem(R.id.action_messaging);
        updateMessagingIcon();

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_sync).setVisible(backButtonAndActionBarEnabled);
        menu.findItem(R.id.action_messaging).setVisible(backButtonAndActionBarEnabled);
        return super.onPrepareOptionsMenu(menu);
    }

    public ConnectJobRecord getActiveJob() {
        return job;
    }
    public void setActiveJob(ConnectJobRecord job) {
        this.job = job;
    }

    private void retrieveMessages(){
        MessageManager.retrieveMessages(this, success -> {
            updateMessagingIcon();
        });
    }

    public void updateMessagingIcon() {
        if(messagingMenuItem != null) {
            int icon = R.drawable.ic_connect_messaging_base;
            if(ConnectMessagingDatabaseHelper.getUnviewedMessages(this).size() > 0) {
                icon = R.drawable.ic_connect_messaging_unread;
            }
            messagingMenuItem.setIcon(ResourcesCompat.getDrawable(getResources(), icon, null));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_messaging) {
            ConnectNavHelper.INSTANCE.goToMessaging(this);
            return true;
        }

        if (item.getItemId() == R.id.action_credential) {
            startActivity(new Intent(this, PersonalIdCredentialActivity.class));
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

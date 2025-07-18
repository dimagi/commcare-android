package org.commcare.activities.connect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.base.Strings;

import org.commcare.activities.CommCareVerificationActivity;
import org.commcare.activities.NavigationHostCommCareActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.ConnectNavHelper;
import org.commcare.connect.MessageManager;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectDownloadingFragment;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.utils.FirebaseMessagingUtil;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;

import java.util.Objects;

import javax.annotation.Nullable;

public class ConnectActivity extends NavigationHostCommCareActivity<ResourceEngineListener> {
    private boolean backButtonAndActionBarEnabled = true;
    private boolean waitDialogEnabled = true;
    String redirectionAction = "";
    String opportunityId = "";
    MenuItem messagingMenuItem = null;

    final ActivityResultLauncher<Intent> verificationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    ConnectDownloadingFragment connectDownloadFragment = getConnectDownloadFragment();
                    if (connectDownloadFragment != null) {
                        connectDownloadFragment.onSuccessfulVerification();
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.connect_title));

        redirectionAction = getIntent().getStringExtra("action");
        opportunityId = getIntent().getStringExtra("opportunity_id");
        if (opportunityId == null) {
            opportunityId = "";
        }

        updateBackButton();

        // Wait for fragment to attach
        getSupportFragmentManager().executePendingTransactions();
        NavInflater inflater = navController.getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.nav_graph_connect);

        int startDestinationId = R.id.connect_jobs_list_fragment;
        Bundle startArgs = null;

        if (getIntent().getBooleanExtra("info", false)) {
            ConnectJobRecord job = ConnectJobHelper.INSTANCE.getActiveJob();
            Objects.requireNonNull(job);

            startDestinationId = job.getStatus() == ConnectJobRecord.STATUS_DELIVERING
                    ? R.id.connect_job_delivery_progress_fragment
                    : R.id.connect_job_learning_progress_fragment;

            boolean buttons = getIntent().getBooleanExtra("buttons", true);
            startArgs = new Bundle();
            startArgs.putBoolean("showLaunch", buttons);

        } else if (!Strings.isNullOrEmpty(redirectionAction)) {
            Logger.log("ConnectActivity", "Redirecting to unlock fragment");
            //Entering from a notification, so we may need to initialize
            PersonalIdManager.getInstance().init(this);
            startDestinationId = R.id.connect_unlock_fragment;
            startArgs = new Bundle();
            startArgs.putString("action", redirectionAction);
            startArgs.putString("opportunity_id", opportunityId);
            startArgs.putBoolean("buttons", getIntent().getBooleanExtra("buttons", true));
        }

        graph.setStartDestination(startDestinationId);
        navController.setGraph(graph, startArgs);

        retrieveMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMessagingIcon();

        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver,
                new IntentFilter(FirebaseMessagingUtil.MESSAGING_UPDATE_BROADCAST));
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver);
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
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

    @Override
    public ResourceEngineListener getReceiver() {
        return getConnectDownloadFragment();
    }

    @Nullable
    private ConnectDownloadingFragment getConnectDownloadFragment() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect);
        Fragment currentFragment =
                navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
        if (currentFragment instanceof ConnectDownloadingFragment) {
            return (ConnectDownloadingFragment) currentFragment;
        }
        return null;
    }

    public void startAppValidation() {
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_CONNECT, true);
        verificationLauncher.launch(i);
    }
}

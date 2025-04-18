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

import com.google.common.base.Strings;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareVerificationActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectDownloadingFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.services.CommCareFirebaseMessagingService;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;

import javax.annotation.Nullable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

public class ConnectActivity extends CommCareActivity<ResourceEngineListener> {
    private boolean backButtonAndActionBarEnabled = true;
    private boolean waitDialogEnabled = true;
    String redirectionAction = "";
    String opportunityId = "";
    NavController navController;
    MenuItem messagingMenuItem = null;

    NavController.OnDestinationChangedListener destinationListener = null;

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
        setContentView(R.layout.screen_connect);
        setTitle(getString(R.string.connect_title));

        redirectionAction = getIntent().getStringExtra("action");
        opportunityId = getIntent().getStringExtra("opportunity_id");
        if(opportunityId == null) {
            opportunityId = "";
        }

        updateBackButton();

        destinationListener = FirebaseAnalyticsUtil.getDestinationChangeListener();

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect);
        navController = host.getNavController();
        navController.addOnDestinationChangedListener(destinationListener);

        if (getIntent().getBooleanExtra("info", false)) {
            ConnectJobRecord job = ConnectManager.getActiveJob();
            int fragmentId = job.getStatus() == ConnectJobRecord.STATUS_DELIVERING ?
                    R.id.connect_job_delivery_progress_fragment :
                    R.id.connect_job_learning_progress_fragment;

            boolean buttons = getIntent().getBooleanExtra("buttons", true);

            Bundle bundle = new Bundle();
            bundle.putBoolean("showLaunch", buttons);

            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            navController.navigate(fragmentId, bundle, options);
        } else if (!Strings.isNullOrEmpty(redirectionAction)) {
            Logger.log("ConnectActivity", "Redirecting to unlock fragment");
            //Entering from a notification, so we may need to initialize
            ConnectManager.init(this);

            //Navigate to the unlock fragment first, then it will navigate on as desired
            NavOptions options = new NavOptions.Builder()
                    .setPopUpTo(navController.getGraph().getStartDestinationId(), true)
                    .build();
            Bundle bundle = new Bundle();
            bundle.putString("action", redirectionAction);
            bundle.putString("opportunity_id", opportunityId);
            bundle.putBoolean("buttons", getIntent().getBooleanExtra("buttons", true));
            navController.navigate(R.id.connect_unlock_fragment, bundle, options);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMessagingIcon();

        LocalBroadcastManager.getInstance(this).registerReceiver(updateReceiver,
                new IntentFilter(CommCareFirebaseMessagingService.MESSAGING_UPDATE_BROADCAST));
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
    protected void onActivityResult(int requestCode, int resultCode, @androidx.annotation.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ConnectManager.handleFinishedActivity(this, requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connect, menu);

        MenuItem notification = menu.findItem(R.id.action_sync);
        notification.getIcon().setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_ATOP);

        messagingMenuItem = menu.findItem(R.id.action_notification);
        updateMessagingIcon();

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_sync).setVisible(backButtonAndActionBarEnabled);
        menu.findItem(R.id.action_notification).setVisible(backButtonAndActionBarEnabled);
        return super.onPrepareOptionsMenu(menu);
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
        if (item.getItemId() == R.id.action_notification) {
            ConnectManager.goToMessaging(this);
            return true;
        }

        //NOTE: Fragments will handle the sync button individually (via MenuProviders)

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (backButtonAndActionBarEnabled) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (destinationListener != null) {
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_connect);
            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                navController.removeOnDestinationChangedListener(destinationListener);
            }
            destinationListener = null;
        }

        super.onDestroy();
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
        i.putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_SETTINGS, true);
        verificationLauncher.launch(i);
    }
}

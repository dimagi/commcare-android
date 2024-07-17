package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareVerificationActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectDownloadingFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.views.dialogs.CustomProgressDialog;

import javax.annotation.Nullable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

public class ConnectActivity extends CommCareActivity<ResourceEngineListener> {
    private boolean backButtonEnabled = true;
    private boolean waitDialogEnabled = true;

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
        updateBackButton();

        destinationListener = FirebaseAnalyticsUtil.getDestinationChangeListener();

        NavHostFragment host = (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect);
        NavController navController = host.getNavController();
        navController.addOnDestinationChangedListener(destinationListener);

        if(getIntent().getBooleanExtra("info", false)) {
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
        }
    }

    @Override
    public void onBackPressed() {
        if(backButtonEnabled) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if(destinationListener != null) {
            NavHostFragment navHostFragment = (NavHostFragment)getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_connect);
            if(navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                navController.removeOnDestinationChangedListener(destinationListener);
            }
            destinationListener = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        ConnectManager.handleFinishedActivity(requestCode, resultCode, intent);
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if(waitDialogEnabled) {
            return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
        }

        return null;
    }

    public void setBackButtonEnabled(boolean enabled) { backButtonEnabled = enabled; }
    public void setWaitDialogEnabled(boolean enabled) { waitDialogEnabled = enabled; }

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
                (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect);
        Fragment currentFragment =
                navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
        if (currentFragment instanceof ConnectDownloadingFragment) {
            return (ConnectDownloadingFragment)currentFragment;
        }
        return null;
    }

    public void startAppValidation() {
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_SETTINGS, true);
        verificationLauncher.launch(i);
    }
}

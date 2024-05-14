package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareVerificationActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectDownloadingFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.views.dialogs.CustomProgressDialog;

import javax.annotation.Nullable;

public class ConnectActivity extends CommCareActivity<ResourceEngineListener> {
    private boolean backButtonEnabled = true;
    private boolean waitDialogEnabled = true;

    NavController.OnDestinationChangedListener destinationListener = null;

    ActivityResultLauncher<Intent> verificationLauncher = registerForActivityResult(
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
        showBackButton();

        boolean showJobInfo = getIntent().getBooleanExtra("info", false);

        destinationListener = FirebaseAnalyticsUtil.getDestinationChangeListener();

        NavHostFragment host = (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connect);
        NavController navController = host.getNavController();
        navController.addOnDestinationChangedListener(destinationListener);

        NavGraph graph = navController.getNavInflater().inflate(R.navigation.nav_graph_connect);

        int startId = showJobInfo ? R.id.connect_job_intro_fragment : R.id.connect_jobs_list_fragment;
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if(showJobInfo && job != null) {
            switch(job.getStatus()) {
                case ConnectJobRecord.STATUS_AVAILABLE,
                        ConnectJobRecord.STATUS_AVAILABLE_NEW -> {
                    startId = R.id.connect_job_intro_fragment;
                }
                case ConnectJobRecord.STATUS_LEARNING -> {
                    startId = R.id.connect_job_learning_progress_fragment;
                }
                case ConnectJobRecord.STATUS_DELIVERING -> {
                    startId = R.id.connect_job_delivery_progress_fragment;
                }
            }
        }

        graph.setStartDestination(startId);
        navController.setGraph(graph);
    }

    @Override
    public void onBackPressed() {
        if(backButtonEnabled) {
            //Disable this handler and call again for default back behavior
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!ConnectManager.isUnlocked()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(destinationListener != null) {
            NavHostFragment navHostFragment = (NavHostFragment)getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment_connect);
            if(navHostFragment != null) {
                navHostFragment.getNavController()
                        .removeOnDestinationChangedListener(destinationListener);
            }
            destinationListener = null;
        }
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

    private void showBackButton() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if(isBackEnabled()){
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
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

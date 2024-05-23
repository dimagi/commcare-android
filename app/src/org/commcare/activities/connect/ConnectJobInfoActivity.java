package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareVerificationActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectDeliveryProgressFragment;
import org.commcare.fragments.connect.ConnectDownloadingFragment;
import org.commcare.fragments.connect.ConnectJobIntroFragment;
import org.commcare.fragments.connect.ConnectLearningProgressFragment;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.views.dialogs.CustomProgressDialog;

import javax.annotation.Nullable;

public class ConnectJobInfoActivity extends CommCareActivity<ConnectJobInfoActivity> {
    private boolean backButtonEnabled = true;
    private boolean waitDialogEnabled = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_connect_job_info);
        setTitle(getString(R.string.connect_title));
        updateBackButton();

        Fragment fragment = null;
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if(job != null) {
            switch(job.getStatus()) {
                case ConnectJobRecord.STATUS_LEARNING -> {
                    fragment = ConnectLearningProgressFragment.newInstance(false);
                }
                case ConnectJobRecord.STATUS_DELIVERING -> {
                    fragment = ConnectDeliveryProgressFragment.newInstance(false, false);
                }
            }
        }

        if(fragment == null) {
            fragment = ConnectJobIntroFragment.newInstance(true);
        }

        getSupportFragmentManager().beginTransaction()
                .add(R.id.connect_job_info_container, fragment).commit();
    }

    @Override
    public void onBackPressed() {
        if(backButtonEnabled) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
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
}

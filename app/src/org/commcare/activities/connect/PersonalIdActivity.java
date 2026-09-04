package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.NavigationHostCommCareActivity;
import org.commcare.fragments.personalId.BackupCodeWorkflow;
import org.commcare.fragments.personalId.EmailWorkFlow;
import org.commcare.fragments.personalId.PersonalIdBiometricConfigFragment;
import org.commcare.connect.ConnectConstants;
import org.commcare.dalvik.R;
import org.commcare.views.dialogs.CustomProgressDialog;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import org.commcare.fragments.personalId.PersonalIdEmailFragment;

public class PersonalIdActivity extends NavigationHostCommCareActivity<PersonalIdActivity> {

    public static final String EXTRA_EXISTING_USER_EMAIL_FLOW = "extra_existing_user_email_flow";
    public static final String EXTRA_PENDING_BACKUP_CODE = "extra_pending_backup_code";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkForEmailScreen();
        checkForPendingBackupCode();
        updateBackButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ConnectConstants.CONFIGURE_BIOMETRIC_REQUEST_CODE) {
            getCurrentFragment().handleFinishedPinActivity(requestCode, resultCode);
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    private PersonalIdBiometricConfigFragment getCurrentFragment() {
        NavHostFragment navHostFragment =
                (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connectid);
        Fragment currentFragment =
                navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
        if (currentFragment instanceof PersonalIdBiometricConfigFragment) {
            return (PersonalIdBiometricConfigFragment)currentFragment;
        }
        return null;
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.activity_connect_id;
    }

    @Override
    protected NavHostFragment getHostFragment() {
        NavHostFragment navHostFragment =
                (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connectid);
        return navHostFragment;
    }

    private void updateBackButton() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(isBackEnabled());
            actionBar.setDisplayHomeAsUpEnabled(isBackEnabled());
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected boolean shouldShowBreadcrumbBar() {
        return false;
    }

    private void checkForEmailScreen() {
        if (!getIntent().getBooleanExtra(EXTRA_EXISTING_USER_EMAIL_FLOW, false)) {
            return;
        }
        Bundle args = new Bundle();
        args.putSerializable(PersonalIdEmailFragment.ARG_EMAIL_WORKFLOW, EmailWorkFlow.EXISTING_USER);
        getHostFragment().getNavController().navigate(R.id.personalid_email, args,
                new NavOptions.Builder()
                        .setPopUpTo(R.id.personalid_phone_fragment, true)
                        .build());
    }

    private void checkForPendingBackupCode() {
        if (!getIntent().getBooleanExtra(EXTRA_PENDING_BACKUP_CODE, false)) {
            return;
        }
        Bundle args = new Bundle();
        args.putSerializable("workflow", BackupCodeWorkflow.RECOVERY_FORGOT_BACKUP_CODE);
        getHostFragment().getNavController().navigate(
                R.id.personalid_set_new_backup_code_fragment,
                args,
                new NavOptions.Builder()
                        .setPopUpTo(R.id.personalid_phone_fragment, true)
                        .build()
        );
    }
}


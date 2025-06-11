package org.commcare.activities.connect;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.commcare.activities.NavigationHostCommCareActivity;
import org.commcare.fragments.personalId.PersonalIdBiometricConfigFragment;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.PersonalIdManager;
import org.commcare.dalvik.R;
import org.commcare.fragments.personalId.PersonalIdPhoneFragmentDirections;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;

import java.util.Objects;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

public class PersonalIdActivity extends NavigationHostCommCareActivity<PersonalIdActivity> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleRedirection(getIntent());
        updateBackButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ConnectConstants.PERSONALID_UNLOCK_PIN
                || requestCode == ConnectConstants.CONFIGURE_BIOMETRIC_REQUEST_CODE) {
            //PIN unlock should only be requested while BiometricConfig fragment is active, else this will crash
            getCurrentFragment().handleFinishedPinActivity(requestCode, resultCode, data);
        } else if (requestCode == ConnectConstants.CONNECT_JOB_INFO) {
            handleRedirection(data);
        }
        if (requestCode == RESULT_OK) {
            finish();
        }
    }

    private void handleRedirection(Intent intent) {
        String value = intent.getStringExtra(ConnectConstants.TASK);
        if (ConnectConstants.BEGIN_REGISTRATION.equals(value)) {
            beginRegistration();
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

    private void beginRegistration() {
        NavDirections navDirections = null;

        switch (PersonalIdManager.getInstance().getStatus()) {
            case NotIntroduced, Registering:
                if (Objects.equals(getCurrentFragmentName(), "PersonalIdPhoneFragment")){
                    navDirections = PersonalIdPhoneFragmentDirections.actionPersonalidPhoneFragmentSelf();
                    navController.navigate(navDirections);
                    break;
                }
        }
        if (navDirections == null) {
            if (Objects.equals(getCurrentFragmentName(), "PersonalIdPhoneFragment")){
                navDirections = PersonalIdPhoneFragmentDirections
                        .actionPersonalidPhoneFragmentToPersonalidBiometricConfig();
                navController.navigate(navDirections);
            }
        }
    }

    public String getCurrentFragmentName() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_connectid);

        if (navHostFragment != null) {
            Fragment currentFragment = navHostFragment.getChildFragmentManager()
                    .getPrimaryNavigationFragment();

            if (currentFragment != null) {
                return currentFragment.getClass().getSimpleName();
            }
        }

        return "UnknownFragment";
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

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        getSupportActionBar().setTitle(title);
    }
}


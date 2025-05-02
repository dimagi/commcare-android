package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.commcare.fragments.connectId.ConnectIdBiometricConfigFragment;
import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.ConnectIDManager;
import org.commcare.fragments.connectId.ConnectIDSignupFragmentDirections;
import org.commcare.dalvik.R;
import org.commcare.views.dialogs.CustomProgressDialog;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

public class ConnectIdActivity extends CommCareActivity<ConnectIdActivity> {

    public boolean forgotPassword = false;
    public boolean forgotPin = false;
    public String recoverPhone;
    public String recoverSecret;
    public String recoveryAltPhone;
    private NavController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_id);
        controller = getHostFragment().getNavController();
        handleRedirection(getIntent());

        updateBackButton();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ConnectConstants.CONNECT_UNLOCK_PIN) {
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
        if (value != null) {
            switch (value) {
                case ConnectConstants.BEGIN_REGISTRATION -> beginRegistration(this);
                case ConnectConstants.VERIFY_PHONE -> beginSecondaryPhoneVerification(this);
            }
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        return CustomProgressDialog.newInstance(null, getString(R.string.please_wait), taskId);
    }

    private ConnectIdBiometricConfigFragment getCurrentFragment() {
        NavHostFragment navHostFragment =
                (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connectid);
        Fragment currentFragment =
                navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
        if (currentFragment instanceof ConnectIdBiometricConfigFragment) {
            return (ConnectIdBiometricConfigFragment)currentFragment;
        }
        return null;
    }

    private NavHostFragment getHostFragment() {
        NavHostFragment navHostFragment =
                (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connectid);
        return navHostFragment;
    }

    private void beginRegistration(Context parent) {
        forgotPassword = false;
        forgotPin = false;
        NavDirections navDirections = null;

        switch (ConnectIDManager.getInstance().getStatus()) {
            case NotIntroduced:
                navDirections = ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentSelf()
                        .setCallingClass(ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE);
                break;

            case Registering:
                ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(parent);
                int phase = user.getRegistrationPhase();

                switch (phase) {
                    case ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE:
                        navDirections = ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentSelf();
                        break;

                    case ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS:
                        navDirections = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidBiometricConfig(phase);
                        break;
                    case ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE:
                        navDirections = ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentToConnectidSecondaryPhoneFragment(
                                phase);
                        break;
                    case ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN:
                    case ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN:
                    case ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN:
                        navDirections = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidPin(
                                phase, user.getPrimaryPhone(), user.getPassword());
                        break;

                    case ConnectConstants.CONNECT_NO_ACTIVITY:
                        navDirections = ConnectIDSignupFragmentDirections
                                .actionConnectidPhoneFragmentToConnectidBiometricConfig(
                                        ConnectConstants.CONNECT_UNLOCK_BIOMETRIC);
                        break;
                }

                if (navDirections == null) {
                    if (user.shouldForcePin()) {
                        navDirections = ConnectIDSignupFragmentDirections.
                                actionConnectidPhoneFragmentToConnectidPin(
                                        ConnectConstants.CONNECT_UNLOCK_PIN,
                                        user.getPrimaryPhone(),
                                        user.getPassword());
                    } else if (user.shouldForcePassword()) {
                        navDirections = ConnectIDSignupFragmentDirections.
                                actionConnectidPhoneFragmentToConnectidPassword(
                                        user.getPrimaryPhone(),
                                        user.getPassword(), ConnectConstants.CONNECT_UNLOCK_PASSWORD);
                    } else {
                        navDirections = ConnectIDSignupFragmentDirections
                                .actionConnectidPhoneFragmentToConnectidBiometricConfig(
                                        ConnectConstants.CONNECT_UNLOCK_BIOMETRIC);
                    }
                }
                break;
        }

        if (navDirections != null) {
            controller.navigate(navDirections);
        }
    }


    private void updateBackButton() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(isBackEnabled());
            actionBar.setDisplayHomeAsUpEnabled(isBackEnabled());
        }
    }

    private void beginSecondaryPhoneVerification(Context parent) {
        NavDirections navDirections = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidMessage
                (parent.getString(R.string.connect_recovery_alt_title),
                        parent.getString(R.string.connect_recovery_alt_message),
                        ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE,
                        parent.getString(R.string.connect_password_fail_button),
                        parent.getString(R.string.connect_recovery_alt_change_button), null, null);
        controller.navigate(navDirections);
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

    public void reset() {
        recoverPhone = null;
        recoveryAltPhone = null;
        recoverSecret = null;
        forgotPassword = false;
        forgotPin = false;
    }
}


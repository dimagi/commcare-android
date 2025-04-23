package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.dalvik.R;
import org.commcare.fragments.connectId.ConnectIDSignupFragmentDirections;
import org.commcare.fragments.connectId.ConnectIdBiometricConfigFragment;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import org.commcare.utils.CommCareNavController;
import org.commcare.views.dialogs.CustomProgressDialog;

public class ConnectIdActivity extends CommCareActivity<ConnectIdActivity> {

    public static boolean forgotPassword = false;
    public static boolean forgotPin = false;
    public static String recoverPhone;
    public static String recoverSecret;
    public static String recoveryAltPhone;
    private NavController controller;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_id);
        NavHostFragment host2 = (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connectid);
        controller = host2.getNavController();
        handleRedirection(getIntent());
    }

    private void handleRedirection(Intent intent) {
        String value = intent.getStringExtra("TASK");
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

    public void beginRegistration(Context parent) {
        forgotPassword = false;
        forgotPin = false;
        NavDirections navDirections = null;
        switch (ConnectManager.getStatus()) {
            case NotIntroduced :
                navDirections = ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentSelf()
                        .setCallingClass(ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE);
                break;
            case Registering :
                ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(parent);
                int phase = user.getRegistrationPhase();
                if (phase != ConnectConstants.CONNECT_NO_ACTIVITY) {
                    switch(phase) {
                        case ConnectConstants.CONNECT_REGISTRATION_PRIMARY_PHONE:
                            navDirections = ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentSelf();
                            break;
                        case ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS:
                            navDirections = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidBiometricConfig(phase);
                            break;
//                        case ConnectConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> fragmentId = R.id.connectid_phone_verify;
//                        case ConnectConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> fragmentId = R.id.connectid_phoneNo;
                        case ConnectConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE :
                            navDirections = ConnectIDSignupFragmentDirections.actionConnectidSignupFragmentToConnectidSecondaryPhoneFragment(
                                    phase, ConnectConstants.METHOD_CHANGE_ALTERNATE, "");
                            break;
                        case ConnectConstants.CONNECT_REGISTRATION_CONFIGURE_PIN:
                        case ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN:
                        case ConnectConstants.CONNECT_REGISTRATION_CHANGE_PIN:
                            navDirections = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidPin(
                                    phase, user.getPrimaryPhone(), user.getPassword());
                            break;
                    }
                } else if (user.shouldForcePin()) {
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
                                    (ConnectConstants.CONNECT_UNLOCK_BIOMETRIC));
                }
                break;
        }

        CommCareNavController.navigateSafely(controller,navDirections);

    }

    private void beginSecondaryPhoneVerification(Context parent) {
        NavDirections navDirections = ConnectIDSignupFragmentDirections.actionConnectidPhoneFragmentToConnectidMessage
                (parent.getString(R.string.connect_recovery_alt_title),
                        parent.getString(R.string.connect_recovery_alt_message),
                        ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE,
                        parent.getString(R.string.connect_password_fail_button),
                        parent.getString(R.string.connect_recovery_alt_change_button),null,null);
        CommCareNavController.navigateSafely(controller,navDirections);
    }

    public static void reset() {
        recoverPhone = null;
        recoveryAltPhone = null;
        recoverSecret = null;
        forgotPassword = false;
        forgotPin = false;
    }
}



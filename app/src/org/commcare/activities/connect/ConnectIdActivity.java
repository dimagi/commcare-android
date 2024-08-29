package org.commcare.activities.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.fragments.connectId.ConnectIdBiometricConfigFragment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections;


public class ConnectIdActivity extends AppCompatActivity {

    public static boolean forgotPassword = false;
    public static boolean forgotPin = false;
    public static String recoverPhone;
    public static String recoverSecret;
    public static String recoveryAltPhone;
    public static NavController controller;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ConnectConstants.CONNECT_UNLOCK_PIN) {
            getCurrentFragment().onActivityResult(requestCode, resultCode, data);
        }
        if (requestCode == ConnectConstants.CONNECTID_REQUEST_CODE) {
            String value = "";
            if (data != null) {
                value = data.getStringExtra("TASK");
            }
            switch (value) {
                case ConnectConstants.BEGIN_REGISTRATION -> beginRegistration(this);
                case ConnectConstants.UNLOCK_CONNECT -> unlockConnect(this);
                case ConnectConstants.VERIFY_PHONE -> beginSecondaryPhoneVerification(this);
            }
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
        Bundle extras = getIntent().getExtras();

        String value = "";
        if (extras != null) {
            value = extras.getString("TASK");
        }
        switch (value) {
            case ConnectConstants.BEGIN_REGISTRATION -> beginRegistration(this);
            case ConnectConstants.UNLOCK_CONNECT -> unlockConnect(this);
            case ConnectConstants.VERIFY_PHONE -> beginSecondaryPhoneVerification(this);
        }
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

    public static void beginRegistration(Context parent) {
        forgotPassword = false;
        forgotPin = false;
        NavDirections navDirections = null;
        int requestCode = ConnectConstants.CONNECT_NO_ACTIVITY;
        switch (ConnectManager.getStatus()) {
            case NotIntroduced ->
                    navDirections = ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionSelf();
            case Registering -> {
                ConnectUserRecord user = ConnectDatabaseHelper.getUser(parent);
                int phase = user.getRegistrationPhase();
                if (phase != ConnectConstants.CONNECT_NO_ACTIVITY) {
                    requestCode = phase;
                } else if (user.shouldForcePin()) {
                    navDirections = ConnectIdRecoveryDecisionFragmentDirections.
                            actionConnectidRecoveryDecisionToConnectidPin(
                                    ConnectConstants.CONNECT_UNLOCK_PIN,
                                    user.getPrimaryPhone(),
                                    user.getPassword());
                } else if (user.shouldForcePassword()) {
                    navDirections = ConnectIdRecoveryDecisionFragmentDirections.
                            actionConnectidRecoveryDecisionToConnectidPassword
                                    (ConnectConstants.CONNECT_UNLOCK_PASSWORD,
                                            user.getPrimaryPhone(),
                                            user.getPassword());
                } else {
                    navDirections = ConnectIdRecoveryDecisionFragmentDirections
                            .actionConnectidRecoveryDecisionToConnectidBiometricConfig
                                    (user.getPrimaryPhone(),
                                            ConnectConstants.CONNECT_UNLOCK_BIOMETRIC)
                            .setAllowPassword(true);
                }
            }
            default -> {
            }
        }

        if (navDirections != null && requestCode != ConnectConstants.CONNECT_NO_ACTIVITY) {
            controller.navigate(navDirections);
        }
    }

    public static void unlockConnect(Context parent) {
        forgotPassword = false;
        forgotPin = false;
        NavDirections navDirections = null;
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(parent);
        navDirections = ConnectIdRecoveryDecisionFragmentDirections.
                actionConnectidRecoveryDecisionToConnectidBiometricConfig
                        (user.getPrimaryPhone(),
                                ConnectConstants.CONNECT_UNLOCK_BIOMETRIC)
                .setAllowPassword(true);
        if (user.shouldForcePin()) {
            navDirections = ConnectIdRecoveryDecisionFragmentDirections
                    .actionConnectidRecoveryDecisionToConnectidPin
                            (ConnectConstants.CONNECT_UNLOCK_PIN,
                                    user.getPrimaryPhone(),
                                    user.getPassword());
        } else if (user.shouldForcePassword()) {
            navDirections = ConnectIdRecoveryDecisionFragmentDirections.
                    actionConnectidRecoveryDecisionToConnectidPassword
                            (ConnectConstants.CONNECT_UNLOCK_PASSWORD,
                                    user.getPrimaryPhone(),
                                    user.getPassword());
        }

        if (navDirections != null) {
            controller.navigate(navDirections);
        }
    }

    public static void beginSecondaryPhoneVerification(Context parent) {
        NavDirections navDirections = ConnectIdRecoveryDecisionFragmentDirections.
                actionConnectidRecoveryDecisionToConnectidMessage
                (parent.getString(R.string.connect_recovery_alt_title),
                        parent.getString(R.string.connect_recovery_alt_message),
                        ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE,
                        parent.getString(R.string.connect_password_fail_button),
                        parent.getString(R.string.connect_recovery_alt_change_button));
        controller.navigate(navDirections);

    }

    public static void reset() {
        recoverPhone = null;
        recoveryAltPhone = null;
        recoverSecret = null;
        forgotPassword = false;
        forgotPin = false;
    }
}



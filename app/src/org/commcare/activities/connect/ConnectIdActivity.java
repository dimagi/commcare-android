package org.commcare.activities.connect;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.ConnectTask;
import org.commcare.dalvik.R;
import org.commcare.fragments.connect.ConnectDownloadingFragment;
import org.commcare.fragments.connectId.ConnectIdBiometricConfigFragment;

import static org.commcare.connect.ConnectIdWorkflows.completeSignIn;

public class ConnectIdActivity extends AppCompatActivity {
    static NavController controller;
    static NavDirections navDirections = null;
    public static boolean forgotPassword = false;
    public static boolean forgotPin = false;
    public static String recoverPhone;
    public static String recoverSecret;
    public static String recoveryAltPhone;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==ConnectTask.CONNECT_UNLOCK_PIN.getRequestCode()) {
            getCurrentFragment().onActivityResult(requestCode, resultCode, data);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_id);
        NavHostFragment host2 = (NavHostFragment)getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connectid);
        controller = host2.getNavController();
        Bundle extras = getIntent().getExtras();

        String value="";
        if (extras != null) {
            value = extras.getString("TASK");
        }
        switch (value){
            case ConnectConstants.BIGIN_REGISTRATION -> beginRegistration1(this);
            case ConnectConstants.UNLOCK_CONNECT -> unlockConnect(this);
            case ConnectConstants.VERIFY_PHONE -> beginSecondaryPhoneVerification(this);
        }
    }

//    @Override
//    public void onBackPressed() {
//
//        int count = getSupportFragmentManager().getBackStackEntryCount();
//
//        if (count == 0) {
//            super.onBackPressed();
//            //additional code
//        } else {
//            getSupportFragmentManager().popBackStack();
//        }
//
//    }

    private ConnectIdBiometricConfigFragment getCurrentFragment() {
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_connectid);
        Fragment currentFragment =
                navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
        if (currentFragment instanceof ConnectIdBiometricConfigFragment) {
            return (ConnectIdBiometricConfigFragment) currentFragment;
        }
        return null;
    }

    public static void beginRegistration1(Context parent) {
        forgotPassword = false;
        forgotPin = false;
        ConnectTask requestCode = ConnectTask.CONNECT_NO_ACTIVITY;
        switch (ConnectManager.getStatus()) {
            case NotIntroduced ->
                    navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionSelf();
            case Registering -> {
                ConnectUserRecord user = ConnectDatabaseHelper.getUser(parent);
                ConnectTask phase = user.getRegistrationPhase();
                if (phase != ConnectTask.CONNECT_NO_ACTIVITY) {
                    requestCode = phase;
                } else if (user.shouldForcePin()) {
                    navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidPin(ConnectConstants.CONNECT_UNLOCK_PIN, user.getPrimaryPhone(), user.getPassword());
                } else if (user.shouldForcePassword()) {
                    navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidPassword(ConnectConstants.CONNECT_UNLOCK_PASSWORD, user.getPrimaryPhone(), user.getPassword());
                } else {
                    navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidBiometricConfig(user.getPrimaryPhone(), ConnectConstants.CONNECT_UNLOCK_BIOMETRIC);
                }
            }
            default -> {
                //Error, should never get here
            }
        }

        if (requestCode != ConnectTask.CONNECT_NO_ACTIVITY) {
            if (navDirections != null) {
                controller.navigate(navDirections);
            }
        }else{
            navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionSelf();
            controller.navigate(navDirections);
        }
    }

    public static void unlockConnect(Context parent) {
        forgotPassword = false;
        forgotPin = false;

        ConnectUserRecord user = ConnectDatabaseHelper.getUser(parent);
        navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidBiometricConfig(user.getPrimaryPhone(), ConnectConstants.CONNECT_UNLOCK_BIOMETRIC);
        if (user.shouldForcePin()) {
            navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidPin(ConnectConstants.CONNECT_UNLOCK_PIN, user.getPrimaryPhone(), user.getPassword());
        } else if (user.shouldForcePassword()) {
            navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidPassword(ConnectConstants.CONNECT_UNLOCK_PASSWORD, user.getPrimaryPhone(), user.getPassword());
        }

        if (navDirections != null) {
            controller.navigate(navDirections);
        }
    }

    private ConnectTask completeUnlock(Context parent) {
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(parent);
        if (user.shouldRequireSecondaryPhoneVerification()) {
            return ConnectTask.CONNECT_UNLOCK_ALT_PHONE_MESSAGE;
        } else {
            completeSignIn();
        }

        return ConnectTask.CONNECT_NO_ACTIVITY;
    }

    public static void beginSecondaryPhoneVerification(Context parent) {
        navDirections = org.commcare.fragments.connectId.ConnectIdRecoveryDecisionFragmentDirections.actionConnectidRecoveryDecisionToConnectidMessage(parent.getString(R.string.connect_recovery_alt_title), parent.getString(R.string.connect_recovery_alt_message), ConnectConstants.CONNECT_VERIFY_ALT_PHONE_MESSAGE, parent.getString(R.string.connect_password_fail_button), parent.getString(R.string.connect_recovery_alt_change_button));
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



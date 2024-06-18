package org.commcare.connect;

import android.app.Activity;
import android.content.Intent;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.SettingsHelper;
import org.commcare.activities.connect.ConnectIdPhoneVerificationActivity;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ConnectIdWorkflows {
    private static CommCareActivity<?> parentActivity;
    private static ConnectManager.ConnectActivityCompleteListener listener;
    private static ConnectTask phase = ConnectTask.CONNECT_NO_ACTIVITY;

    //Only used for remembering the phone number between the first and second registration screens
    private static String primaryPhone = null;

    private static String recoveryPhone = null;
    private static String recoveryAltPhone = null;
    private static String recoverySecret = null;
    private static boolean forgotPassword = false;
    private static boolean forgotPin = false;
    
    public static void reset() {
        phase = ConnectTask.CONNECT_NO_ACTIVITY;
        primaryPhone = null;
        recoveryPhone = null;
        recoveryAltPhone = null;
        recoverySecret = null;
        forgotPassword = false;
        forgotPin = false;
    }
    
    public static void beginRegistration(CommCareActivity<?> parent, ConnectManager.ConnectIdStatus status, ConnectManager.ConnectActivityCompleteListener callback) {
        parentActivity = parent;
        listener = callback;
        forgotPassword = false;
        forgotPin = false;

        ConnectTask requestCode = ConnectTask.CONNECT_NO_ACTIVITY;
        switch (status) {
            case NotIntroduced -> {
                requestCode = ConnectTask.CONNECT_REGISTER_OR_RECOVER_DECISION;
            }
            case Registering -> {
                ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
                ConnectTask phase = user.getRegistrationPhase();
                if (phase != ConnectTask.CONNECT_NO_ACTIVITY) {
                    requestCode = phase;
                } else if (user.shouldForcePin()) {
                    requestCode = ConnectTask.CONNECT_UNLOCK_PIN;
                }else if (user.shouldForcePassword()) {
                    requestCode = ConnectTask.CONNECT_UNLOCK_PASSWORD;
                } else {
                    requestCode = ConnectTask.CONNECT_UNLOCK_BIOMETRIC;
                }
            }
            default -> {
                //Error, should never get here
            }
        }

        if (requestCode != ConnectTask.CONNECT_NO_ACTIVITY) {
            phase = requestCode;
            continueWorkflow();
        }
    }

    public static void beginSecondaryPhoneVerification(CommCareActivity<?> parent, ConnectManager.ConnectActivityCompleteListener callback) {
        parentActivity = parent;
        listener = callback;

        phase = ConnectTask.CONNECT_VERIFY_ALT_PHONE_MESSAGE;

        continueWorkflow();
    }

    public static void unlockConnect(CommCareActivity<?> parent, ConnectManager.ConnectActivityCompleteListener callback) {
        parentActivity = parent;
        listener = callback;
        forgotPassword = false;
        forgotPin = false;

        ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);

        phase = ConnectTask.CONNECT_UNLOCK_BIOMETRIC;
        if (user.shouldForcePin()) {
            phase = ConnectTask.CONNECT_UNLOCK_PIN;
        } else if (user.shouldForcePassword()) {
            phase = ConnectTask.CONNECT_UNLOCK_PASSWORD;
        }

        continueWorkflow();
    }

    private static void continueWorkflow() {
        //Determine activity to launch for next phase
        Class<?> nextActivity = phase.getNextActivity();
        Map<String, Serializable> params = new HashMap<>();
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);

        switch (phase) {
            case CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, ConnectConstants.METHOD_REGISTER_PRIMARY);
                params.put(ConnectConstants.PHONE, primaryPhone);
            }
            case CONNECT_REGISTRATION_MAIN -> {
                params.put(ConnectConstants.PHONE, primaryPhone);
            }
            case CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRegistrationPrimary));
                params.put(ConnectConstants.PHONE, user.getPrimaryPhone());
                params.put(ConnectConstants.CHANGE, "true");
                params.put(ConnectConstants.USERNAME, user.getUserId());
                params.put(ConnectConstants.PASSWORD, user.getPassword());
            }
            case CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, ConnectConstants.METHOD_CHANGE_PRIMARY);
            }
            case CONNECT_REGISTRATION_CONFIGURE_PIN,
                    CONNECT_REGISTRATION_CHANGE_PIN -> {
                params.put(ConnectConstants.PHONE, user.getPrimaryPhone());
                params.put(ConnectConstants.SECRET, "");
                params.put(ConnectConstants.RECOVER, false);
                params.put(ConnectConstants.CHANGE, true);
            }
            case CONNECT_REGISTRATION_CONFIRM_PIN -> {
                params.put(ConnectConstants.PHONE, user.getPrimaryPhone());
                params.put(ConnectConstants.SECRET, "");
                params.put(ConnectConstants.RECOVER, false);
                params.put(ConnectConstants.CHANGE, false);
            }
            case CONNECT_REGISTRATION_ALTERNATE_PHONE,
                    CONNECT_VERIFY_ALT_PHONE_CHANGE,
                    CONNECT_UNLOCK_ALT_PHONE_CHANGE-> {
                params.put(ConnectConstants.METHOD, ConnectConstants.METHOD_CHANGE_ALTERNATE);
            }
            case CONNECT_REGISTRATION_SUCCESS -> {
                //Show message screen indicating success
                params.put(ConnectConstants.TITLE, R.string.connect_register_success_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_register_success_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_register_success_button);
            }
            case CONNECT_RECOVERY_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, ConnectConstants.METHOD_RECOVER_PRIMARY);
            }
            case CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                params.put(ConnectConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRecoveryPrimary));
                params.put(ConnectConstants.PHONE, recoveryPhone);
                params.put(ConnectConstants.CHANGE, "false");
                params.put(ConnectConstants.USERNAME, recoveryPhone);
                params.put(ConnectConstants.PASSWORD, "");
            }
            case CONNECT_RECOVERY_VERIFY_PASSWORD -> {
                params.put(ConnectConstants.PHONE, recoveryPhone);
                params.put(ConnectConstants.SECRET, recoverySecret);
            }
            case CONNECT_RECOVERY_VERIFY_PIN -> {
                params.put(ConnectConstants.PHONE, recoveryPhone);
                params.put(ConnectConstants.SECRET, recoverySecret);
                params.put(ConnectConstants.RECOVER, true);
                params.put(ConnectConstants.CHANGE, false);
            }
            case CONNECT_RECOVERY_CHANGE_PIN -> {
                params.put(ConnectConstants.PHONE, recoveryPhone);
                params.put(ConnectConstants.SECRET, recoverySecret);
                params.put(ConnectConstants.RECOVER, true);
                params.put(ConnectConstants.CHANGE, true);
            }
            case CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                //Show message screen indicating plan to use alt phone
                params.put(ConnectConstants.TITLE, R.string.connect_recovery_alt_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_recovery_alt_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_recovery_alt_button);
            }
            case CONNECT_VERIFY_ALT_PHONE_MESSAGE,
                    CONNECT_UNLOCK_ALT_PHONE_MESSAGE -> {
                //Show message screen indicating plan to use alt phone
                params.put(ConnectConstants.TITLE, R.string.connect_recovery_alt_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_recovery_alt_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_recovery_alt_button);
                params.put(ConnectConstants.BUTTON2, R.string.connect_recovery_alt_change_button);
            }
            case CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                params.put(ConnectConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRecoveryAlternate));
                params.put(ConnectConstants.PHONE, null);
                params.put(ConnectConstants.CHANGE, "false");
                params.put(ConnectConstants.USERNAME, recoveryPhone);
                params.put(ConnectConstants.PASSWORD, recoverySecret);
                params.put(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE, recoveryAltPhone);
            }
            case CONNECT_VERIFY_ALT_PHONE, CONNECT_UNLOCK_VERIFY_ALT_PHONE -> {
                params.put(ConnectConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodVerifyAlternate));
                params.put(ConnectConstants.PHONE, null);
                params.put(ConnectConstants.CHANGE, "false");
                params.put(ConnectConstants.USERNAME, user.getUserId());
                params.put(ConnectConstants.PASSWORD, user.getPassword());
            }
            case CONNECT_RECOVERY_SUCCESS -> {
                //Show message screen indicating success
                params.put(ConnectConstants.TITLE, R.string.connect_recovery_success_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_recovery_success_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_recovery_success_button);
            }
            case CONNECT_UNLOCK_BIOMETRIC -> {
                params.put(ConnectConstants.ALLOW_PASSWORD, "true");
            }
            case CONNECT_REGISTRATION_UNLOCK_BIOMETRIC, CONNECT_RECOVERY_UNLOCK_BIOMETRIC -> {
                params.put(ConnectConstants.ALLOW_PASSWORD, "false");
            }
            case CONNECT_BIOMETRIC_ENROLL_FAIL -> {
                params.put(ConnectConstants.TITLE, R.string.connect_biometric_enroll_fail_title);
                params.put(ConnectConstants.MESSAGE, R.string.connect_biometric_enroll_fail_message);
                params.put(ConnectConstants.BUTTON, R.string.connect_biometric_enroll_fail_button);
            }
        }

        if (nextActivity != null) {
            Intent i = new Intent(parentActivity, nextActivity);

            for (Map.Entry<String, Serializable> pair : params.entrySet()) {
                i.putExtra(pair.getKey(), pair.getValue());
            }

            parentActivity.startActivityForResult(i, phase.getRequestCode());
        }
    }

    public static boolean handleFinishedActivity(int requestCode, int resultCode, Intent intent) {
        boolean flagStartOfRegistration = false;
        boolean success = resultCode == Activity.RESULT_OK;
        ConnectTask nextRequestCode = ConnectTask.CONNECT_NO_ACTIVITY;
        boolean rememberPhase = false;
        boolean launchSecuritySettings = false;

        ConnectTask task = ConnectTask.fromRequestCode(requestCode);
        switch (task) {
            case CONNECT_REGISTER_OR_RECOVER_DECISION -> {
                if (success) {
                    boolean createNew = intent.getBooleanExtra(ConnectConstants.CREATE, false);

                    if (createNew) {
                        nextRequestCode = ConnectTask.CONNECT_REGISTRATION_CONSENT;
                    } else {
                        nextRequestCode = ConnectTask.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS;
                        recoveryPhone = intent.getStringExtra(ConnectConstants.PHONE);
                    }
                }
            }
            case CONNECT_REGISTRATION_CONSENT -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_PRIMARY_PHONE :
                        ConnectTask.CONNECT_NO_ACTIVITY;
            }
            case CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_MAIN :
                        ConnectTask.CONNECT_REGISTRATION_CONSENT;
                if (success) {
                    primaryPhone = intent.getStringExtra(ConnectConstants.PHONE);

                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
                    if (user != null) {
                        user.setPrimaryPhone(primaryPhone);
                        ConnectDatabaseHelper.storeUser(parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_MAIN -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS :
                        ConnectTask.CONNECT_REGISTRATION_PRIMARY_PHONE;
                if (success) {
                    ConnectUserRecord user = ConnectUserRecord.getUserFromIntent(intent);
                    ConnectUserRecord dbUser = ConnectDatabaseHelper.getUser(parentActivity);
                    if (dbUser != null) {
                        dbUser.setName(user.getName());
                        dbUser.setAlternatePhone(user.getAlternatePhone());
                        user = dbUser;
                    } else {
                        flagStartOfRegistration = true;
                    }
                    ConnectDatabaseHelper.storeUser(parentActivity, user);
                    rememberPhase = true;
                }
            }
            case CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS -> {
                //Backing up here is problematic, we just created a new account...
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_MAIN;
                if (success) {
                    boolean failedEnrollment = intent.getBooleanExtra(ConnectConstants.ENROLL_FAIL, false);
                    nextRequestCode = failedEnrollment ? ConnectTask.CONNECT_BIOMETRIC_ENROLL_FAIL :
                            ConnectTask.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC;
                }
            }
            case CONNECT_BIOMETRIC_ENROLL_FAIL -> {
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                if (success) {
                    //Go to settings
                    launchSecuritySettings = true;
                }
            }
            case CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE :
                        ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                rememberPhase = success;
            }
            case CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                if (success) {
                    boolean changeNumber = intent != null && intent.getBooleanExtra(ConnectConstants.CHANGE,
                            false);

                    nextRequestCode = changeNumber ? ConnectTask.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE :
                            ConnectTask.CONNECT_REGISTRATION_CONFIGURE_PIN;
                    rememberPhase = !changeNumber;
                }
            }
            case CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                //Note that we return to primary phone verification
                // (whether they did or didn't change the phone number)
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if (success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
                    if (user != null) {
                        user.setPrimaryPhone(intent.getStringExtra(ConnectConstants.PHONE));
                        ConnectDatabaseHelper.storeUser(parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_CONFIGURE_PIN -> {
                rememberPhase = success;
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_ALTERNATE_PHONE :
                        ConnectTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;

                if(success) {
                    forgotPin = false;
                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
                    if (user != null) {
                        user.setPin(intent.getStringExtra(ConnectConstants.PIN));
                        user.setLastPinDate(new Date());
                        ConnectDatabaseHelper.storeUser(parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
                rememberPhase = success;
                nextRequestCode = success ? ConnectTask.CONNECT_REGISTRATION_CONFIRM_PIN :
                        ConnectTask.CONNECT_REGISTRATION_CONFIGURE_PIN;
                if (success) {
                    user.setAlternatePhone(intent.getStringExtra(ConnectConstants.PHONE));
                    ConnectDatabaseHelper.storeUser(parentActivity, user);
                }
            }
            case CONNECT_REGISTRATION_CONFIRM_PIN -> {
                rememberPhase = success;
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_ALTERNATE_PHONE;
                if(success) {
                    forgotPin = intent.getBooleanExtra(ConnectConstants.FORGOT, false);
                    nextRequestCode = forgotPin ? ConnectTask.CONNECT_REGISTRATION_CHANGE_PIN :
                            ConnectTask.CONNECT_REGISTRATION_SUCCESS;
                }
            }
            case CONNECT_REGISTRATION_CHANGE_PIN -> {
                rememberPhase = success;
                nextRequestCode = ConnectTask.CONNECT_REGISTRATION_CONFIRM_PIN;
                forgotPin = false;
            }
            case CONNECT_RECOVERY_PRIMARY_PHONE -> {
                if (success) {
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_CONFIGURE_BIOMETRICS;
                    recoveryPhone = intent.getStringExtra(ConnectConstants.PHONE);
                }
            }
            case CONNECT_RECOVERY_CONFIGURE_BIOMETRICS -> {
                if (success) {
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_UNLOCK_BIOMETRIC;
                }
            }
            case CONNECT_RECOVERY_UNLOCK_BIOMETRIC -> {
                if (success) {
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                }
            }
            case CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    if(intent.hasExtra(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE)) {
                        recoveryAltPhone = intent.getStringExtra(ConnectConstants.CONNECT_KEY_SECONDARY_PHONE);
                    }

                    //First try PIN, then password, then secondary OTP
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_VERIFY_PIN;
                    if(forgotPin) {
                        nextRequestCode = forgotPassword ? ConnectTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE :
                                ConnectTask.CONNECT_RECOVERY_VERIFY_PASSWORD;
                    }

                    //Remember the secret key for use through the rest of the recovery process
                    recoverySecret = intent.getStringExtra(ConnectConstants.SECRET);
                }
            }
            case CONNECT_RECOVERY_VERIFY_PIN -> {
                nextRequestCode = ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    forgotPin = intent.getBooleanExtra(ConnectConstants.FORGOT, false);
                    if (forgotPin) {
                        nextRequestCode = forgotPassword ? ConnectTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE :
                                ConnectTask.CONNECT_RECOVERY_VERIFY_PASSWORD;
                    } else {
                        nextRequestCode = ConnectTask.CONNECT_RECOVERY_SUCCESS;
                    }
                }
            }
            case CONNECT_RECOVERY_VERIFY_PASSWORD -> {
                nextRequestCode = success ? ConnectTask.CONNECT_RECOVERY_CHANGE_PIN :
                        ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    forgotPassword = intent.getBooleanExtra(ConnectConstants.FORGOT, false);
                    if (forgotPassword) {
                        nextRequestCode = ConnectTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE;
                    }
                }
            }
            case CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                if (success) {
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_VERIFY_ALT_PHONE;
                }
            }
            case CONNECT_VERIFY_ALT_PHONE_MESSAGE -> {
                if (success) {
                    boolean change = intent.getBooleanExtra(ConnectConstants.BUTTON2, false);

                    nextRequestCode = change ? ConnectTask.CONNECT_VERIFY_ALT_PHONE_CHANGE : ConnectTask.CONNECT_VERIFY_ALT_PHONE;
                }
            }
            case CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                nextRequestCode = success ? ConnectTask.CONNECT_RECOVERY_CHANGE_PIN :
                        ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
            }
            case CONNECT_VERIFY_ALT_PHONE_CHANGE -> {
                nextRequestCode = success ? ConnectTask.CONNECT_VERIFY_ALT_PHONE : ConnectTask.CONNECT_VERIFY_ALT_PHONE_MESSAGE;
            }
            case CONNECT_VERIFY_ALT_PHONE -> {
                if(success) {
                    completeSignin();
                }
            }
            case CONNECT_RECOVERY_CHANGE_PIN -> {
                nextRequestCode = success ? ConnectTask.CONNECT_RECOVERY_SUCCESS :
                        ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    //Update pin
                    forgotPin = false;
                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
                    if (user != null) {
                        user.setPin(intent.getStringExtra(ConnectConstants.PIN));
                        user.setLastPinDate(new Date());
                        ConnectDatabaseHelper.storeUser(parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_SUCCESS,
                    CONNECT_REGISTRATION_SUCCESS -> {
                //Finish workflow, user registered/recovered and logged in
                rememberPhase = true;
                completeSignin();
            }
            case CONNECT_UNLOCK_BIOMETRIC -> {
                if (success) {
                    nextRequestCode = completeUnlock();
                } else if (intent != null && intent.getBooleanExtra(ConnectConstants.PASSWORD, false)) {
                    nextRequestCode = ConnectTask.CONNECT_UNLOCK_PASSWORD;
                } else if (intent != null && intent.getBooleanExtra(ConnectConstants.RECOVER, false)) {
                    nextRequestCode = ConnectTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                }
            }
            case CONNECT_UNLOCK_PIN -> {
                nextRequestCode = ConnectTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    forgotPin = intent.getBooleanExtra(ConnectConstants.FORGOT, false);
                    if (forgotPin) {
                        //Begin the recovery workflow
                        nextRequestCode = ConnectTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                    } else {
                        nextRequestCode = completeUnlock();
                    }
                }
            }
            case CONNECT_UNLOCK_PASSWORD -> {
                if (success) {
                    boolean forgot = intent.getBooleanExtra(ConnectConstants.FORGOT, false);
                    if (forgot) {
                        //Begin the recovery workflow
                        nextRequestCode = ConnectTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                        forgotPassword = true;
                    } else {
                        forgotPassword = false;
                        FirebaseAnalyticsUtil.reportCccSignIn(AnalyticsParamValue.CCC_SIGN_IN_METHOD_PASSWORD);

                        ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
                        user.setLastPinDate(new Date());
                        ConnectDatabaseHelper.storeUser(parentActivity, user);

                        nextRequestCode = completeUnlock();
                    }
                }
            }
            case CONNECT_UNLOCK_ALT_PHONE_MESSAGE -> {
                if(success) {
                    boolean change = intent.getBooleanExtra(ConnectConstants.BUTTON2, false);

                    nextRequestCode = change ? ConnectTask.CONNECT_UNLOCK_ALT_PHONE_CHANGE : ConnectTask.CONNECT_UNLOCK_VERIFY_ALT_PHONE;
                }
            }
            case CONNECT_UNLOCK_ALT_PHONE_CHANGE -> {
                nextRequestCode = ConnectTask.CONNECT_UNLOCK_VERIFY_ALT_PHONE;
            }
            case CONNECT_UNLOCK_VERIFY_ALT_PHONE -> {
                if(success) {
                    ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
                    user.setSecondaryPhoneVerified(true);
                    ConnectDatabaseHelper.storeUser(parentActivity, user);

                    completeSignin();
                }
            }
            default -> {
                return false;
            }
        }

        phase = nextRequestCode;

        if (rememberPhase) {
            ConnectDatabaseHelper.setRegistrationPhase(parentActivity, phase);
        }

        continueWorkflow();

        if (launchSecuritySettings) {
            //Launch after continuing workflow so previous activity is still there when user returns
            SettingsHelper.launchSecuritySettings(parentActivity);
        }

        return flagStartOfRegistration;
    }

    private static ConnectTask completeUnlock() {
        ConnectUserRecord user = ConnectDatabaseHelper.getUser(parentActivity);
        if(user.shouldRequireSecondaryPhoneVerification()) {
            return ConnectTask.CONNECT_UNLOCK_ALT_PHONE_MESSAGE;
        } else {
            completeSignin();
        }

        return ConnectTask.CONNECT_NO_ACTIVITY;
    }

    private static void completeSignin() {
        if(listener != null) {
            listener.connectActivityComplete(true);
        }
    }
}

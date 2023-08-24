package org.commcare.activities.connect;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.AppManagerDeveloperPreferences;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manager class for ConnectID, handles workflow navigation and user management
 *
 * @author dviggiano
 */
public class ConnectIdManager {
    /**
     * Enum representing the current state of ConnectID
     */
    public enum ConnectIdStatus {
        NotIntroduced,
        Registering,
        LoggedOut,
        LoggedIn
    }

    /**
     * Interface for handling callbacks when a ConnectID activity finishes
     */
    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static ConnectIdManager manager = null;
    private ConnectIdStatus connectStatus = ConnectIdStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;
    private ConnectActivityCompleteListener loginListener;
    private ConnectIdTask phase = ConnectIdTask.CONNECT_NO_ACTIVITY;

    //Only used for remembering the phone number between the first and second registration screens
    private String primaryPhone = null;
    private String recoveryPhone = null;
    private String recoverySecret = null;
    private boolean forgotPassword = false;
    private boolean passwordOnlyWorkflow = false;

    //Singleton, private constructor
    private ConnectIdManager() {
    }

    private static ConnectIdManager getInstance() {
        if (manager == null) {
            manager = new ConnectIdManager();
        }

        return manager;
    }

    public static void init(CommCareActivity<?> parent) {
        ConnectIdManager manager = getInstance();
        manager.parentActivity = parent;
        ConnectIdDatabaseHelper.init(parent);

        ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
        if (user != null) {
            if (user.getRegistrationPhase() != ConnectIdTask.CONNECT_NO_ACTIVITY) {
                manager.connectStatus = ConnectIdStatus.Registering;
            } else if (manager.connectStatus == ConnectIdStatus.NotIntroduced) {
                manager.connectStatus = ConnectIdStatus.LoggedOut;
            }
        }
    }

    public static boolean isConnectIdIntroduced() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return switch (getInstance().connectStatus) {
            case NotIntroduced, Registering -> false;
            case LoggedOut, LoggedIn -> true;
        };
    }

    public static boolean requiresUnlock() {
        return isConnectIdIntroduced() && !isUnlocked();
    }

    public static boolean isUnlocked() {
        return AppManagerDeveloperPreferences.isConnectIdEnabled()
                && getInstance().connectStatus == ConnectIdStatus.LoggedIn;
    }

    public static boolean shouldShowSignInMenuOption() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return switch (getInstance().connectStatus) {
            case LoggedOut, LoggedIn -> false;
            case NotIntroduced, Registering -> true;
        };
    }

    public static boolean shouldShowSignOutMenuOption() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return switch (getInstance().connectStatus) {
            case NotIntroduced, Registering, LoggedOut -> false;
            case LoggedIn -> true;
        };
    }

    public static String getConnectButtonText(Context context) {
        return switch (getInstance().connectStatus) {
            case LoggedOut, Registering, NotIntroduced ->
                    context.getString(R.string.connect_button_logged_out);
            case LoggedIn -> context.getString(R.string.connect_button_logged_in);
        };
    }

    public static boolean shouldShowConnectButton() {
        if (!AppManagerDeveloperPreferences.isConnectIdEnabled()) {
            return false;
        }

        return switch (getInstance().connectStatus) {
            case NotIntroduced, Registering, LoggedIn -> false;
            case LoggedOut -> true;
        };
    }

    public static void signOut() {
        if (getInstance().connectStatus == ConnectIdStatus.LoggedIn) {
            getInstance().connectStatus = ConnectIdStatus.LoggedOut;
        }
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectIdDatabaseHelper.getUser(context);
    }

    public static void forgetUser() {
        ConnectIdManager manager = getInstance();

        ConnectIdDatabaseHelper.forgetUser(manager.parentActivity);

        manager.connectStatus = ConnectIdStatus.NotIntroduced;
        manager.loginListener = null;
        manager.phase = ConnectIdTask.CONNECT_NO_ACTIVITY;
        manager.primaryPhone = null;
        manager.recoveryPhone = null;
        manager.recoverySecret = null;
        manager.forgotPassword = false;
    }

    public static void handleConnectButtonPress(ConnectActivityCompleteListener listener) {
        ConnectIdManager manager = getInstance();
        manager.loginListener = listener;
        manager.forgotPassword = false;

        ConnectIdTask requestCode = ConnectIdTask.CONNECT_NO_ACTIVITY;
        switch (manager.connectStatus) {
            case NotIntroduced -> {
                requestCode = ConnectIdTask.CONNECT_REGISTER_OR_RECOVER_DECISION;
            }
            case LoggedOut, Registering -> {
                ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
                ConnectIdTask phase = user.getRegistrationPhase();
                if (phase != ConnectIdTask.CONNECT_NO_ACTIVITY) {
                    requestCode = phase;
                } else {
                    requestCode = user.shouldForcePassword() ?
                            ConnectIdTask.CONNECT_UNLOCK_PASSWORD :
                            ConnectIdTask.CONNECT_UNLOCK_BIOMETRIC;
                }
            }
            case LoggedIn -> {
                //NOTE: This is disabled now, but eventually will go to Connect menu (i.e. educate, verify, etc.)
                Toast.makeText(manager.parentActivity, "Not ready yet",
                        Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode != ConnectIdTask.CONNECT_NO_ACTIVITY) {
            manager.phase = requestCode;
            manager.continueWorkflow();
        }
    }

    private void continueWorkflow() {
        //Determine activity to launch for next phase
        Class<?> nextActivity = phase.getNextActivity();
        Map<String, Serializable> params = new HashMap<>();
        ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(parentActivity);

        switch (phase) {
            case CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                params.put(ConnectIdConstants.METHOD, ConnectIdConstants.METHOD_REGISTER_PRIMARY);
                params.put(ConnectIdConstants.PHONE, primaryPhone);
            }
            case CONNECT_REGISTRATION_MAIN -> {
                params.put(ConnectIdConstants.PHONE, primaryPhone);
            }
            case CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                params.put(ConnectIdConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRegistrationPrimary));
                params.put(ConnectIdConstants.PHONE, user.getPrimaryPhone());
                params.put(ConnectIdConstants.CHANGE, "true");
                params.put(ConnectIdConstants.USERNAME, user.getUserId());
                params.put(ConnectIdConstants.PASSWORD, user.getPassword());
            }
            case CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                params.put(ConnectIdConstants.METHOD, ConnectIdConstants.METHOD_CHANGE_PRIMARY);
            }
            case CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                params.put(ConnectIdConstants.USERNAME, user.getUserId());
                params.put(ConnectIdConstants.PASSWORD, user.getPassword());
                params.put(ConnectIdConstants.METHOD, passwordOnlyWorkflow ? "true" : "false");
            }
            case CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                params.put(ConnectIdConstants.METHOD, ConnectIdConstants.METHOD_CHANGE_ALTERNATE);
            }
            case CONNECT_REGISTRATION_SUCCESS -> {
                //Show message screen indicating success
                params.put(ConnectIdConstants.TITLE, R.string.connect_register_success_title);
                params.put(ConnectIdConstants.MESSAGE, R.string.connect_register_success_message);
                params.put(ConnectIdConstants.BUTTON, R.string.connect_register_success_button);
            }
            case CONNECT_RECOVERY_PRIMARY_PHONE -> {
                params.put(ConnectIdConstants.METHOD, ConnectIdConstants.METHOD_RECOVER_PRIMARY);
            }
            case CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                params.put(ConnectIdConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRecoveryPrimary));
                params.put(ConnectIdConstants.PHONE, recoveryPhone);
                params.put(ConnectIdConstants.CHANGE, "false");
                params.put(ConnectIdConstants.USERNAME, recoveryPhone);
                params.put(ConnectIdConstants.PASSWORD, "");
            }
            case CONNECT_RECOVERY_VERIFY_PASSWORD,
                    CONNECT_RECOVERY_CHANGE_PASSWORD -> {
                params.put(ConnectIdConstants.PHONE, recoveryPhone);
                params.put(ConnectIdConstants.SECRET, recoverySecret);
            }
            case CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                //Show message screen indicating plan to use alt phone
                params.put(ConnectIdConstants.TITLE, R.string.connect_recovery_alt_title);
                params.put(ConnectIdConstants.MESSAGE, R.string.connect_recovery_alt_message);
                params.put(ConnectIdConstants.BUTTON, R.string.connect_recovery_alt_button);
            }
            case CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                params.put(ConnectIdConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIdPhoneVerificationActivity.MethodRecoveryAlternate));
                params.put(ConnectIdConstants.PHONE, null);
                params.put(ConnectIdConstants.CHANGE, "false");
                params.put(ConnectIdConstants.USERNAME, recoveryPhone);
                params.put(ConnectIdConstants.PASSWORD, recoverySecret);
            }
            case CONNECT_RECOVERY_SUCCESS -> {
                //Show message screen indicating success
                params.put(ConnectIdConstants.TITLE, R.string.connect_recovery_success_title);
                params.put(ConnectIdConstants.MESSAGE, R.string.connect_recovery_success_message);
                params.put(ConnectIdConstants.BUTTON, R.string.connect_recovery_success_button);
            }
            case CONNECT_UNLOCK_BIOMETRIC -> {
                params.put(ConnectIdConstants.ALLOW_PASSWORD, "true");
            }
            case CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                params.put(ConnectIdConstants.ALLOW_PASSWORD, "false");
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

    public static void handleFinishedActivity(int requestCode, int resultCode, Intent intent) {
        ConnectIdManager manager = getInstance();
        boolean success = resultCode == Activity.RESULT_OK;
        ConnectIdTask nextRequestCode = ConnectIdTask.CONNECT_NO_ACTIVITY;
        boolean rememberPhase = false;

        ConnectIdTask task = ConnectIdTask.fromRequestCode(requestCode);
        switch (task) {
            case CONNECT_REGISTER_OR_RECOVER_DECISION -> {
                if (success) {
                    boolean createNew = intent.getBooleanExtra(ConnectIdConstants.CREATE, false);

                    if (createNew) {
                        nextRequestCode = ConnectIdTask.CONNECT_REGISTRATION_CONSENT;
                    } else {
                        nextRequestCode = ConnectIdTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                        manager.recoveryPhone = intent.getStringExtra(ConnectIdConstants.PHONE);
                    }
                }
            }
            case CONNECT_REGISTRATION_CONSENT -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_REGISTRATION_PRIMARY_PHONE :
                        ConnectIdTask.CONNECT_NO_ACTIVITY;
            }
            case CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_REGISTRATION_MAIN :
                        ConnectIdTask.CONNECT_REGISTRATION_CONSENT;
                if (success) {
                    manager.primaryPhone = intent.getStringExtra(ConnectIdConstants.PHONE);

                    ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPrimaryPhone(manager.primaryPhone);
                        ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_MAIN -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS :
                        ConnectIdTask.CONNECT_REGISTRATION_PRIMARY_PHONE;
                if (success) {
                    ConnectUserRecord user = ConnectUserRecord.getUserFromIntent(intent);
                    ConnectUserRecord dbUser = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
                    if (dbUser != null) {
                        dbUser.setName(user.getName());
                        dbUser.setAlternatePhone(user.getAlternatePhone());
                        user = dbUser;
                    } else {
                        manager.connectStatus = ConnectIdStatus.Registering;
                    }
                    ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                    rememberPhase = true;
                }
            }
            case CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS -> {
                //Backing up here is problematic, we just created a new account...
                nextRequestCode = ConnectIdTask.CONNECT_REGISTRATION_MAIN;
                if (success) {
                    //If no biometric configured, proceed with password only
                    boolean configured = intent.getBooleanExtra(ConnectIdConstants.CONFIGURED, false);
                    manager.passwordOnlyWorkflow = intent.getBooleanExtra(ConnectIdConstants.PASSWORD, false);
                    nextRequestCode = !manager.passwordOnlyWorkflow && configured ?
                            ConnectIdTask.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC :
                            ConnectIdTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                }
            }
            case CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE :
                        ConnectIdTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                rememberPhase = success;
            }
            case CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                nextRequestCode = manager.passwordOnlyWorkflow ? ConnectIdTask.CONNECT_REGISTRATION_MAIN :
                        ConnectIdTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                if (success) {
                    boolean changeNumber = intent != null && intent.getBooleanExtra(ConnectIdConstants.CHANGE,
                            false);
                    nextRequestCode = changeNumber ? ConnectIdTask.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE :
                            ConnectIdTask.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                    rememberPhase = !changeNumber;
                }
            }
            case CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_REGISTRATION_SUCCESS :
                        ConnectIdTask.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                if (success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setAlternatePhone(intent.getStringExtra(ConnectIdConstants.PHONE));
                        ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                //Note that we return to primary phone verification
                // (whether they did or didn't change the phone number)
                nextRequestCode = ConnectIdTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if (success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPrimaryPhone(intent.getStringExtra(ConnectIdConstants.PHONE));
                        ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_REGISTRATION_ALTERNATE_PHONE :
                        ConnectIdTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if (success) {
                    rememberPhase = true;

                    //Update password
                    manager.forgotPassword = false;
                    ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPassword(intent.getStringExtra(ConnectIdConstants.PASSWORD));
                        user.setLastPasswordDate(new Date());
                        ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_PRIMARY_PHONE -> {
                if (success) {
                    nextRequestCode = ConnectIdTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                    manager.recoveryPhone = intent.getStringExtra(ConnectIdConstants.PHONE);
                }
            }
            case CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    //If the user forgot their password, proceed directly to alt OTP
                    nextRequestCode = manager.forgotPassword ? ConnectIdTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE :
                            ConnectIdTask.CONNECT_RECOVERY_VERIFY_PASSWORD;

                    //Remember the secret key for use through the rest of the recovery process
                    manager.recoverySecret = intent.getStringExtra(ConnectIdConstants.SECRET);
                }
            }
            case CONNECT_RECOVERY_VERIFY_PASSWORD -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_RECOVERY_SUCCESS :
                        ConnectIdTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    manager.forgotPassword = intent.getBooleanExtra(ConnectIdConstants.FORGOT, false);
                    if (manager.forgotPassword) {
                        nextRequestCode = ConnectIdTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE;
                    } else {
                        String username = intent.getStringExtra(ConnectIdConstants.USERNAME);
                        String name = intent.getStringExtra(ConnectIdConstants.NAME);
                        String password = intent.getStringExtra(ConnectIdConstants.PASSWORD);

                        if (username != null && name != null && password != null) {
                            //TODO: Need to get secondary phone from server
                            ConnectUserRecord user = new ConnectUserRecord(manager.recoveryPhone, username,
                                    password, name, "");
                            user.setLastPasswordDate(new Date());
                            ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                        }
                    }
                }
            }
            case CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                if (success) {
                    nextRequestCode = ConnectIdTask.CONNECT_RECOVERY_VERIFY_ALT_PHONE;
                }
            }
            case CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_RECOVERY_CHANGE_PASSWORD :
                        ConnectIdTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;

                if (success) {
                    String username = intent.getStringExtra(ConnectIdConstants.USERNAME);
                    String name = intent.getStringExtra(ConnectIdConstants.NAME);
                    String altPhone = intent.getStringExtra(ConnectIdConstants.ALT_PHONE);

                    if (username != null && name != null) {
                        //NOTE: They'll choose a new password next
                        ConnectUserRecord user = new ConnectUserRecord(manager.recoveryPhone, username,
                                "", name, altPhone);
                        ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_CHANGE_PASSWORD -> {
                nextRequestCode = success ? ConnectIdTask.CONNECT_RECOVERY_SUCCESS :
                        ConnectIdTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    //Update password
                    manager.forgotPassword = false;
                    ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPassword(intent.getStringExtra(ConnectIdConstants.PASSWORD));
                        user.setLastPasswordDate(new Date());
                        ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_SUCCESS,
                    CONNECT_REGISTRATION_SUCCESS -> {
                //Finish workflow, user registered/recovered and logged in
                rememberPhase = true;
                manager.connectStatus = ConnectIdStatus.LoggedIn;
                manager.loginListener.connectActivityComplete(true);
            }
            case CONNECT_UNLOCK_BIOMETRIC -> {
                if (success) {
                    manager.connectStatus = ConnectIdStatus.LoggedIn;
                    manager.loginListener.connectActivityComplete(true);
                } else if (intent != null && intent.getBooleanExtra(ConnectIdConstants.PASSWORD, false)) {
                    nextRequestCode = ConnectIdTask.CONNECT_UNLOCK_PASSWORD;
                } else if (intent != null && intent.getBooleanExtra(ConnectIdConstants.RECOVER, false)) {
                    nextRequestCode = ConnectIdTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                }
            }
            case CONNECT_UNLOCK_PASSWORD -> {
                if (success) {
                    boolean forgot = intent.getBooleanExtra(ConnectIdConstants.FORGOT, false);
                    if (forgot) {
                        //Begin the recovery workflow
                        nextRequestCode = ConnectIdTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                        manager.forgotPassword = true;
                    } else {
                        manager.forgotPassword = false;
                        manager.connectStatus = ConnectIdStatus.LoggedIn;
                        manager.loginListener.connectActivityComplete(true);
                        FirebaseAnalyticsUtil.reportCccSignIn(AnalyticsParamValue.CCC_SIGN_IN_METHOD_PASSWORD);

                        ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
                        user.setLastPasswordDate(new Date());
                        ConnectIdDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            default -> {
                return;
            }
        }

        manager.phase = nextRequestCode;

        if (rememberPhase) {
            ConnectIdDatabaseHelper.setRegistrationPhase(manager.parentActivity, manager.phase);
        }

        manager.continueWorkflow();
    }

    public static void rememberAppCredentials(String appId, String userId, String passwordOrPin) {
        ConnectIdManager manager = getInstance();
        if (isUnlocked()) {
            ConnectIdDatabaseHelper.storeApp(manager.parentActivity, appId, userId, passwordOrPin);
        }
    }

    public static void forgetAppCredentials(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectIdDatabaseHelper.getAppData(manager.parentActivity, appId, userId);
        if (record != null) {
            ConnectIdDatabaseHelper.deleteAppData(manager.parentActivity, record);
        }
    }

    public static AuthInfo.ProvidedAuth getCredentialsForApp(String appId, String userId) {
        if (isUnlocked()) {
            ConnectLinkedAppRecord record = ConnectIdDatabaseHelper.getAppData(manager.parentActivity, appId,
                    userId);
            if (record != null && record.getPassword().length() > 0) {
                return new AuthInfo.ProvidedAuth(record.getUserId(), record.getPassword(), false);
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getConnectToken() {
        if (isUnlocked()) {
            ConnectUserRecord user = ConnectIdDatabaseHelper.getUser(manager.parentActivity);
            if (user != null && (new Date()).compareTo(user.getConnectTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(user.getConnectToken());
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isUnlocked()) {
            ConnectLinkedAppRecord record = ConnectIdDatabaseHelper.getAppData(manager.parentActivity, appId,
                    userId);
            if (record != null && (new Date()).compareTo(record.getHqTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(record.getHqToken());
            }
        }

        return null;
    }
}

package org.commcare.activities.connect;

import static android.app.Activity.RESULT_OK;

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
 * @author dviggiano
 * Manager class for ConnectID, handles workflow navigation and user management
 */
public class ConnectIDManager {
    public enum ConnectIdStatus {
        NotIntroduced,
        Registering,
        LoggedOut,
        LoggedIn
    }

    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static ConnectIDManager manager = null;
    private ConnectIdStatus connectStatus = ConnectIdStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;
    private ConnectActivityCompleteListener loginListener;
    private ConnectIDTask phase = ConnectIDTask.CONNECT_NO_ACTIVITY;

    //Only used for remembering the phone number between the first and second registration screens
    private String primaryPhone = null;
    private String recoveryPhone = null;
    private String recoverySecret = null;
    private boolean forgotPassword = false;
    private boolean passwordOnlyWorkflow = false;

    //Singleton, private constructor
    private ConnectIDManager() {
    }

    private static ConnectIDManager getInstance() {
        if (manager == null) {
            manager = new ConnectIDManager();
        }

        return manager;
    }

    public static void init(CommCareActivity<?> parent) {
        ConnectIDManager manager = getInstance();
        manager.parentActivity = parent;
        ConnectIDDatabaseHelper.init(parent);

        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
        if (user != null) {
            if (user.getRegistrationPhase() != ConnectIDTask.CONNECT_NO_ACTIVITY) {
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
        return ConnectIDDatabaseHelper.getUser(context);
    }

    public static void forgetUser() {
        ConnectIDManager manager = getInstance();

        ConnectIDDatabaseHelper.forgetUser(manager.parentActivity);

        manager.connectStatus = ConnectIdStatus.NotIntroduced;
        manager.loginListener = null;
        manager.phase = ConnectIDTask.CONNECT_NO_ACTIVITY;
        manager.primaryPhone = null;
        manager.recoveryPhone = null;
        manager.recoverySecret = null;
        manager.forgotPassword = false;
    }

    public static void handleConnectButtonPress(ConnectActivityCompleteListener listener) {
        ConnectIDManager manager = getInstance();
        manager.loginListener = listener;
        manager.forgotPassword = false;

        ConnectIDTask requestCode = ConnectIDTask.CONNECT_NO_ACTIVITY;
        switch (manager.connectStatus) {
            case NotIntroduced -> {
                requestCode = ConnectIDTask.CONNECT_REGISTER_OR_RECOVER_DECISION;
            }
            case LoggedOut, Registering -> {
                ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                ConnectIDTask phase = user.getRegistrationPhase();
                if (phase != ConnectIDTask.CONNECT_NO_ACTIVITY) {
                    requestCode = phase;
                } else {
                    requestCode = user.shouldForcePassword() ?
                            ConnectIDTask.CONNECT_UNLOCK_PASSWORD :
                            ConnectIDTask.CONNECT_UNLOCK_BIOMETRIC;
                }
            }
            case LoggedIn -> {
                //NOTE: This is disabled now, but eventually will go to Connect menu (i.e. educate, verify, etc.)
                Toast.makeText(manager.parentActivity, "Not ready yet",
                        Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode != ConnectIDTask.CONNECT_NO_ACTIVITY) {
            manager.phase = requestCode;
            manager.continueWorkflow();
        }
    }

    private void continueWorkflow() {
        //Determine activity to launch for next phase
        Class<?> nextActivity = phase.getNextActivity();
        Map<String, Serializable> params = new HashMap<>();
        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(parentActivity);

        switch (phase) {
            case CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                params.put(ConnectIDConstants.METHOD, ConnectIDConstants.METHOD_REGISTER_PRIMARY);
                params.put(ConnectIDConstants.PHONE, primaryPhone);
            }
            case CONNECT_REGISTRATION_MAIN -> {
                params.put(ConnectIDConstants.PHONE, primaryPhone);
            }
            case CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                params.put(ConnectIDConstants.METHOD, String.format(Locale.getDefault(), "%d", ConnectIDPhoneVerificationActivity.MethodRegistrationPrimary));
                params.put(ConnectIDConstants.PHONE, user.getPrimaryPhone());
                params.put(ConnectIDConstants.CHANGE, "true");
                params.put(ConnectIDConstants.USERNAME, user.getUserId());
                params.put(ConnectIDConstants.PASSWORD, user.getPassword());
            }
            case CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                params.put(ConnectIDConstants.METHOD, ConnectIDConstants.METHOD_CHANGE_PRIMARY);
            }
            case CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                params.put(ConnectIDConstants.USERNAME, user.getUserId());
                params.put(ConnectIDConstants.PASSWORD, user.getPassword());
                params.put(ConnectIDConstants.METHOD, passwordOnlyWorkflow ? "true" : "false");
            }
            case CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                params.put(ConnectIDConstants.METHOD, ConnectIDConstants.METHOD_CHANGE_ALTERNATE);
            }
            case CONNECT_REGISTRATION_SUCCESS -> {
                //Show message screen indicating success
                params.put(ConnectIDConstants.TITLE, R.string.connect_register_success_title);
                params.put(ConnectIDConstants.MESSAGE, R.string.connect_register_success_message);
                params.put(ConnectIDConstants.BUTTON, R.string.connect_register_success_button);
            }
            case CONNECT_RECOVERY_PRIMARY_PHONE -> {
                params.put(ConnectIDConstants.METHOD, ConnectIDConstants.METHOD_RECOVER_PRIMARY);
            }
            case CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                params.put(ConnectIDConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIDPhoneVerificationActivity.MethodRecoveryPrimary));
                params.put(ConnectIDConstants.PHONE, recoveryPhone);
                params.put(ConnectIDConstants.CHANGE, "false");
                params.put(ConnectIDConstants.USERNAME, recoveryPhone);
                params.put(ConnectIDConstants.PASSWORD, "");
            }
            case CONNECT_RECOVERY_VERIFY_PASSWORD,
                    CONNECT_RECOVERY_CHANGE_PASSWORD -> {
                params.put(ConnectIDConstants.PHONE, recoveryPhone);
                params.put(ConnectIDConstants.SECRET, recoverySecret);
            }
            case CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                //Show message screen indicating plan to use alt phone
                params.put(ConnectIDConstants.TITLE, R.string.connect_recovery_alt_title);
                params.put(ConnectIDConstants.MESSAGE, R.string.connect_recovery_alt_message);
                params.put(ConnectIDConstants.BUTTON, R.string.connect_recovery_alt_button);
            }
            case CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                params.put(ConnectIDConstants.METHOD, String.format(Locale.getDefault(), "%d",
                        ConnectIDPhoneVerificationActivity.MethodRecoveryAlternate));
                params.put(ConnectIDConstants.PHONE, null);
                params.put(ConnectIDConstants.CHANGE, "false");
                params.put(ConnectIDConstants.USERNAME, recoveryPhone);
                params.put(ConnectIDConstants.PASSWORD, recoverySecret);
            }
            case CONNECT_RECOVERY_SUCCESS -> {
                //Show message screen indicating success
                params.put(ConnectIDConstants.TITLE, R.string.connect_recovery_success_title);
                params.put(ConnectIDConstants.MESSAGE, R.string.connect_recovery_success_message);
                params.put(ConnectIDConstants.BUTTON, R.string.connect_recovery_success_button);
            }
            case CONNECT_UNLOCK_BIOMETRIC -> {
                params.put(ConnectIDConstants.ALLOW_PASSWORD, "true");
            }
            case CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                params.put(ConnectIDConstants.ALLOW_PASSWORD, "false");
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
        ConnectIDManager manager = getInstance();
        boolean success = resultCode == RESULT_OK;
        ConnectIDTask nextRequestCode = ConnectIDTask.CONNECT_NO_ACTIVITY;
        boolean rememberPhase = false;

        ConnectIDTask task = ConnectIDTask.fromRequestCode(requestCode);
        switch (task) {
            case CONNECT_REGISTER_OR_RECOVER_DECISION -> {
                if (success) {
                    boolean createNew = intent.getBooleanExtra(ConnectIDConstants.CREATE, false);

                    if (createNew) {
                        nextRequestCode = ConnectIDTask.CONNECT_REGISTRATION_CONSENT;
                    } else {
                        nextRequestCode = ConnectIDTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                        manager.recoveryPhone = intent.getStringExtra(ConnectIDConstants.PHONE);
                    }
                }
            }
            case CONNECT_REGISTRATION_CONSENT -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_REGISTRATION_PRIMARY_PHONE :
                        ConnectIDTask.CONNECT_NO_ACTIVITY;
            }
            case CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_REGISTRATION_MAIN :
                        ConnectIDTask.CONNECT_REGISTRATION_CONSENT;
                if (success) {
                    manager.primaryPhone = intent.getStringExtra(ConnectIDConstants.PHONE);

                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPrimaryPhone(manager.primaryPhone);
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_MAIN -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS :
                        ConnectIDTask.CONNECT_REGISTRATION_PRIMARY_PHONE;
                if (success) {
                    ConnectUserRecord user = ConnectUserRecord.getUserFromIntent(intent);
                    ConnectUserRecord dbUser = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if (dbUser != null) {
                        dbUser.setName(user.getName());
                        dbUser.setAlternatePhone(user.getAlternatePhone());
                        user = dbUser;
                    } else {
                        manager.connectStatus = ConnectIdStatus.Registering;
                    }
                    ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    rememberPhase = true;
                }
            }
            case CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS -> {
                //Backing up here is problematic, we just created a new account...
                nextRequestCode = ConnectIDTask.CONNECT_REGISTRATION_MAIN;
                if (success) {
                    //If no biometric configured, proceed with password only
                    boolean configured = intent.getBooleanExtra(ConnectIDConstants.CONFIGURED, false);
                    manager.passwordOnlyWorkflow = intent.getBooleanExtra(ConnectIDConstants.PASSWORD, false);
                    nextRequestCode = !manager.passwordOnlyWorkflow && configured ?
                            ConnectIDTask.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC :
                            ConnectIDTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                }
            }
            case CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE :
                        ConnectIDTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                rememberPhase = success;
            }
            case CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                nextRequestCode = manager.passwordOnlyWorkflow ? ConnectIDTask.CONNECT_REGISTRATION_MAIN :
                        ConnectIDTask.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                if (success) {
                    boolean changeNumber = intent != null && intent.getBooleanExtra(ConnectIDConstants.CHANGE, false);
                    nextRequestCode = changeNumber ? ConnectIDTask.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE :
                            ConnectIDTask.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                    rememberPhase = !changeNumber;
                }
            }
            case CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_REGISTRATION_SUCCESS :
                        ConnectIDTask.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                if (success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setAlternatePhone(intent.getStringExtra(ConnectIDConstants.PHONE));
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                //Note that we return to primary phone verification
                // (whether they did or didn't change the phone number)
                nextRequestCode = ConnectIDTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if (success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPrimaryPhone(intent.getStringExtra(ConnectIDConstants.PHONE));
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_REGISTRATION_ALTERNATE_PHONE :
                        ConnectIDTask.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if (success) {
                    rememberPhase = true;

                    //Update password
                    manager.forgotPassword = false;
                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPassword(intent.getStringExtra(ConnectIDConstants.PASSWORD));
                        user.setLastPasswordDate(new Date());
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_PRIMARY_PHONE -> {
                if (success) {
                    nextRequestCode = ConnectIDTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                    manager.recoveryPhone = intent.getStringExtra(ConnectIDConstants.PHONE);
                }
            }
            case CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    //If the user forgot their password, proceed directly to alt OTP
                    nextRequestCode = manager.forgotPassword ? ConnectIDTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE :
                            ConnectIDTask.CONNECT_RECOVERY_VERIFY_PASSWORD;

                    //Remember the secret key for use through the rest of the recovery process
                    manager.recoverySecret = intent.getStringExtra(ConnectIDConstants.SECRET);
                }
            }
            case CONNECT_RECOVERY_VERIFY_PASSWORD -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_RECOVERY_SUCCESS :
                        ConnectIDTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    manager.forgotPassword = intent.getBooleanExtra(ConnectIDConstants.FORGOT, false);
                    if (manager.forgotPassword) {
                        nextRequestCode = ConnectIDTask.CONNECT_RECOVERY_ALT_PHONE_MESSAGE;
                    } else {
                        String username = intent.getStringExtra(ConnectIDConstants.USERNAME);
                        String name = intent.getStringExtra(ConnectIDConstants.NAME);
                        String password = intent.getStringExtra(ConnectIDConstants.PASSWORD);

                        if (username != null && name != null && password != null) {
                            //TODO: Need to get secondary phone from server
                            ConnectUserRecord user = new ConnectUserRecord(manager.recoveryPhone, username,
                                    password, name, "");
                            user.setLastPasswordDate(new Date());
                            ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                        }
                    }
                }
            }
            case CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                if (success) {
                    nextRequestCode = ConnectIDTask.CONNECT_RECOVERY_VERIFY_ALT_PHONE;
                }
            }
            case CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_RECOVERY_CHANGE_PASSWORD :
                        ConnectIDTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;

                if (success) {
                    String username = intent.getStringExtra(ConnectIDConstants.USERNAME);
                    String name = intent.getStringExtra(ConnectIDConstants.NAME);
                    String altPhone = intent.getStringExtra(ConnectIDConstants.ALT_PHONE);

                    if (username != null && name != null) {
                        //NOTE: They'll choose a new password next
                        ConnectUserRecord user = new ConnectUserRecord(manager.recoveryPhone, username,
                                "", name, altPhone);
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case CONNECT_RECOVERY_CHANGE_PASSWORD -> {
                nextRequestCode = success ? ConnectIDTask.CONNECT_RECOVERY_SUCCESS :
                        ConnectIDTask.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    //Update password
                    manager.forgotPassword = false;
                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if (user != null) {
                        user.setPassword(intent.getStringExtra(ConnectIDConstants.PASSWORD));
                        user.setLastPasswordDate(new Date());
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
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
                } else if (intent != null && intent.getBooleanExtra(ConnectIDConstants.PASSWORD, false)) {
                    nextRequestCode = ConnectIDTask.CONNECT_UNLOCK_PASSWORD;
                } else if (intent != null && intent.getBooleanExtra(ConnectIDConstants.RECOVER, false)) {
                    nextRequestCode = ConnectIDTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                }
            }
            case CONNECT_UNLOCK_PASSWORD -> {
                if (success) {
                    boolean forgot = intent.getBooleanExtra(ConnectIDConstants.FORGOT, false);
                    if (forgot) {
                        //Begin the recovery workflow
                        nextRequestCode = ConnectIDTask.CONNECT_RECOVERY_PRIMARY_PHONE;
                        manager.forgotPassword = true;
                    } else {
                        manager.forgotPassword = false;
                        manager.connectStatus = ConnectIdStatus.LoggedIn;
                        manager.loginListener.connectActivityComplete(true);
                        FirebaseAnalyticsUtil.reportCccSignIn(AnalyticsParamValue.CCC_SIGN_IN_METHOD_PASSWORD);

                        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                        user.setLastPasswordDate(new Date());
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            default -> {
                return;
            }
        }

        manager.phase = nextRequestCode;

        if (rememberPhase) {
            ConnectIDDatabaseHelper.setRegistrationPhase(manager.parentActivity, manager.phase);
        }

        manager.continueWorkflow();
    }

    public static void rememberAppCredentials(String appId, String userId, String passwordOrPin) {
        ConnectIDManager manager = getInstance();
        if (isUnlocked()) {
            ConnectIDDatabaseHelper.storeApp(manager.parentActivity, appId, userId, passwordOrPin);
        }
    }

    public static void forgetAppCredentials(String appId, String userId) {
        ConnectLinkedAppRecord record = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, appId, userId);
        if (record != null) {
            ConnectIDDatabaseHelper.deleteAppData(manager.parentActivity, record);
        }
    }

    public static AuthInfo.ProvidedAuth getCredentialsForApp(String appId, String userId) {
        if (isUnlocked()) {
            ConnectLinkedAppRecord record = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, appId, userId);
            if (record != null && record.getPassword().length() > 0) {
                return new AuthInfo.ProvidedAuth(record.getUserId(), record.getPassword(), false);
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getConnectToken() {
        if (isUnlocked()) {
            ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
            if (user != null && (new Date()).compareTo(user.getConnectTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(user.getConnectToken());
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getTokenCredentialsForApp(String appId, String userId) {
        if (isUnlocked()) {
            ConnectLinkedAppRecord record = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, appId, userId);
            if (record != null && (new Date()).compareTo(record.getHqTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(record.getHqToken());
            }
        }

        return null;
    }
}

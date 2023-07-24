package org.commcare.activities.connect;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.AppManagerDeveloperPreferences;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ConnectIDManager {
    public enum ConnectIDStatus {
        NotIntroduced,
        LoggedOut,
        LoggedIn
    }

    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static ConnectIDManager manager = null;
    private ConnectIDStatus connectStatus = ConnectIDStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;
    private ConnectActivityCompleteListener loginListener;
    private int phase = ConnectIDConstants.CONNECT_NO_ACTIVITY;

    //Only used for remembering the phone number between the first and second registration screens
    private String primaryPhone = null;
    private String recoveryPhone = null;
    private String recoverySecret = null;
    private boolean forgotPassword = false;

    //Singleton, private constructor
    private ConnectIDManager() {}

    private static ConnectIDManager getInstance() {
        if(manager == null) {
            manager = new ConnectIDManager();
        }

        return manager;
    }

    public static void init(CommCareActivity<?> parent) {
        ConnectIDManager manager = getInstance();
        manager.parentActivity = parent;
        ConnectIDDatabaseHelper.init(parent);

        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
        if(user != null && manager.connectStatus == ConnectIDStatus.NotIntroduced) {
            manager.connectStatus = ConnectIDStatus.LoggedOut;
        }
    }

    public static boolean isConnectIDIntroduced(Context context) {
        boolean introduced = AppManagerDeveloperPreferences.isConnectIDEnabled() && getInstance().connectStatus != ConnectIDStatus.NotIntroduced;

        if(introduced) {
            ConnectUserRecord user = getUser(context);
            introduced = user != null && user.getRegistrationPhase() == ConnectIDConstants.CONNECT_NO_ACTIVITY;
        }

        return introduced;
    }

    public static boolean isSignedIn() {
        return AppManagerDeveloperPreferences.isConnectIDEnabled() && getInstance().connectStatus == ConnectIDStatus.LoggedIn;
    }

    public static boolean shouldShowSignInMenuOption() {
        return AppManagerDeveloperPreferences.isConnectIDEnabled()
                && (getInstance().connectStatus == ConnectIDStatus.NotIntroduced
                || getInstance().connectStatus == ConnectIDStatus.LoggedOut);
    }

    public static boolean shouldShowSignOutMenuOption() {
        return AppManagerDeveloperPreferences.isConnectIDEnabled() && getInstance().connectStatus == ConnectIDStatus.LoggedIn;
    }

    public static String getConnectButtonText(Context context) {
        return switch (getInstance().connectStatus) {
            case LoggedOut, NotIntroduced -> context.getString(R.string.connect_button_logged_out);
            case LoggedIn -> context.getString(R.string.connect_button_logged_in);
        };
    }

    public static boolean shouldShowConnectButton() {
        return getInstance().connectStatus != ConnectIDStatus.LoggedIn && getInstance().connectStatus != ConnectIDStatus.NotIntroduced;
    }

    public static void signOut() {
        if(getInstance().connectStatus == ConnectIDStatus.LoggedIn) {
            getInstance().connectStatus = ConnectIDStatus.LoggedOut;
        };
    }

    public static ConnectUserRecord getUser(Context context) {
        return ConnectIDDatabaseHelper.getUser(context);
    }

    public static void forgetUser() {
        ConnectIDManager manager = getInstance();

        ConnectIDDatabaseHelper.forgetUser(manager.parentActivity);

        manager.connectStatus = ConnectIDStatus.NotIntroduced;
        manager.loginListener = null;
        manager.phase = ConnectIDConstants.CONNECT_NO_ACTIVITY;
        manager.primaryPhone = null;
        manager.recoveryPhone = null;
        manager.recoverySecret = null;
        manager.forgotPassword = false;
    }

    public static void handleConnectButtonPress(ConnectActivityCompleteListener listener) {
        ConnectIDManager manager = getInstance();
        manager.loginListener = listener;
        manager.forgotPassword = false;

        int requestCode = ConnectIDConstants.CONNECT_NO_ACTIVITY;
        switch (manager.connectStatus) {
            case NotIntroduced -> {
                requestCode = ConnectIDConstants.CONNECT_REGISTER_OR_RECOVER_DECISION;
            }
            case LoggedOut -> {
                ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                int phase = user.getRegistrationPhase();
                if(phase != ConnectIDConstants.CONNECT_NO_ACTIVITY) {
                    requestCode = phase;
                }
                else {
                    Date passwordDate = user.getLastPasswordDate();
                    boolean forcePassword = passwordDate == null;
                    if (!forcePassword) {
                        //See how much time has passed since last password login
                        long millis = (new Date()).getTime() - passwordDate.getTime();
                        long days = TimeUnit.DAYS.convert(millis, TimeUnit.MILLISECONDS);
                        forcePassword = days >= 7;
                    }

                    requestCode = forcePassword ? ConnectIDConstants.CONNECT_UNLOCK_PASSWORD : ConnectIDConstants.CONNECT_UNLOCK_BIOMETRIC;
                }
            }
            case LoggedIn -> {
                //NOTE: This is disabled now, but eventually will go to Connect menu (i.e. educate, verify, etc.)
                Toast.makeText(manager.parentActivity, "Not ready yet",
                        Toast.LENGTH_SHORT).show();
            }
        }

        if(requestCode != ConnectIDConstants.CONNECT_NO_ACTIVITY) {
            manager.phase = requestCode;
            manager.continueWorkflow();
        }
    }

    private void continueWorkflow() {
        //Determine activity to launch for next phase
        Class<?> nextActivity = null;
        Map<String, String> params = new HashMap<>();
        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(parentActivity);

        switch (phase) {
            case ConnectIDConstants.CONNECT_REGISTER_OR_RECOVER_DECISION -> {
                nextActivity = ConnectIDRecoveryDecisionActivity.class;
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CONSENT -> {
                nextActivity = ConnectIDConsentActivity.class;
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                nextActivity = ConnectIDPhoneActivity.class;

                params.put(ConnectIDConstants.METHOD, "true");
                params.put(ConnectIDConstants.PHONE, null);
                params.put(ConnectIDConstants.USERNAME, null);
                params.put(ConnectIDConstants.PASSWORD, null);
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_MAIN -> {
                nextActivity = ConnectIDRegistrationActivity.class;

                params.put(ConnectIDConstants.PHONE, primaryPhone);
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS -> {
                nextActivity = ConnectIDVerificationActivity.class;
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                nextActivity = ConnectIDPhoneVerificationActivity.class;

                params.put(ConnectIDConstants.METHOD, String.format(Locale.getDefault(), "%d", ConnectIDPhoneVerificationActivity.MethodRegistrationPrimary));
                params.put(ConnectIDConstants.PHONE, user.getPrimaryPhone());
                params.put(ConnectIDConstants.CHANGE, "true");
                params.put(ConnectIDConstants.USERNAME, user.getUserID());
                params.put(ConnectIDConstants.PASSWORD, user.getPassword());
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                nextActivity = ConnectIDPhoneActivity.class;

                params.put(ConnectIDConstants.METHOD, "false");
                params.put(ConnectIDConstants.PHONE, user.getPrimaryPhone());
                params.put(ConnectIDConstants.ALT_PHONE, user.getAlternatePhone());
                params.put(ConnectIDConstants.USERNAME, user.getUserID());
                params.put(ConnectIDConstants.PASSWORD, user.getPassword());
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                nextActivity = ConnectIDPasswordActivity.class;

                params.put(ConnectIDConstants.USERNAME, user.getUserID());
                params.put(ConnectIDConstants.PASSWORD, user.getPassword());
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_SUCCESS -> {
                //Show message screen indicating success
                nextActivity = ConnectIDMessageActivity.class;

                params.put(ConnectIDConstants.TITLE, parentActivity.getString(R.string.connect_register_success_title));
                params.put(ConnectIDConstants.MESSAGE, parentActivity.getString(R.string.connect_register_success_message));
                params.put(ConnectIDConstants.BUTTON, parentActivity.getString(R.string.connect_register_success_button));
            }
            case ConnectIDConstants.CONNECT_RECOVERY_PRIMARY_PHONE -> {
                nextActivity = ConnectIDPhoneActivity.class;

                params.put(ConnectIDConstants.METHOD, "false");
                params.put(ConnectIDConstants.PHONE, null);
                params.put(ConnectIDConstants.USERNAME, null);
                params.put(ConnectIDConstants.PASSWORD, null);
            }
            case ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                nextActivity = ConnectIDPhoneVerificationActivity.class;

                params.put(ConnectIDConstants.METHOD, String.format(Locale.getDefault(), "%d", ConnectIDPhoneVerificationActivity.MethodRecoveryPrimary));
                params.put(ConnectIDConstants.PHONE, recoveryPhone);
                params.put(ConnectIDConstants.CHANGE, "false");
                params.put(ConnectIDConstants.USERNAME, recoveryPhone);
                params.put(ConnectIDConstants.PASSWORD, "");
            }
            case ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PASSWORD -> {
                nextActivity = ConnectIDPasswordVerificationActivity.class;

                params.put(ConnectIDConstants.PHONE, recoveryPhone);
                params.put(ConnectIDConstants.SECRET, recoverySecret);
            }
            case ConnectIDConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                //Show message screen indicating plan to use alt phone
                nextActivity = ConnectIDMessageActivity.class;

                params.put(ConnectIDConstants.TITLE, parentActivity.getString(R.string.connect_recovery_alt_title));
                params.put(ConnectIDConstants.MESSAGE, parentActivity.getString(R.string.connect_recovery_alt_message));
                params.put(ConnectIDConstants.BUTTON, parentActivity.getString(R.string.connect_recovery_alt_button));
            }
            case ConnectIDConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE-> {
                nextActivity = ConnectIDPhoneVerificationActivity.class;

                params.put(ConnectIDConstants.METHOD, String.format(Locale.getDefault(), "%d", ConnectIDPhoneVerificationActivity.MethodRecoveryAlternate));
                params.put(ConnectIDConstants.PHONE, null);
                params.put(ConnectIDConstants.CHANGE, "false");
                params.put(ConnectIDConstants.USERNAME, recoveryPhone);
                params.put(ConnectIDConstants.PASSWORD, recoverySecret);
            }
            case ConnectIDConstants.CONNECT_RECOVERY_CHANGE_PASSWORD -> {
                nextActivity = ConnectIDPasswordActivity.class;

                params.put(ConnectIDConstants.PHONE, recoveryPhone);
                params.put(ConnectIDConstants.SECRET, recoverySecret);
            }
            case ConnectIDConstants.CONNECT_RECOVERY_SUCCESS -> {
                //Show message screen indicating success
                nextActivity = ConnectIDMessageActivity.class;

                params.put(ConnectIDConstants.TITLE, parentActivity.getString(R.string.connect_recovery_success_title));
                params.put(ConnectIDConstants.MESSAGE, parentActivity.getString(R.string.connect_recovery_success_message));
                params.put(ConnectIDConstants.BUTTON, parentActivity.getString(R.string.connect_recovery_success_button));
            }
            case ConnectIDConstants.CONNECT_UNLOCK_PASSWORD-> {
                nextActivity = ConnectIDPasswordVerificationActivity.class;
            }
            case ConnectIDConstants.CONNECT_UNLOCK_BIOMETRIC -> {
                nextActivity = ConnectIDLoginActivity.class;

                params.put(ConnectIDConstants.ALLOW_PASSWORD, "true");
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                nextActivity = ConnectIDLoginActivity.class;

                params.put(ConnectIDConstants.ALLOW_PASSWORD, "false");
            }
        }

        if(nextActivity != null) {
            Intent i = new Intent(parentActivity, nextActivity);

            for (Map.Entry<String, String> pair : params.entrySet()) {
                i.putExtra(pair.getKey(), pair.getValue());
            }

            parentActivity.startActivityForResult(i, phase);
        }
    }

    public static void handleFinishedActivity(int requestCode, int resultCode, Intent intent) {
        ConnectIDManager manager = getInstance();
        boolean success = resultCode == RESULT_OK;
        int nextRequestCode = ConnectIDConstants.CONNECT_NO_ACTIVITY;
        boolean rememberPhase = false;

        switch (requestCode) {
            case ConnectIDConstants.CONNECT_REGISTER_OR_RECOVER_DECISION -> {
                if(success) {
                    boolean createNew = intent.getBooleanExtra(ConnectIDConstants.CREATE, false);

                    if(createNew) {
                        nextRequestCode = ConnectIDConstants.CONNECT_REGISTRATION_CONSENT;
                    }
                    else {
                        nextRequestCode = ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                        manager.recoveryPhone = intent.getStringExtra(ConnectIDConstants.PHONE);
                    }
                }
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CONSENT -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_REGISTRATION_PRIMARY_PHONE : ConnectIDConstants.CONNECT_NO_ACTIVITY;
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_PRIMARY_PHONE -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_REGISTRATION_MAIN : ConnectIDConstants.CONNECT_REGISTRATION_CONSENT;
                if(success) {
                    manager.primaryPhone = intent.getStringExtra(ConnectIDConstants.PHONE);

                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if(user != null) {
                        user.setPrimaryPhone(manager.primaryPhone);
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_MAIN -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS : ConnectIDConstants.CONNECT_REGISTRATION_PRIMARY_PHONE;
                if(success) {
                    ConnectUserRecord user = ConnectUserRecord.getUserFromIntent(intent);
                    ConnectUserRecord dbUser = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if(dbUser != null) {
                        dbUser.setName(user.getName());
                        dbUser.setAlternatePhone(user.getAlternatePhone());
                        user = dbUser;
                    }
                    ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    rememberPhase = true;
                }
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS -> {
                //Backing up here is problematic, we just created a new account...
                nextRequestCode = ConnectIDConstants.CONNECT_REGISTRATION_MAIN;
                if(success) {
                    //If no biometric configured, proceed with password only
                    boolean configured = intent.getBooleanExtra(ConnectIDConstants.CONFIGURED, false);
                    nextRequestCode = configured ? ConnectIDConstants.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC : ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                }
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE : ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                rememberPhase = success;
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                nextRequestCode = ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                if(success) {
                    boolean changeNumber = intent != null && intent.getBooleanExtra(ConnectIDConstants.CHANGE, false);
                    nextRequestCode = changeNumber ? ConnectIDConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE : ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                    rememberPhase = !changeNumber;
                }
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE -> {
                //Note that we return to primary phone verification (whether they did or didn't change the phone number)
                nextRequestCode = ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if(success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if(user != null) {
                        user.setPrimaryPhone(intent.getStringExtra(ConnectIDConstants.PHONE));
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_REGISTRATION_SUCCESS : ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                if (success) {
                    rememberPhase = true;

                    //Update password
                    manager.forgotPassword = false;
                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if(user != null) {
                        user.setPassword(intent.getStringExtra(ConnectIDConstants.PASSWORD));
                        user.setLastPasswordDate(new Date());
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case ConnectIDConstants.CONNECT_RECOVERY_PRIMARY_PHONE -> {
                if(success) {
                    nextRequestCode = ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                    manager.recoveryPhone = intent.getStringExtra(ConnectIDConstants.PHONE);
                }
            }
            case ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE -> {
                if (success) {
                    //If the user forgot their password, proceed directly to alt OTP
                    nextRequestCode = manager.forgotPassword ? ConnectIDConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE : ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PASSWORD;

                    //Remember the secret key for use through the rest of the recovery process
                    manager.recoverySecret = intent.getStringExtra(ConnectIDConstants.SECRET);
                }
//                else {
//                    boolean changeNumber = intent != null && intent.getBooleanExtra(ConnectIDConstants.CHANGE, false);
//                    if(changeNumber) {
//                        //Means server failed trying to send SMS
//                        //TODO: What to do when number exists but SMS request fails?
//                    }
//                }
            }
            case ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PASSWORD -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_RECOVERY_SUCCESS : ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    manager.forgotPassword = intent.getBooleanExtra(ConnectIDConstants.FORGOT, false);
                    if(manager.forgotPassword) {
                        nextRequestCode = ConnectIDConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE;
                    }
                    else {
                        String username = intent.getStringExtra(ConnectIDConstants.USERNAME);
                        String name = intent.getStringExtra(ConnectIDConstants.NAME);
                        String password = intent.getStringExtra(ConnectIDConstants.PASSWORD);

                        if (username != null && name != null && password != null) {
                            //TODO: Need to get secondary phone from server
                            ConnectUserRecord user = new ConnectUserRecord(manager.recoveryPhone, username, password, name, "");
                            user.setLastPasswordDate(new Date());
                            ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                        }
                    }
                }
            }
            case ConnectIDConstants.CONNECT_RECOVERY_ALT_PHONE_MESSAGE -> {
                if(success) {
                    nextRequestCode = ConnectIDConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE;
                }
            }
            case ConnectIDConstants.CONNECT_RECOVERY_VERIFY_ALT_PHONE -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_RECOVERY_CHANGE_PASSWORD : ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;

                if(success) {
                    String username = intent.getStringExtra(ConnectIDConstants.USERNAME);
                    String name = intent.getStringExtra(ConnectIDConstants.NAME);
                    String altPhone = intent.getStringExtra(ConnectIDConstants.ALT_PHONE);

                    if(username != null && name != null) {
                        //NOTE: They'll choose a new password next
                        ConnectUserRecord user = new ConnectUserRecord(manager.recoveryPhone, username, "", name, altPhone);
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case ConnectIDConstants.CONNECT_RECOVERY_CHANGE_PASSWORD -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_RECOVERY_SUCCESS : ConnectIDConstants.CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE;
                if (success) {
                    //Update password
                    manager.forgotPassword = false;
                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if(user != null) {
                        user.setPassword(intent.getStringExtra(ConnectIDConstants.PASSWORD));
                        user.setLastPasswordDate(new Date());
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
                }
            }
            case ConnectIDConstants.CONNECT_RECOVERY_SUCCESS,
                 ConnectIDConstants.CONNECT_REGISTRATION_SUCCESS -> {
                //Finish workflow, user registered/recovered and logged in
                rememberPhase = true;
                manager.connectStatus = ConnectIDStatus.LoggedIn;
                manager.loginListener.connectActivityComplete(true);
            }
            case ConnectIDConstants.CONNECT_UNLOCK_BIOMETRIC -> {
                if (success) {
                    manager.connectStatus = ConnectIDStatus.LoggedIn;
                    manager.loginListener.connectActivityComplete(true);
                } else if (intent != null && intent.getBooleanExtra(ConnectIDConstants.PASSWORD, false)) {
                    nextRequestCode = ConnectIDConstants.CONNECT_UNLOCK_PASSWORD;
                } else if (intent != null && intent.getBooleanExtra(ConnectIDConstants.RECOVER, false)) {
                    nextRequestCode = ConnectIDConstants.CONNECT_RECOVERY_PRIMARY_PHONE;
                }
            }
            case ConnectIDConstants.CONNECT_UNLOCK_PASSWORD -> {
                if (success) {
                    boolean forgot = intent.getBooleanExtra(ConnectIDConstants.FORGOT, false);
                    if(forgot) {
                        //Begin the recovery workflow
                        nextRequestCode = ConnectIDConstants.CONNECT_RECOVERY_PRIMARY_PHONE;
                        manager.forgotPassword = true;
                    }
                    else {
                        manager.forgotPassword = false;
                        manager.connectStatus = ConnectIDStatus.LoggedIn;
                        manager.loginListener.connectActivityComplete(true);

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

        if(rememberPhase) {
            ConnectIDDatabaseHelper.setRegistrationPhase(manager.parentActivity, manager.phase);
        }

        manager.continueWorkflow();
    }

    //TODO: Re-enable this once we're ready for OIDC
//    public static void getConnectToken() {
//        ConnectIDManager manager = getInstance();
//        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser();
//
//        Multimap<String, String> params = ArrayListMultimap.create();
//        params.put("client_id", "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa");
//        params.put("scope", "openid");
//        params.put("grant_type", "password");
//        params.put("username", user.getUserID());
//        params.put("password", user.getPassword());
//
//        String url = manager.parentActivity.getString(R.string.ConnectURL) + "/o/token/";
//
//        ConnectIDNetworkHelper.post(manager.parentActivity, url, new AuthInfo.NoAuth(), params, new ConnectIDNetworkHelper.INetworkResultHandler() {
//            @Override
//            public void processSuccess(int responseCode, InputStream responseData) {
//                try {
//                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
//                    JSONObject json = new JSONObject(responseAsString);
//                    String key = "access_token";
//                    if (json.has(key)) {
//                        //username = json.getString(key);
//                    }
//                } catch (IOException | JSONException e) {
//                    Logger.exception("Parsing return from OIDC call", e);
//                }
//            }
//
//            @Override
//            public void processFailure(int responseCode, IOException e) {
//                Toast.makeText(manager.parentActivity, "OIDC error", Toast.LENGTH_SHORT).show();
//            }
//        });
//    }

    public static void rememberAppCredentials(String appID, String userID, String passwordOrPin) {
        ConnectIDManager manager = getInstance();
        if(manager.connectStatus == ConnectIDStatus.LoggedIn) {
            ConnectIDDatabaseHelper.storeApp(manager.parentActivity, appID, userID, passwordOrPin);
            //TODO: Re-enable when ready to test OAuth
            //getConnectToken();
        }
    }

    public static void forgetAppCredentials(String appID, String userID) {
        ConnectLinkedAppRecord record = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, appID, userID);
        if(record != null) {
            ConnectIDDatabaseHelper.deleteAppData(manager.parentActivity, record);
        }
    }

    public static AuthInfo.ProvidedAuth getCredentialsForApp(String appID, String userID) {
        if(getInstance().connectStatus != ConnectIDStatus.LoggedIn) {
            return null;
        }

        ConnectLinkedAppRecord record = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, appID, userID);
        if(record != null && record.getPassword().length() > 0) {
            return new AuthInfo.ProvidedAuth(record.getUserID(), record.getPassword(), false);
        }

        return null;
    }
}

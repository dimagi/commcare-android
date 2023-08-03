package org.commcare.activities.connect;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.AppManagerDeveloperPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ConnectIDManager {
    public enum ConnectIDStatus {
        NotIntroduced,
        Registering,
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
    private boolean passwordOnlyWorkflow = false;

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
        if(user != null) {
            if(user.getRegistrationPhase() != ConnectIDConstants.CONNECT_NO_ACTIVITY) {
                manager.connectStatus = ConnectIDStatus.Registering;
            }
            else if(manager.connectStatus == ConnectIDStatus.NotIntroduced) {
                manager.connectStatus = ConnectIDStatus.LoggedOut;
            }
        }
    }

    public static boolean isConnectIDIntroduced() {
        if(!AppManagerDeveloperPreferences.isConnectIDEnabled()) {
            return false;
        }

        return switch(getInstance().connectStatus) {
            case NotIntroduced, Registering -> false;
            case LoggedOut, LoggedIn -> true;
        };
    }

    public static boolean isSignedIn() {
        return AppManagerDeveloperPreferences.isConnectIDEnabled() && getInstance().connectStatus == ConnectIDStatus.LoggedIn;
    }

    public static boolean shouldShowSignInMenuOption() {
        if(!AppManagerDeveloperPreferences.isConnectIDEnabled()) {
            return false;
        }

        return switch(getInstance().connectStatus) {
            case LoggedOut, LoggedIn -> false;
            case NotIntroduced, Registering -> true;
        };
    }

    public static boolean shouldShowSignOutMenuOption() {
        if(!AppManagerDeveloperPreferences.isConnectIDEnabled()) {
            return false;
        }

        return switch(getInstance().connectStatus) {
            case NotIntroduced, Registering, LoggedOut -> false;
            case LoggedIn -> true;
        };
    }

    public static String getConnectButtonText(Context context) {
        return switch (getInstance().connectStatus) {
            case LoggedOut, Registering, NotIntroduced -> context.getString(R.string.connect_button_logged_out);
            case LoggedIn -> context.getString(R.string.connect_button_logged_in);
        };
    }

    public static boolean shouldShowConnectButton() {
        if(!AppManagerDeveloperPreferences.isConnectIDEnabled()) {
            return false;
        }

        return switch(getInstance().connectStatus) {
            case NotIntroduced, Registering, LoggedIn -> false;
            case LoggedOut -> true;
        };
    }

    public static void signOut() {
        if(getInstance().connectStatus == ConnectIDStatus.LoggedIn) {
            getInstance().connectStatus = ConnectIDStatus.LoggedOut;
        }
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
            case LoggedOut, Registering -> {
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

                params.put(ConnectIDConstants.METHOD, ConnectIDConstants.METHOD_REGISTER_PRIMARY);
                params.put(ConnectIDConstants.PHONE, primaryPhone);
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

                params.put(ConnectIDConstants.METHOD, ConnectIDConstants.METHOD_CHANGE_PRIMARY);
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_PASSWORD -> {
                nextActivity = ConnectIDPasswordActivity.class;

                params.put(ConnectIDConstants.USERNAME, user.getUserID());
                params.put(ConnectIDConstants.PASSWORD, user.getPassword());
                params.put(ConnectIDConstants.METHOD, passwordOnlyWorkflow ? "true" : "false");
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                nextActivity = ConnectIDPhoneActivity.class;

                params.put(ConnectIDConstants.METHOD, ConnectIDConstants.METHOD_CHANGE_ALTERNATE);
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

                params.put(ConnectIDConstants.METHOD, ConnectIDConstants.METHOD_RECOVER_PRIMARY);
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
                    else {
                        manager.connectStatus = ConnectIDStatus.Registering;
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
                    manager.passwordOnlyWorkflow = intent.getBooleanExtra(ConnectIDConstants.PASSWORD, false);
                    nextRequestCode = !manager.passwordOnlyWorkflow && configured ? ConnectIDConstants.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC : ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
                }
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_UNLOCK_BIOMETRIC -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE : ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                rememberPhase = success;
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE -> {
                nextRequestCode = manager.passwordOnlyWorkflow ? ConnectIDConstants.CONNECT_REGISTRATION_MAIN : ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS;
                if(success) {
                    boolean changeNumber = intent != null && intent.getBooleanExtra(ConnectIDConstants.CHANGE, false);
                    nextRequestCode = changeNumber ? ConnectIDConstants.CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE : ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                    rememberPhase = !changeNumber;
                }
            }
            case ConnectIDConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE -> {
                nextRequestCode = success ? ConnectIDConstants.CONNECT_REGISTRATION_SUCCESS : ConnectIDConstants.CONNECT_REGISTRATION_CONFIGURE_PASSWORD;
                if(success) {
                    rememberPhase = true;
                    ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
                    if(user != null) {
                        user.setAlternatePhone(intent.getStringExtra(ConnectIDConstants.PHONE));
                        ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);
                    }
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
                nextRequestCode = success ? ConnectIDConstants.CONNECT_REGISTRATION_ALTERNATE_PHONE : ConnectIDConstants.CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE;
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

        if(rememberPhase) {
            ConnectIDDatabaseHelper.setRegistrationPhase(manager.parentActivity, manager.phase);
        }

        manager.continueWorkflow();
    }

    public static AuthInfo.TokenAuth acquireSSOTokenSync() {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        String hqUser;
        try {
            hqUser = CommCareApplication.instance().getRecordForCurrentUser().getUsername();
        } catch(Exception e) {
            //No token if no session
            return null;
        }

        ConnectLinkedAppRecord appRecord = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, seatedAppId, hqUser);
        if(appRecord == null) {
            return null;
        }

        //See if we already have a valid token
        AuthInfo.TokenAuth hqTokenAuth = getTokenCredentialsForApp(seatedAppId, hqUser);
        if(hqTokenAuth == null) {
            //First get a valid Connect token
            AuthInfo.TokenAuth connectToken = getConnectToken();
            if(connectToken == null) {
                //Retrieve a new connect token
                connectToken = retrieveConnectToken();
            }

            if(connectToken == null) {
                //If we can't get a valid Connect token there's no point continuing
                return null;
            }

            //Link user if necessary
            linkHQWorker(hqUser, appRecord.getPassword(), connectToken.bearerToken);

            //Retrieve HQ token
            hqTokenAuth = retrieveHQToken(hqUser, connectToken.bearerToken);
        }

        return hqTokenAuth;
    }

    public static AuthInfo.TokenAuth retrieveConnectToken() {
        ConnectIDManager manager = getInstance();
        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);

        HashMap<String, String> params = new HashMap<>();
        params.put("client_id", "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa");
        params.put("scope", "openid");
        params.put("grant_type", "password");
        params.put("username", user.getUserID());
        params.put("password", user.getPassword());

        String url = manager.parentActivity.getString(R.string.ConnectURL) + "/o/token/";

        ConnectIDNetworkHelper.PostResult postResult = ConnectIDNetworkHelper.postSync(manager.parentActivity, url, new AuthInfo.NoAuth(), params, true);
        if(postResult.e != null) {
            Toast.makeText(manager.parentActivity, "OIDC error", Toast.LENGTH_SHORT).show();
        } else if(postResult.responseCode != 200) {
            Toast.makeText(manager.parentActivity, String.format(Locale.getDefault(), "OIDC error: %d", postResult.responseCode), Toast.LENGTH_SHORT).show();
        } else {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(postResult.responseStream));
                postResult.responseStream.close();
                JSONObject json = new JSONObject(responseAsString);
                String key = "access_token";
                if (json.has(key)) {
                    String token = json.getString(key);
                    Date expiration = new Date();
                    key = "expires_in";
                    int seconds = json.has(key) ? json.getInt(key) : 0;
                    expiration.setTime(expiration.getTime() + ((long)seconds * 1000));
                    user.updateConnectToken(token, expiration);
                    ConnectIDDatabaseHelper.storeUser(manager.parentActivity, user);

                    return new AuthInfo.TokenAuth(token);
                }
            } catch (IOException | JSONException e) {
                Logger.exception("Parsing return from Connect OIDC call", e);
            }
        }

        return null;
    }

    public static void linkHQWorker(String hqUsername, String hqPassword, String connectToken) {
        ConnectIDManager manager = getInstance();
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectLinkedAppRecord appRecord = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, seatedAppId, hqUsername);
        if(appRecord != null && !appRecord.getWorkerLinked()) {
            HashMap<String, String> params = new HashMap<>();
            params.put("token", connectToken);

            String url = ServerUrls.getKeyServer().replace("phone/keys/", "settings/users/commcare/link_connectid_user/");

            try {
                ConnectIDNetworkHelper.PostResult postResult = ConnectIDNetworkHelper.postSync(manager.parentActivity, url, new AuthInfo.ProvidedAuth(hqUsername, hqPassword), params, true);
                if (postResult.e == null && postResult.responseCode == 200) {
                    postResult.responseStream.close();

                    //Remember that we linked the user successfully
                    appRecord.setWorkerLinked(true);
                    ConnectIDDatabaseHelper.storeApp(manager.parentActivity, appRecord);
                }
            } catch (IOException e) {
                //Don't care for now
            }
        }
    }

    public static AuthInfo.TokenAuth retrieveHQToken(String hqUsername, String connectToken) {
        ConnectIDManager manager = getInstance();

        HashMap<String, String> params = new HashMap<>();
        params.put("client_id", "4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV");
        params.put("scope", "sync");
        params.put("grant_type", "password");
        params.put("username", hqUsername + "@" + HiddenPreferences.getUserDomain());
        params.put("password", connectToken);

        String host = "";
        try {
            host = (new URL(ServerUrls.getKeyServer())).getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        String url = "https://" + host + "/oauth/token/";

        ConnectIDNetworkHelper.PostResult postResult = ConnectIDNetworkHelper.postSync(manager.parentActivity, url, new AuthInfo.NoAuth(), params, true);
        if(postResult.e != null) {
            Toast.makeText(manager.parentActivity, "HQ OIDC error", Toast.LENGTH_SHORT).show();
        } else if(postResult.responseCode != 200) {
            Toast.makeText(manager.parentActivity, String.format(Locale.getDefault(), "HQ OIDC error: %d", postResult.responseCode), Toast.LENGTH_SHORT).show();
        } else {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(postResult.responseStream));
                JSONObject json = new JSONObject(responseAsString);
                String key = "access_token";
                if (json.has(key)) {
                    String token = json.getString(key);
                    Date expiration = new Date();
                    key = "expires_in";
                    int seconds = json.has(key) ? json.getInt(key) : 0;
                    expiration.setTime(expiration.getTime() + ((long)seconds * 1000));

                    String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                    ConnectIDDatabaseHelper.storeHQToken(manager.parentActivity, seatedAppId, hqUsername, token, expiration);

                    return new AuthInfo.TokenAuth(token);
                }
            } catch (IOException | JSONException e) {
                Logger.exception("Parsing return from HQ OIDC call", e);
            }
        }

        return null;
    }

    public static void rememberAppCredentials(String appID, String userID, String passwordOrPin) {
        ConnectIDManager manager = getInstance();
        if(isSignedIn()) {
            ConnectIDDatabaseHelper.storeApp(manager.parentActivity, appID, userID, passwordOrPin);
        }
    }

    public static void forgetAppCredentials(String appID, String userID) {
        ConnectLinkedAppRecord record = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, appID, userID);
        if(record != null) {
            ConnectIDDatabaseHelper.deleteAppData(manager.parentActivity, record);
        }
    }

    public static AuthInfo.ProvidedAuth getCredentialsForApp(String appID, String userID) {
        if(isSignedIn()) {
            ConnectLinkedAppRecord record = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, appID, userID);
            if (record != null && record.getPassword().length() > 0) {
                return new AuthInfo.ProvidedAuth(record.getUserID(), record.getPassword(), false);
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getConnectToken() {
        if(isSignedIn()) {
            ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(manager.parentActivity);
            if (user != null && (new Date()).compareTo(user.getConnectTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(user.getConnectToken());
            }
        }

        return null;
    }

    public static AuthInfo.TokenAuth getTokenCredentialsForApp(String appID, String userID) {
        if(isSignedIn()) {
            ConnectLinkedAppRecord record = ConnectIDDatabaseHelper.getAppData(manager.parentActivity, appID, userID);
            if (record != null && (new Date()).compareTo(record.getHQTokenExpiration()) < 0) {
                return new AuthInfo.TokenAuth(record.getHQToken());
            }
        }

        return null;
    }
}

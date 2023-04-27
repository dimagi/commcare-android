package org.commcare.activities;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.widget.Toast;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.dalvik.R;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.EncryptionUtils;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import okhttp3.MediaType;
import okhttp3.RequestBody;

public class ConnectIDManager {
    //ConnectID UI elements hidden from user when this is set to false
    public static final boolean ENABLE_CONNECT_ID = true;

    public enum ConnectIDStatus {
        NotIntroduced,
        LoggedOut,
        LoggedIn
    }

    public enum RegistrationPhase {
        Initial, //Collect primary info: name, DOB, etc.
        Secrets, //Configure fingerprint, PIN, password, etc.
        Unlock, //Unlock a secret after configuring them
        Pictures, //Get pictures of face and official ID,
        PhoneVerify, //Verify phone number via SMS code
        Verify, //Verify phone number via SMS
        RecoverPrimary,
        RecoverSecondary
    }

    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static final int CONNECT_UNLOCK_ACTIVITY = 1001;
    private static final int CONNECT_RECOVERY_DECISION_ACTIVITY = 1002;
    private static final int CONNECT_REGISTER_ACTIVITY = 1003;
    private static final int CONNECT_VERIFY_ACTIVITY = 1004;
    private static final int CONNECT_PICTURES_ACTIVITY = 1005;
    private static final int CONNECT_PHONE_VERIFY_ACTIVITY = 1006;

    private static ConnectIDManager manager = null;
    private final Object connectDbHandleLock = new Object();
    private SQLiteDatabase connectDatabase;
//    private int dbState;
    private ConnectIDStatus connectStatus = ConnectIDStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;
    private ConnectActivityCompleteListener loginListener;
    private RegistrationPhase phase = RegistrationPhase.Initial;
    private String recoveryPhone = null;
    private String recoverySecret = null;

    private ConnectIDManager() {
    }

    private static ConnectIDManager getInstance() {
        if(manager == null) {
            manager = new ConnectIDManager();
        }

        return manager;
    }

    public static void init(CommCareActivity<?> parent) {
        ConnectIDManager manager = getInstance();
        manager.parentActivity = parent;
        manager.initConnectDb();

        ConnectUserRecord user = getUser();
        if(user != null && manager.connectStatus == ConnectIDStatus.NotIntroduced) {
            manager.connectStatus = ConnectIDStatus.LoggedOut;
        }
    }

    private void initConnectDb() {
        SQLiteDatabase database;
        database = new DatabaseConnectOpenHelper(parentActivity).getWritableDatabase(EncryptionUtils.getConnectDBPassphrase(parentActivity));
        database.close();
    }

    public <T extends Persistable> SqlStorage<T> getConnectStorage(Class<T> c) {
        return getConnectStorage(c.getAnnotation(Table.class).value(), c);
    }

    public <T extends Persistable> SqlStorage<T> getConnectStorage(String table, Class<T> c) {
        return new SqlStorage<>(table, c, new AndroidDbHelper(parentActivity.getApplicationContext()) {
            @Override
            public SQLiteDatabase getHandle() {
                synchronized (connectDbHandleLock) {
                    if (connectDatabase == null || !connectDatabase.isOpen()) {
                        connectDatabase = new DatabaseConnectOpenHelper(this.c).getWritableDatabase(EncryptionUtils.getConnectDBPassphrase(parentActivity));
                    }
                    return connectDatabase;
                }
            }
        });
    }

    public static ConnectUserRecord getUser() {
        ConnectUserRecord user = null;
        ConnectIDManager manager = getInstance();
        for (ConnectUserRecord r : manager.getConnectStorage(ConnectUserRecord.class)) {
            user = r;
            break;
        }

        return user;
    }

    public static void storeUser(ConnectUserRecord user) {
        getInstance().getConnectStorage(ConnectUserRecord.class).write(user);
    }

    public static ConnectLinkedAppRecord getAppData(String appId) {
        ConnectLinkedAppRecord record = null;
        ConnectIDManager manager= getInstance();
        for (ConnectLinkedAppRecord r : manager.getConnectStorage(ConnectLinkedAppRecord.class)) {
            if(r.getAppID().equals(appId)) {
                record = r;
                break;
            }
        }

        return record;
    }

    public static void storeApp(ConnectLinkedAppRecord record) {
        getInstance().getConnectStorage(ConnectLinkedAppRecord.class).write(record);
    }

    public static boolean isConnectIDIntroduced() {
        return getInstance().connectStatus != ConnectIDStatus.NotIntroduced;
    }

    public static boolean shouldShowSignInMenuOption() {
        return getInstance().connectStatus == ConnectIDStatus.NotIntroduced;
    }

    public static boolean shouldShowSignOutMenuOption() {
        return getInstance().connectStatus == ConnectIDStatus.LoggedIn;
    }

    public static boolean isConnectIDActivity(int requestCode) {
        return requestCode == CONNECT_RECOVERY_DECISION_ACTIVITY ||
                requestCode == CONNECT_REGISTER_ACTIVITY ||
                requestCode == CONNECT_VERIFY_ACTIVITY ||
                requestCode == CONNECT_UNLOCK_ACTIVITY ||
                requestCode == CONNECT_PICTURES_ACTIVITY ||
                requestCode == CONNECT_PHONE_VERIFY_ACTIVITY;
    }

    public static String getConnectButtonText() {
        return switch (getInstance().connectStatus) {
            case LoggedOut, NotIntroduced -> Localization.get("connect.button.logged.out");
            case LoggedIn -> Localization.get("connect.button.logged.in");
        };
    }

    public static boolean shouldEnableConnectButton() {
        return getInstance().connectStatus != ConnectIDStatus.LoggedIn;
    }

    public static void handleConnectButtonPress(ConnectActivityCompleteListener listener) {
        ConnectIDManager manager = getInstance();
        manager.loginListener = listener;

        switch (manager.connectStatus) {
            case NotIntroduced -> {
                Intent i = new Intent(manager.parentActivity, ConnectIDRecoveryDecisionActivity.class);
                manager.parentActivity.startActivityForResult(i, CONNECT_RECOVERY_DECISION_ACTIVITY);
            }
            case LoggedOut -> {
                Intent i = new Intent(manager.parentActivity, ConnectIDLoginActivity.class);
                manager.parentActivity.startActivityForResult(i, CONNECT_UNLOCK_ACTIVITY);
            }
            case LoggedIn -> {
                //TODO: Go to Connect menu (i.e. educate, verify, etc.)
                Toast.makeText(manager.parentActivity, "Not ready yet",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void signOut() {
        getInstance().connectStatus = ConnectIDStatus.LoggedOut;
    }

    public static void forgetUser() {
        ConnectIDManager manager = getInstance();
        manager.connectStatus = ConnectIDStatus.NotIntroduced;
        manager.getConnectStorage(ConnectUserRecord.class).remove(getUser().getID());
    }

    private void resetPassword() {
        String password = ConnectIDRegistrationActivity.generatePassword();

        HashMap<String, String> params = new HashMap<>();
        AuthInfo authInfo = new AuthInfo.NoAuth();
        params.put("phone", recoveryPhone);
        params.put("secret_key", recoverySecret);
        params.put("password", password);
        String url = parentActivity.getString(R.string.ConnectURL) + "/users/recovery/reset_password";

        //params.put("device_id", CommCareApplication.instance().getPhoneId());

        Gson gson = new Gson();
        String json = gson.toJson(params);

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        ModernHttpTask postTask =
                new ModernHttpTask(parentActivity, url,
                        ImmutableMultimap.of(),
                        new HashMap<>(),
                        requestBody,
                        HTTPMethod.POST,
                        authInfo);
        postTask.connect(new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                String username = "";
                String displayName = "";
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    JSONObject json = new JSONObject(responseAsString);
                    String key = "username";
                    if (json.has(key)) {
                        username = json.getString(key);
                    }

                    key = "name";
                    if (json.has(key)) {
                        displayName = json.getString(key);
                    }
                }
                catch(IOException | JSONException e) {
                    Logger.exception("Parsing return from reset_password", e);
                }
                storeUser(new ConnectUserRecord(username, password, displayName));

                //We're done with these, so null them out
                recoveryPhone = null;
                recoverySecret = null;

                connectStatus = ConnectIDStatus.LoggedIn;
                loginListener.connectActivityComplete(true);

                Toast.makeText(parentActivity, "Account recovered!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processClientError(int responseCode) {
                //400 error
                Toast.makeText(parentActivity, "Password reset: Client error", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void processServerError(int responseCode) {
                Toast.makeText(parentActivity, "Password reset: Server error", Toast.LENGTH_SHORT).show();
                //500 error for internal server error
            }

            @Override
            public void processOther(int responseCode) {
                Toast.makeText(parentActivity, "Password reset: Other error", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void handleIOException(IOException exception) {
                Toast.makeText(parentActivity, "Password reset: Exception", Toast.LENGTH_SHORT).show();
                //UnknownHostException if host not found
            }

            @Override
            public <A, B, C> void connectTask(CommCareTask<A, B, C, HttpResponseProcessor> task) {}

            @Override
            public void startBlockingForTask(int id) {}

            @Override
            public void stopBlockingForTask(int id) {}

            @Override
            public void taskCancelled() {}

            @Override
            public HttpResponseProcessor getReceiver() { return this; }

            @Override
            public void startTaskTransition() {}

            @Override
            public void stopTaskTransition(int taskId) {}

            @Override
            public void hideTaskCancelButton() {}
        });
        postTask.executeParallel();
    }

    public static void handleFinishedActivity(int requestCode, int resultCode, Intent intent) {
        ConnectIDManager manager = getInstance();
        boolean success = resultCode == RESULT_OK;
        RegistrationPhase nextPhase = RegistrationPhase.Initial;

        switch (requestCode) {
            case CONNECT_RECOVERY_DECISION_ACTIVITY -> {
                if(success) {
                    boolean createNew = intent.getBooleanExtra(ConnectIDRecoveryDecisionActivity.CREATE, false);
                    String phone = intent.getStringExtra(ConnectIDRecoveryDecisionActivity.PHONE);

                    if(createNew) {
                        Intent i = new Intent(manager.parentActivity, ConnectIDRegistrationActivity.class);
                        manager.parentActivity.startActivityForResult(i, CONNECT_REGISTER_ACTIVITY);
                    }
                    else {
                        nextPhase = RegistrationPhase.RecoverPrimary;
                        manager.recoveryPhone = phone;
                    }
                }
            }
            case CONNECT_REGISTER_ACTIVITY -> {
                nextPhase = success ? RegistrationPhase.Secrets : RegistrationPhase.Initial;
                if(success) {
                    ConnectUserRecord user = ConnectUserRecord.getUserFromIntent(intent);
                    storeUser(user);
                }
            }
            case CONNECT_VERIFY_ACTIVITY -> {
                //Backing up here is problematic, we just created a new account...
                nextPhase = success ? RegistrationPhase.Unlock : RegistrationPhase.Initial;
            }
            case CONNECT_UNLOCK_ACTIVITY -> {
                if(manager.phase == RegistrationPhase.Unlock) {
                    nextPhase = success ? RegistrationPhase.Pictures : RegistrationPhase.Secrets;
                }
                else {
                    if (success) {
                        manager.connectStatus = ConnectIDStatus.LoggedIn;
                        manager.loginListener.connectActivityComplete(true);
                    }

                    return;
                }
            }
            case CONNECT_PICTURES_ACTIVITY -> {
                nextPhase = success ? RegistrationPhase.PhoneVerify : RegistrationPhase.Secrets;
            }
            case CONNECT_PHONE_VERIFY_ACTIVITY -> {
                //Action depends since we use this page several times
                switch (manager.phase) {
                    case RecoverPrimary -> {
                        nextPhase = success ? RegistrationPhase.RecoverSecondary : RegistrationPhase.Initial;
                        if (success) {
                            //Remember the secret key for use through the rest of the recovery process
                            manager.recoverySecret = intent.getStringExtra(ConnectIDPhoneVerificationActivity.PASSWORD);
                        }
                    }
                    case RecoverSecondary -> {
                        nextPhase = success ? RegistrationPhase.Initial : RegistrationPhase.RecoverPrimary;

                        if(success) {
                            //Create and set new password
                            manager.resetPassword();
                        }
                    }
                    default -> {
                        nextPhase = success ? RegistrationPhase.Initial : RegistrationPhase.Pictures;
                        if (success) {
                            //Finish workflow, user registered and logged in
                            manager.connectStatus = ConnectIDStatus.LoggedIn;
                            manager.loginListener.connectActivityComplete(true);
                        }
                    }
                }
            }
        }

        manager.phase = nextPhase;

        //Determine activity to launch for next phase
        Class<?> nextActivity = null;
        int nextRequestCode = -1;
        switch (manager.phase) {
            case Secrets -> {
                nextActivity = ConnectIDVerificationActivity.class;
                nextRequestCode = CONNECT_VERIFY_ACTIVITY;
            }
            case Unlock -> {
                nextActivity = ConnectIDLoginActivity.class;
                nextRequestCode = CONNECT_UNLOCK_ACTIVITY;
            }
            case Pictures -> {
                nextActivity = ConnectIDPicturesActivity.class;
                nextRequestCode = CONNECT_PICTURES_ACTIVITY;
            }
            case PhoneVerify, RecoverPrimary, RecoverSecondary -> {
                nextActivity = ConnectIDPhoneVerificationActivity.class;
                nextRequestCode = CONNECT_PHONE_VERIFY_ACTIVITY;
            }
        }

        if(nextActivity != null) {
            Intent i = new Intent(manager.parentActivity, nextActivity);

            ConnectUserRecord user = getUser();
            if(user != null) {
                i.putExtra(ConnectIDPhoneVerificationActivity.USERNAME, user.getUserID());
                i.putExtra(ConnectIDPhoneVerificationActivity.PASSWORD, user.getPassword());
            }
            else if(manager.phase == RegistrationPhase.RecoverPrimary) {
                i.putExtra(ConnectIDPhoneVerificationActivity.METHOD, ConnectIDPhoneVerificationActivity.MethodRecoveryPrimary);
                i.putExtra(ConnectIDPhoneVerificationActivity.USERNAME, manager.recoveryPhone);
                i.putExtra(ConnectIDPhoneVerificationActivity.PASSWORD, "");
            }
            else if(manager.phase == RegistrationPhase.RecoverSecondary) {
                i.putExtra(ConnectIDPhoneVerificationActivity.METHOD, ConnectIDPhoneVerificationActivity.MethodRecoveryAlternate);
                i.putExtra(ConnectIDPhoneVerificationActivity.USERNAME, manager.recoveryPhone);
                i.putExtra(ConnectIDPhoneVerificationActivity.PASSWORD, manager.recoverySecret);
            }

            manager.parentActivity.startActivityForResult(i, nextRequestCode);
        }
    }

    public static void rememberAppCredentials(String appID, String username, String passwordOrPin) {
        ConnectIDManager manager = getInstance();
        if(manager.connectStatus == ConnectIDStatus.LoggedIn) {
            storeApp(new ConnectLinkedAppRecord(appID, username, passwordOrPin));
        }
    }

    public static AuthInfo.BasicAuth getCredentialsForApp(String appID) {
        if(getInstance().connectStatus != ConnectIDStatus.LoggedIn) {
            return null;
        }

        ConnectLinkedAppRecord record = getAppData(appID);
        if(record != null) {
            return new AuthInfo.BasicAuth(record.getUserID(), record.getPassword());
        }

        return null;
    }
}

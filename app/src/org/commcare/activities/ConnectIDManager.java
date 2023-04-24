package org.commcare.activities;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.models.database.AndroidDbHelper;
import org.commcare.models.database.MigrationException;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.connect.DatabaseConnectOpenHelper;
import org.commcare.modern.database.Table;
import org.commcare.utils.EncryptionUtils;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.Persistable;

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
        Verify //Verify phone number via SMS
    }

    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static final int CONNECT_UNLOCK_ACTIVITY = 1001;
    private static final int CONNECT_REGISTER_ACTIVITY = 1002;
    private static final int CONNECT_VERIFY_ACTIVITY = 1003;
    private static final int CONNECT_PICTURES_ACTIVITY = 1005;
    private static final int CONNECT_PHONE_VERIFY_ACTIVITY = 1006;

    private static final int STATE_UNINSTALLED = 0;
    private static final int STATE_READY = 2;
    public static final int STATE_CORRUPTED = 4;
    public static final int STATE_LEGACY_DETECTED = 8;
    public static final int STATE_MIGRATION_FAILED = 16;
    public static final int STATE_MIGRATION_QUESTIONABLE = 32;


    private static ConnectIDManager manager = null;
    private final Object connectDbHandleLock = new Object();
    private SQLiteDatabase connectDatabase;
//    private int dbState;
    private ConnectIDStatus connectStatus = ConnectIDStatus.NotIntroduced;
    private CommCareActivity<?> parentActivity;
    private ConnectActivityCompleteListener loginListener;
    private ConnectActivityCompleteListener registrationListener;
    private RegistrationPhase phase = RegistrationPhase.Initial;

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

    private int initConnectDb() {
        SQLiteDatabase database;
        try {
            database = new DatabaseConnectOpenHelper(parentActivity).getWritableDatabase(EncryptionUtils.GetConnectDbPassphrase(parentActivity));
            database.close();
            return STATE_READY;
        } catch (SQLiteException e) {
            // Only thrown if DB isn't there
            return STATE_UNINSTALLED;
        } catch (MigrationException e) {
            if (e.isDefiniteFailure()) {
                return STATE_MIGRATION_FAILED;
            } else {
                return STATE_MIGRATION_QUESTIONABLE;
            }
        }
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
                        connectDatabase = new DatabaseConnectOpenHelper(this.c).getWritableDatabase(EncryptionUtils.GetConnectDbPassphrase(parentActivity));
                    }
                    return connectDatabase;
                }
            }
        });
    }

    public static ConnectUserRecord getUser() {
        ConnectUserRecord user = null;
        ConnectIDManager manager= getInstance();
        for (ConnectUserRecord r : manager.getConnectStorage(ConnectUserRecord.class)) {
            user = r;
            break;
        }

        return user;
    }

    public static void storeUser(ConnectUserRecord user) {
        getInstance().getConnectStorage(ConnectUserRecord.class).write(user);
    }

    public static void loadUserFromIntent(Intent intent) {
        ConnectUserRecord user = ConnectUserRecord.getUserFromIntent(intent);
        storeUser(user);
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
        return requestCode == CONNECT_REGISTER_ACTIVITY ||
                requestCode == CONNECT_VERIFY_ACTIVITY ||
                requestCode == CONNECT_UNLOCK_ACTIVITY ||
                requestCode == CONNECT_PICTURES_ACTIVITY ||
                requestCode == CONNECT_PHONE_VERIFY_ACTIVITY;
    }

    public static String getConnectButtonText() {
        return switch (getInstance().connectStatus) {
            case LoggedOut -> Localization.get("connect.button.logged.out");
            case LoggedIn -> Localization.get("connect.button.logged.in");
            default -> "";
        };
    }

    public static boolean shouldEnableConnectButton() {
        return getInstance().connectStatus != ConnectIDStatus.LoggedIn;
    }

    public static void handleConnectButtonPress(CommCareActivity<?> activity, ConnectActivityCompleteListener listener) {
        ConnectIDManager manager = getInstance();
        manager.parentActivity = activity;
        manager.loginListener = listener;

        switch (manager.connectStatus) {
            case NotIntroduced -> {
                final PaneledChoiceDialog dialog = new PaneledChoiceDialog(activity, Localization.get("connect.dialog.unrecognized"));

                DialogChoiceItem newAccountChoice = new DialogChoiceItem(
                        Localization.get("connect.dialog.new.account"), -1, v -> {
                    //New account
                    activity.dismissAlertDialog();
                    beginRegistrationWorkflow(activity, listener);
                });

                DialogChoiceItem sameNumberChoice = new DialogChoiceItem(
                        Localization.get("connect.dialog.same.number"), -1, v -> {
                    //Existing account, same phone number
                    activity.dismissAlertDialog();

                    //TODO: Handle simple account recovery (same phone number)
                    Toast.makeText(manager.parentActivity, "Not ready yet",
                            Toast.LENGTH_SHORT).show();
                });

                DialogChoiceItem newNumberChoice = new DialogChoiceItem(
                        Localization.get("connect.dialog.new.number"), -1, v -> {
                    //Existing account, new phone number
                    activity.dismissAlertDialog();

                    //TODO: Handle advanced account recovery (new phone number)
                    Toast.makeText(manager.parentActivity, "Not ready yet",
                            Toast.LENGTH_SHORT).show();
                });

                dialog.setChoiceItems(
                        new DialogChoiceItem[]{newAccountChoice, sameNumberChoice, newNumberChoice});
                //dialog.addCollapsibleInfoPane(Localization.get("pin.dialog.extra.info"));
                activity.showAlertDialog(dialog);
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

    public static void beginRegistrationWorkflow(CommCareActivity<?> activity, ConnectActivityCompleteListener listener) {
        getInstance().parentActivity = activity;
        getInstance().registrationListener = listener;

        Intent i = new Intent(activity, ConnectIDRegistrationActivity.class);
        activity.startActivityForResult(i, CONNECT_REGISTER_ACTIVITY);
    }

    public static void handleFinishedActivity(int requestCode, int resultCode, Intent intent) {
        ConnectIDManager manager = getInstance();
        boolean success = resultCode == RESULT_OK;
        RegistrationPhase nextPhase = RegistrationPhase.Initial;

        switch (requestCode) {
            case CONNECT_REGISTER_ACTIVITY -> {
                nextPhase = success ? RegistrationPhase.Secrets : RegistrationPhase.Initial;
                if(success) {
                    loadUserFromIntent(intent);
                }
            }
            case CONNECT_VERIFY_ACTIVITY ->
                //Backing up here is problematic, we just created a new account...
                    nextPhase = success ? RegistrationPhase.Unlock : RegistrationPhase.Initial;
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
            case CONNECT_PICTURES_ACTIVITY ->
                    nextPhase = success ? RegistrationPhase.PhoneVerify : RegistrationPhase.Secrets;
            case CONNECT_PHONE_VERIFY_ACTIVITY -> {
                nextPhase = success ? RegistrationPhase.Initial : RegistrationPhase.Pictures;
                if(success)
                {
                    //Finish workflow, user registered and logged in
                    manager.connectStatus = ConnectIDStatus.LoggedIn;
                    manager.registrationListener.connectActivityComplete(true);
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
            case PhoneVerify -> {
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

            manager.parentActivity.startActivityForResult(i, nextRequestCode);
        }
    }

    public static void rememberAppCreds(String appID, String username, String passwordOrPin) {
        ConnectIDManager manager = getInstance();
        if(manager.connectStatus == ConnectIDStatus.LoggedIn) {
            SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            boolean wipe = username == null || username.length() == 0;
            editor.putString("User " + appID, wipe ? "" : username);
            editor.putString("Pass " + appID, wipe ? "" : passwordOrPin);

            editor.apply();
        }
    }

    public static AuthInfo.BasicAuth getCredsForApp(String appID) {
        if(getInstance().connectStatus != ConnectIDStatus.LoggedIn) {
            return null;
        }

        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String username = prefs.getString("User " + appID, null);
        String pass = prefs.getString("Pass " + appID, null);

        if(username == null || pass == null || username.length() == 0 || pass.length() == 0) {
            return null;
        }

        return new AuthInfo.BasicAuth(username, pass);
    }
}

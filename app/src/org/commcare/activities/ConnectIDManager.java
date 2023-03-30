package org.commcare.activities;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

public class ConnectIDManager {
    public enum ConnectIDStatus {
        NotIntroduced,
        LoggedOut,
        LoggedIn
    }

    public enum RegistrationPhase {
        Initial, //Collect primary info: name, DOB, etc.
        Secrets, //Configure fingerprint, PIN, password, etc.
        Pictures, //Get pictures of face and official ID,
        PhoneVerify, //Verify phone number via SMS code
        Verify //Verify phone number via SMS
    }

    public interface ConnectActivityCompleteListener {
        void connectActivityComplete(boolean success);
    }

    private static final int UNLOCK_CONNECT_ACTIVITY = 1001;
    private static final int CONNECT_REGISTER_ACTIVITY = 1002;
    private static final int CONNECT_VERIFY_ACTIVITY = 1003;
    private static final int CONNECT_PICTURES_ACTIVITY = 1004;
    private static final int CONNECT_PHONE_VERIFY_ACTIVITY = 1005;

    private static ConnectIDManager manager = null;
    private ConnectIDStatus connectStatus = ConnectIDStatus.NotIntroduced;
    private Activity parentActivity;
    private ConnectActivityCompleteListener loginListener;
    private ConnectActivityCompleteListener registrationListener;
    private RegistrationPhase phase;

    private ConnectIDUser user = null;

    private ConnectIDManager() {
        phase = RegistrationPhase.Initial;
    }

    private static ConnectIDManager getInstance() {
        if(manager == null) {
            manager = new ConnectIDManager();
        }

        return manager;
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
                requestCode == CONNECT_PICTURES_ACTIVITY ||
                requestCode == CONNECT_PHONE_VERIFY_ACTIVITY;
    }

    public static String getConnectButtonText() {
        return switch (getInstance().connectStatus) {
            case LoggedOut -> "Login to Connect ID";
            case LoggedIn -> "Go to Connect menu";
            default -> "";
        };
    }

    public static void handleConnectButtonPress(Activity activity, ConnectActivityCompleteListener listener) {
        ConnectIDManager manager = getInstance();
        manager.parentActivity = activity;
        manager.loginListener = listener;

        switch (manager.connectStatus) {
            case NotIntroduced, LoggedOut -> {
                Intent i = new Intent(manager.parentActivity, ConnectIDLoginActivity.class);
                manager.parentActivity.startActivityForResult(i, UNLOCK_CONNECT_ACTIVITY);
            }
            case LoggedIn ->
                //TODO: Go to Connect menu (i.e. educate, verify, etc.)
                    Toast.makeText(manager.parentActivity, "TODO: Go to Connect menu",
                            Toast.LENGTH_SHORT).show();
        }
    }

    public static void signOut() {
        getInstance().connectStatus = ConnectIDStatus.LoggedOut;
    }

    public static void beginRegistrationWorkflow(Activity activity, ConnectActivityCompleteListener listener) {
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
            case UNLOCK_CONNECT_ACTIVITY -> {
                if (success) {
                    manager.connectStatus = ConnectIDStatus.LoggedIn;
                    manager.loginListener.connectActivityComplete(true);
                }
                return;
            }
            case CONNECT_REGISTER_ACTIVITY -> {
                nextPhase = success ? RegistrationPhase.Secrets : RegistrationPhase.Initial;
                if(success) {
                    manager.user = new ConnectIDUser();
                    manager.user.Username = intent.getStringExtra(ConnectIDRegistrationActivity.USERNAME);
                    manager.user.Password = intent.getStringExtra(ConnectIDRegistrationActivity.PASSWORD);
                    manager.user.Name = intent.getStringExtra(ConnectIDRegistrationActivity.NAME);
                    manager.user.DOB = intent.getStringExtra(ConnectIDRegistrationActivity.DOB);
                    manager.user.Phone = intent.getStringExtra(ConnectIDRegistrationActivity.PHONE);
                    manager.user.AltPhone = intent.getStringExtra(ConnectIDRegistrationActivity.ALTPHONE);
                }
            }
            case CONNECT_VERIFY_ACTIVITY ->
                //Backing up here is problematic, we just created a new account...
                    nextPhase = success ? RegistrationPhase.Pictures : RegistrationPhase.Initial;
            case CONNECT_PICTURES_ACTIVITY ->
                    nextPhase = success ? RegistrationPhase.PhoneVerify : RegistrationPhase.Secrets;
            case CONNECT_PHONE_VERIFY_ACTIVITY -> {
                nextPhase = success ? RegistrationPhase.Initial : RegistrationPhase.Pictures;
                if(success)
                {
                    //Finish workflow, user registered and logged in
                    manager.registrationListener.connectActivityComplete(true);
                }
            }
        }

        //Determine activity to launch for next phase
        Class<?> nextActivity = null;
        int nextRequestCode = -1;
        switch (nextPhase) {
            case Secrets -> {
                nextActivity = ConnectIDVerificationActivity.class;
                nextRequestCode = CONNECT_VERIFY_ACTIVITY;
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

        manager.phase = nextPhase;

        if(nextActivity != null) {
            Intent i = new Intent(manager.parentActivity, nextActivity);
            if(manager.user != null) {
                i.putExtra(ConnectIDPhoneVerificationActivity.USERNAME, manager.user.Username);
                i.putExtra(ConnectIDPhoneVerificationActivity.PASSWORD, manager.user.Password);
            }
            manager.parentActivity.startActivityForResult(i, nextRequestCode);
        }
    }
}

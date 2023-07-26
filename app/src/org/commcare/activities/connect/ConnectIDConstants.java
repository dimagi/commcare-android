package org.commcare.activities.connect;

public class ConnectIDConstants {
    public static final String METHOD = "METHOD";
    public static final String CREATE = "CREATE";
    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String NAME = "NAME";
    public static final String PHONE = "PHONE";
    public static final String ALT_PHONE = "ALT_PHONE";

    public static final String TITLE = "TITLE";
    public static final String MESSAGE = "MESSAGE";
    public static final String BUTTON = "BUTTON";
    public static final String SECRET = "SECRET";

    public static final String RECOVER = "RECOVER";
    public static final String CONFIGURED = "CONFIGURED";
    public static final String CHANGE = "CHANGE";
    public static final String FORGOT = "FORGOT";
    public static final String ALLOW_PASSWORD = "ALLOW_PASSWORD";


    private static final  int activityAssigner = 1000;
    public static final int CONNECT_NO_ACTIVITY = activityAssigner + 1;
    public static final int CONNECT_REGISTER_OR_RECOVER_DECISION = activityAssigner + 2;
    public static final int CONNECT_REGISTRATION_PRIMARY_PHONE = activityAssigner + 3;
    public static final int CONNECT_REGISTRATION_CONSENT = activityAssigner + 4;
    public static final int CONNECT_REGISTRATION_MAIN = activityAssigner + 5;
    public static final int CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS = activityAssigner + 6;
    public static final int CONNECT_REGISTRATION_UNLOCK_BIOMETRIC = activityAssigner + 7;
    public static final int CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE = activityAssigner + 8;
    public static final int CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE = activityAssigner + 9;
    public static final int CONNECT_REGISTRATION_CONFIGURE_PASSWORD = activityAssigner + 10;
    public static final int CONNECT_REGISTRATION_SUCCESS = activityAssigner + 11;
    public static final int CONNECT_RECOVERY_PRIMARY_PHONE = activityAssigner + 12;
    public static final int CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE = activityAssigner + 13;
    public static final int CONNECT_RECOVERY_VERIFY_PASSWORD = activityAssigner + 14;
    public static final int CONNECT_RECOVERY_ALT_PHONE_MESSAGE = activityAssigner + 15;
    public static final int CONNECT_RECOVERY_VERIFY_ALT_PHONE = activityAssigner + 16;
    public static final int CONNECT_RECOVERY_CHANGE_PASSWORD = activityAssigner + 17;
    public static final int CONNECT_RECOVERY_SUCCESS = activityAssigner + 18;

    public static final int CONNECT_UNLOCK_BIOMETRIC = activityAssigner + 19;
    public static final int CONNECT_UNLOCK_PASSWORD = activityAssigner + 20;
}
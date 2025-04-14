package org.commcare.connect;

/**
 * Constants used for ConnectID, i.e. when passing params to activities
 *
 * @author dviggiano
 */
public class ConnectConstants {
    public static final int CONNECT_ID_TASK_ID_OFFSET = 1000;
    public final static int CREDENTIAL_PICKER_REQUEST = 2000;
    public static final int CONNECTID_REQUEST_CODE = 1034;
    public static final int LOGIN_CONNECT_LAUNCH_REQUEST_CODE = 1050;
    public static final int COMMCARE_SETUP_CONNECT_LAUNCH_REQUEST_CODE = 1051;
    public static final int STANDARD_HOME_CONNECT_LAUNCH_REQUEST_CODE = 1051;
    public static final int NETWORK_ACTIVITY_ID = 7000;
    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String PIN = "PIN";
    public static final String TASK = "TASK";
    public static final String NAME = "NAME";
    public static final String PHONE = "PHONE";
    public static final String ALT_PHONE = "ALT_PHONE";
    public static final String CONNECT_KEY_TOKEN = "access_token";
    public static final String CONNECT_KEY_EXPIRES = "expires_in";
    public static final String BEGIN_REGISTRATION = "BEGIN_REGISTRATION";
    public static final String VERIFY_PHONE = "VERIFY_PHONE";
    public static final String METHOD_REGISTER_PRIMARY = "REGISTER_PRIMARY";
    public static final String METHOD_CHANGE_PRIMARY = "CHANGE_PRIMARY";
    public static final String METHOD_CHANGE_ALTERNATE = "CHANGE_ALTERNATE";
    public static final String METHOD_RECOVER_PRIMARY = "RECOVER_PRIMARY";
    public static final String CONNECT_KEY_USERNAME = "username";
    public static final String CONNECT_KEY_NAME = "name";
    public static final String CONNECT_KEY_SECRET = "secret";
    public static final String CONNECT_KEY_SECONDARY_PHONE = "secondary_phone";
    public static final String CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY = "secondary_phone_validate_by";
    public static final String CONNECT_KEY_DB_KEY = "db_key";
    public static final String JOB_NEW_OPPORTUNITY = "job-new-opportunity";
    public static final String JOB_LEARNING = "job-learning";
    public static final String JOB_DELIVERY = "job-delivery";
    public static final String NEW_APP = "new-app";
    public static final String LEARN_APP = "learn-app";
    public static final String DELIVERY_APP = "delivery-app";
    public final static int CONNECT_NO_ACTIVITY = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET;
    public final static int CONNECT_REGISTRATION_PRIMARY_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 2;
    public final static int CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 5;
    public final static int CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 7;
    public final static int CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 8;
    public final static int CONNECT_REGISTRATION_ALTERNATE_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 10;
    public final static int CONNECT_REGISTRATION_SUCCESS = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 11;
    public final static int CONNECT_RECOVERY_PRIMARY_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 12;
    public final static int CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 13;
    public final static int CONNECT_RECOVERY_VERIFY_PASSWORD = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 14;
    public final static int CONNECT_RECOVERY_ALT_PHONE_MESSAGE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 15;
    public final static int CONNECT_RECOVERY_VERIFY_ALT_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 16;
    public final static int CONNECT_RECOVERY_SUCCESS = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 18;
    public final static int CONNECT_UNLOCK_BIOMETRIC = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 19;
    public final static int CONNECT_UNLOCK_PASSWORD = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 20;
    public final static int CONNECT_UNLOCK_PIN = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 21;
    public final static int CONNECT_BIOMETRIC_ENROLL_FAIL = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 22;
    public final static int CONNECT_REGISTRATION_CONFIGURE_PIN = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 24;
    public final static int CONNECT_REGISTRATION_CONFIRM_PIN = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 25;
    public final static int CONNECT_RECOVERY_VERIFY_PIN = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 26;
    public final static int CONNECT_RECOVERY_CHANGE_PIN = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 27;
    public final static int CONNECT_RECOVERY_CONFIGURE_BIOMETRICS = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 28;
    public final static int CONNECT_VERIFY_ALT_PHONE_MESSAGE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 30;
    public final static int CONNECT_VERIFY_ALT_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 31;
    public final static int CONNECT_UNLOCK_ALT_PHONE_MESSAGE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 32;
    public final static int CONNECT_UNLOCK_VERIFY_ALT_PHONE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 33;
    public final static int CONNECT_JOB_INFO = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 34;
    public final static int CONNECT_REGISTRATION_CHANGE_PIN = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 35;
    public final static int CONNECT_UNLOCK_ALT_PHONE_CHANGE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 36;
    public final static int CONNECT_VERIFY_ALT_PHONE_CHANGE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 37;
    public final static int CONNECT_RECOVERY_WRONG_PIN = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 38;
    public final static int CONNECT_REGISTRATION_WRONG_PIN = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 39;

    public final static int CONNECT_USER_DEACTIVATE_CONFIRMATION = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 40;
    public final static int CONNECT_VERIFY_USER_DEACTIVATE = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 41;
    public final static int CONNECT_USER_DEACTIVATE_SUCCESS = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 42;
    public final static int CONNECT_RECOVERY_WRONG_PASSWORD = ConnectConstants.CONNECT_ID_TASK_ID_OFFSET + 43;
}

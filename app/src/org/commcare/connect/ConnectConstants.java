package org.commcare.connect;

/**
 * Constants used for ConnectID, i.e. when passing params to activities
 *
 * @author dviggiano
 */
public class ConnectConstants {

    public static final int PERSONAL_ID_TASK_ID_OFFSET = 1000;
    public final static int CREDENTIAL_PICKER_REQUEST = 2000;
    public static final int LOGIN_CONNECT_LAUNCH_REQUEST_CODE = 1050;
    public static final int COMMCARE_SETUP_CONNECT_LAUNCH_REQUEST_CODE = 1051;
    public static final int STANDARD_HOME_CONNECT_LAUNCH_REQUEST_CODE = 1052;
    public static final int CONFIGURE_BIOMETRIC_REQUEST_CODE = 1053;
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
    public static final String USER_PHOTO = "PHOTO";
    public static final String IS_DEMO = "IS_DEMO";
    public static final String PIN_LAST_VERIFIED_DATE = "PIN_LAST_VERIFIED_DATE";
    public static final String CCC_DEST_OPPORTUNITY_SUMMARY_PAGE = "ccc_opportunity_summary_page";
    public static final String CCC_DEST_LEARN_PROGRESS = "ccc_learn_progress";
    public static final String CCC_DEST_DELIVERY_PROGRESS = "ccc_delivery_progress";
    public static final String CCC_DEST_PAYMENTS = "ccc_payment";
    public static final String CONNECT_KEY_USERNAME = "username";
    public static final String CONNECT_KEY_NAME = "name";
    public static final String PERSONALID_KEY_SECRET = "secret";
    public static final String CONNECT_KEY_DB_KEY = "db_key";
    public static final String JOB_NEW_OPPORTUNITY = "job-new-opportunity";
    public static final String JOB_LEARNING = "job-learning";
    public static final String JOB_DELIVERY = "job-delivery";
    public static final String NEW_APP = "new-app";
    public static final String LEARN_APP = "learn-app";
    public static final String DELIVERY_APP = "delivery-app";
    public final static int PERSONALID_NO_ACTIVITY = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET;
    public final static int PERSONALID_REGISTRATION_PRIMARY_PHONE = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 1;
    public final static int PERSONALID_REGISTRATION_CONFIGURE_BIOMETRICS = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET
            + 2;
    public final static int PERSONALID_REGISTRATION_VERIFY_PRIMARY_PHONE = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET
            + 3;
    public final static int PERSONALID_REGISTRATION_CHANGE_PRIMARY_PHONE = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET
            + 4;
    public final static int PERSONALID_REGISTRATION_SUCCESS = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 5;
    public final static int PERSONALID_RECOVERY_SUCCESS = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 6;
    public final static int PERSONALID_UNLOCK_BIOMETRIC = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 7;
    public final static int PERSONALID_UNLOCK_PIN = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 8;
    public final static int PERSONALID_BIOMETRIC_ENROLL_FAIL = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 9;
    public final static int PERSONALID_REGISTRATION_CONFIGURE_PIN = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 10;
    public final static int PERSONALID_REGISTRATION_CONFIRM_PIN = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 11;
    public final static int PERSONALID_RECOVERY_VERIFY_PIN = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 12;
    public final static int PERSONALID_RECOVERY_CHANGE_PIN = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 13;
    public final static int CONNECT_JOB_INFO = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 14;
    public final static int PERSONALID_RECOVERY_WRONG_PIN = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 15;
    public final static int PERSONALID_DEVICE_CONFIGURATION_FAILED = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 16;
    public final static int PERSONALID_RECOVERY_ACCOUNT_ORPHANED = ConnectConstants.PERSONAL_ID_TASK_ID_OFFSET + 17;
}

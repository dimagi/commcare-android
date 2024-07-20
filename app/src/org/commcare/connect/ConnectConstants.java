package org.commcare.connect;

/**
 * Constants used for ConnectID, i.e. when passing params to activities
 *
 * @author dviggiano
 */
public class ConnectConstants {
    public static final int ConnectIdTaskIdOffset = 1000;

    public static int CREDENTIAL_PICKER_REQUEST = 2000;
    public static final String METHOD = "METHOD";
    public static final String CREATE = "CREATE";
    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String PIN = "PIN";
    public static final String NAME = "NAME";
    public static final String PHONE = "PHONE";
    public static final String ALT_PHONE = "ALT_PHONE";

    public static final String TITLE = "TITLE";
    public static final String MESSAGE = "MESSAGE";
    public static final String BUTTON = "BUTTON";
    public static final String BUTTON2 = "BUTTON2";
    public static final String SECRET = "SECRET";

    public static final String RECOVER = "RECOVER";
    public static final String ENROLL_FAIL = "ENROLL_FAIL";
    public static final String CHANGE = "CHANGE";
    public static final String FORGOT = "FORGOT";
    public static final String WRONG_PIN = "WRONG_PIN";
    public static final String ALLOW_PASSWORD = "ALLOW_PASSWORD";

    public static final String METHOD_REGISTER_PRIMARY = "REGISTER_PRIMARY";
    public static final String METHOD_CHANGE_PRIMARY = "CHANGE_PRIMARY";
    public static final String METHOD_CHANGE_ALTERNATE = "CHANGE_ALTERNATE";
    public static final String METHOD_RECOVER_PRIMARY = "RECOVER_PRIMARY";

    public static final String CONNECT_KEY_TOKEN = "access_token";
    public static final String CONNECT_KEY_EXPIRES = "expires_in";
    public static final String CONNECT_KEY_USERNAME = "username";
    public static final String CONNECT_KEY_NAME = "name";
    public static final String CONNECT_KEY_SECRET = "secret";
    public static final String CONNECT_KEY_SECONDARY_PHONE = "secondary_phone";
    public static final String CONNECT_KEY_VALIDATE_SECONDARY_PHONE_BY = "secondary_phone_validate_by";
    public static final String CONNECT_KEY_DB_KEY = "db_key";
}

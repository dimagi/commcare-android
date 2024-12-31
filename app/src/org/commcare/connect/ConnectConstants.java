package org.commcare.connect;

/**
 * Constants used for ConnectID, i.e. when passing params to activities
 *
 * @author dviggiano
 */
public class ConnectConstants {
    public static final int CONNECTID_TASKID_OFFSET = 1000;
    public static final String USERNAME = "USERNAME";
    public static final String PASSWORD = "PASSWORD";
    public static final String PIN = "PIN";
    public static final String NAME = "NAME";
    public static final String PHONE = "PHONE";
    public static final String ALT_PHONE = "ALT_PHONE";
    public static final String CONNECT_KEY_TOKEN = "access_token";
    public static final String CONNECT_KEY_EXPIRES = "expires_in";
    public final static int CONNECT_NO_ACTIVITY = ConnectConstants.CONNECTID_TASKID_OFFSET;
}

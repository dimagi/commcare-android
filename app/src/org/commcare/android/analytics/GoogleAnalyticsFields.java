package org.commcare.android.analytics;

/**
 * Contains the hierarchy of all labels used to send data to google analytics. The hierarchy of
 * information that a google analytics event contains is, from top to bottom:
 * Category --> Action --> Label --> Value. Label and Value are both optional.
 */
public final class GoogleAnalyticsFields {

    public static String CATEGORY_BASIC = "Basic Action";

    public static String ACTION_BUTTON = "Button Press";
    // -------
    public static String LABEL_START = "Start Button";
    public static String LABEL_SAVED = "Saved Forms Button";
    public static String LABEL_INCOMPLETE = "Incomplete Forms Button";
    public static String LABEL_SYNC = "Sync Button";
    public static String LABEL_LOGOUT = "Logout Button";

    public static String ACTION_SETTINGS = "Enter an Edit Settings Screen";
    // --------
    public static String LABEL_APP_SERVER = "CC Application Server";
    public static String LABEL_DATA_SERVER = "Data Server";
    public static String LABEL_SUBMISSION_SERVER = "Submission Server";
    public static String LABEL_KEY_SERVER = "Key Server";
    public static String LABEL_AUTO_UPDATE = "Auto Update Frequency";
    public static String LABEL_FUZZY_SEARCH = "Fuzzy Search Matches";
    public static String LABEL_PRINT_TEMPLATE = "Set Print Template";
    public static String LABEL_LOCALE = "Change Locale";


    public static String CATEGORY_FORM = "Form Navigation";
    // --------
    public static String ACTION_FORWARD = "Navigate Forward";
    public static String ACTION_BACKWARD = "Navigate Backward";
    public static String ACTION_QUIT_ATTEMPT = "Attempt Quit Form Entry";
    // --------
    public static String LABEL_ARROW = "Press Arrow";
    public static String LABEL_SWIPE = "Swipe";


}

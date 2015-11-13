package org.commcare.android.analytics;

/**
 * Contains the hierarchy of all labels used to send data to google analytics. The hierarchy of
 * information that a google analytics event contains is, from top to bottom:
 * Category --> Action --> Label --> Value. Label and Value are both optional.
 */
public final class GoogleAnalyticsFields {

    public static String CATEGORY_HOME_SCREEN = "Home Screen";
    public static String CATEGORY_FORM_ENTRY = "Form Entry";

    // Actions for both categories
    public static String ACTION_OPTIONS_MENU = "Enter Options Menu";
    public static String ACTION_SETTINGS_SCREEN = "Enter a Settings Screen";

    // Actions for CATEGORY_HOME_SCREEN only
    public static String ACTION_BUTTON = "Button Press";

    // Actions for CATEGORY_FORM_ENTRY only
    public static String ACTION_FORWARD = "Navigate Forward";
    public static String ACTION_BACKWARD = "Navigate Backward";
    public static String ACTION_QUIT_ATTEMPT = "Attempt Quit Form Entry";

    // Labels for ACTION_BUTTON
    public static String LABEL_START = "Start Button";
    public static String LABEL_SAVED = "Saved Forms Button";
    public static String LABEL_INCOMPLETE = "Incomplete Forms Button";
    public static String LABEL_SYNC = "Sync Button";
    public static String LABEL_LOGOUT = "Logout Button";

    // Labels for ACTION_SETTINGS_SCREEN (in CATEGORY_HOME_SCREEN)
    public static String LABEL_APP_SERVER = "CC Application Server";
    public static String LABEL_DATA_SERVER = "Data Server";
    public static String LABEL_SUBMISSION_SERVER = "Submission Server";
    public static String LABEL_KEY_SERVER = "Key Server";
    public static String LABEL_AUTO_UPDATE = "Auto Update Frequency";
    public static String LABEL_FUZZY_SEARCH = "Fuzzy Search Matches";
    public static String LABEL_PRINT_TEMPLATE = "Set Print Template";
    public static String LABEL_LOCALE = "Change Locale";

    // Labels for ACTION_SETTINGS_SCREEN (in CATEGORY_FORM_ENTRY)
    public static String LABEL_SAVE_FORM = "Save Form";
    public static String LABEL_FORM_HIERARCHY = "Form Hierarchy";
    public static String LABEL_CHANGE_LANGUAGE = "Change Language";
    public static String LABEL_CHANGE_SETTINGS = "Change Settings";

    // Labels for ACTION_FORWARD and ACTION_BACKWARD
    public static String LABEL_ARROW = "Press Arrow";
    public static String LABEL_SWIPE = "Swipe";

    // Screen Names
    public static String SCREEN_HOME = "CommCareHomeActivity";


}

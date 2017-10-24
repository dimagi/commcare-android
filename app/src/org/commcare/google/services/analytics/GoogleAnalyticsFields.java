package org.commcare.google.services.analytics;

/**
 * Contains the hierarchy of all labels used to send data to google analytics. The hierarchy of
 * information that a google analytics event contains is, from top to bottom:
 * Category --> Action --> Label --> Value. Label and Value are both optional.
 */
public final class GoogleAnalyticsFields {

    // Categories
    public static final String CATEGORY_HOME_SCREEN = "Home Screen";
    public static final String CATEGORY_CC_PREFS = "CommCare Preferences";
    public static final String CATEGORY_ADVANCED_ACTIONS = "Advanced Actions";
    public static final String CATEGORY_FORM_PREFS = "Form Entry Preferences";
    public static final String CATEGORY_DEV_PREFS = "Developer Preferences";
    public static final String CATEGORY_ARCHIVED_FORMS = "Archived Forms";
    public static final String CATEGORY_TIMED_EVENTS = "Timed Events";
    public static final String CATEGORY_APP_INSTALL = "New App Install";
    public static final String CATEGORY_MODULE_NAVIGATION = "Module Navigation";
    public static final String CATEGORY_LANGUAGE_STATS = "Language Statistics";

    // Actions for CATEGORY_TIMED_EVENTS only
    public static final String ACTION_TIME_IN_A_FORM = "Time Spent in A Form";
    public static final String ACTION_SESSION_LENGTH = "Session Length";

    // Actions for CATEGORY_MODULE_NAVIGATION
    public static final String ACTION_CONTINUE_FROM_DETAIL = "Continue Forward from Detail View";
    public static final String ACTION_EXIT_FROM_DETAIL = "Exit Detail View";

    // Actions for CATEGORY_ADVANCED_ACTIONS
    public static final String ACTION_REPORT_PROBLEM = "Report Problem";
    public static final String ACTION_VALIDATE_MEDIA = "Validate Media";
    public static final String ACTION_MANAGE_SD = "Manage SD";
    public static final String ACTION_WIFI_DIRECT = "Wifi Direct";
    public static final String ACTION_CONNECTION_TEST = "Connection Test";
    public static final String ACTION_CLEAR_USER_DATA = "Clear User Data";
    public static final String ACTION_CLEAR_SAVED_SESSION = "Clear Saved Session";
    public static final String ACTION_FORCE_LOG_SUBMISSION = "Force Log Submission";
    public static final String ACTION_RECOVERY_MODE = "Recovery Mode";
    public static final String ACTION_ENABLE_PRIVILEGES = "Enable Mobile Privileges";

    // Actions for CATEGORY_LANGUAGE_STATS
    public static final String ACTION_LANGUAGE_AT_FORM_ENTRY = "Language at Time of Form Entry";

    // Labels for ACTION_VIEW_PREF and ACTION_EDIT_PREF in CATEGORY_CC_PREFS
    public static final String LABEL_APP_SERVER = "CC Application Server";
    public static final String LABEL_DATA_SERVER = "Data Server";
    public static final String LABEL_SUBMISSION_SERVER = "Submission Server";
    public static final String LABEL_KEY_SERVER = "Key Server";
    public static final String LABEL_SUPPORT_EMAIL = "Support Email Address";
    public static final String LABEL_AUTO_UPDATE = "Auto Update Frequency";
    public static final String LABEL_FUZZY_SEARCH = "Fuzzy Search Matches";
    public static final String LABEL_PRINT_TEMPLATE = "Set Print Template";
    public static final String LABEL_DEVELOPER_OPTIONS = "Developer Options";
    public static final String LABEL_UPDATE_TARGET = "Change Update Target";

    // Values for ACTION_CONTINUE_FROM_DETAIL/ACTION_EXIT_FROM_DETAIL : LABEL_ARROW/LABEL_SWIPE
    public static final int VALUE_DOESNT_HAVE_TABS = 0;
    public static final int VALUE_HAS_TABS = 1;
}

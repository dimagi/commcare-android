package org.commcare.logging.analytics;

/**
 * Contains the hierarchy of all labels used to send data to google analytics. The hierarchy of
 * information that a google analytics event contains is, from top to bottom:
 * Category --> Action --> Label --> Value. Label and Value are both optional.
 */
public final class GoogleAnalyticsFields {

    // Categories
    public static final String CATEGORY_HOME_SCREEN = "Home Screen";
    public static final String CATEGORY_FORM_ENTRY = "Form Entry";
    public static final String CATEGORY_CC_PREFS = "CommCare Preferences";
    public static final String CATEGORY_FORM_PREFS = "Form Entry Preferences";
    public static final String CATEGORY_DEV_PREFS = "Developer Preferences";
    public static final String CATEGORY_SERVER_COMMUNICATION = "Server Communication";
    public static final String CATEGORY_ARCHIVED_FORMS = "Archived Forms";
    public static final String CATEGORY_TIMED_EVENTS = "Timed Events";
    public static final String CATEGORY_PRE_LOGIN_STATS = "Pre-Login Stats";
    public static final String CATEGORY_MODULE_NAVIGATION = "Module Navigation";
    public static final String CATEGORY_ADVANCED_USAGE = "Advanced CommCare Usage";

    // Actions for CATEGORY_HOME_SCREEN only
    public static final String ACTION_BUTTON = "Button Press";

    // Actions for multiple categories
    public static final String ACTION_OPTIONS_MENU = "Open Options Menu";
    public static final String ACTION_OPTIONS_MENU_ITEM = "Enter an Options Menu Item";
    public static final String ACTION_PREF_MENU = "Open Pref Menu";
    public static final String ACTION_VIEW_PREF = "Click on a Pref Item";
    public static final String ACTION_EDIT_PREF = "Edit a Preference";

    // Actions for CATEGORY_FORM_ENTRY only
    public static final String ACTION_FORWARD = "Navigate Forward";
    public static final String ACTION_BACKWARD = "Navigate Backward";
    public static final String ACTION_TRIGGER_QUIT_ATTEMPT = "Trigger Quit Attempt";
    public static final String ACTION_EXIT_FORM = "Form is Exited";

    // Actions for CATEGORY_SERVER_COMMUNICATION only
    public static final String ACTION_USER_SYNC_ATTEMPT = "User Sync Attempt";
    public static final String ACTION_AUTO_SYNC_ATTEMPT = "Auto Sync Attempt";

    // Actions for CATEGORY_SAVED_FORMS only
    public static final String ACTION_VIEW_FORMS_LIST = "View Archived Forms List";
    public static final String ACTION_OPEN_ARCHIVED_FORM = "Open an Archived Form";

    // Actions for CATEGORY_TIMED_EVENTS only
    public static final String ACTION_TIME_IN_A_FORM = "Time Spent in A Form";
    public static final String ACTION_SESSION_LENGTH = "Session Length";

    // Actions for CATEGORY_PRE_LOGIN_STATS
    public static final String ACTION_APP_INSTALL = "New App Install";

    // Actions for CATEGORY_MODULE_NAVIGATION
    public static final String ACTION_CONTINUE_FROM_DETAIL = "Continue Forward from Detail View";
    public static final String ACTION_EXIT_FROM_DETAIL = "Exit Detail View";

    // Actions for CATEGORY_ADVANCED_USAGE
    public static final String ACTION_SUPER_USER_ENABLED = "Enabled Super-User Mode";

    // Labels for ACTION_BUTTON
    public static final String LABEL_START_BUTTON = "Start Button";
    public static final String LABEL_SAVED_FORMS_BUTTON = "Saved Forms Button";
    public static final String LABEL_INCOMPLETE_FORMS_BUTTON = "Incomplete Forms Button";
    public static final String LABEL_SYNC_BUTTON = "Sync Button";
    public static final String LABEL_LOGOUT_BUTTON = "Logout Button";
    public static final String LABEL_REPORT_BUTTON = "Report An Issue Button";

    // Labels for ACTION_VIEW_PREF and ACTION_EDIT_PREF in CATEGORY_CC_PREFS
    public static final String LABEL_APP_SERVER = "CC Application Server";
    public static final String LABEL_DATA_SERVER = "Data Server";
    public static final String LABEL_SUBMISSION_SERVER = "Submission Server";
    public static final String LABEL_KEY_SERVER = "Key Server";
    public static final String LABEL_FORM_RECORD_SERVER = "Form Record Server";
    public static final String LABEL_AUTO_UPDATE = "Auto Update Frequency";
    public static final String LABEL_FUZZY_SEARCH = "Fuzzy Search Matches";
    public static final String LABEL_PRINT_TEMPLATE = "Set Print Template";
    public static final String LABEL_LOCALE = "Change Locale";

    // Labels for ACTION_VIEW_PREF and ACTION_EDIT_PREF in CATEGORY_FORM_PREFS
    public static final String LABEL_FONT_SIZE = "Font Size";
    public static final String LABEL_INLINE_HELP = "Rich Help Inline";

    // Labels for ACTION_VIEW_PREF and ACTION_EDIT_PREF in CATEGORY_DEV_PREFS
    public static final String LABEL_DEV_MODE = "Developer Mode Enabled";
    public static final String LABEL_ACTION_BAR = "Action Bar Enabled";
    public static final String LABEL_GRID_MENUS = "Grid Menus Enabled";
    public static final String LABEL_NAV_UI = "Navigation UI";
    public static final String LABEL_ENTITY_LIST_REFRESH = "Entity List Screen Auto-Refresh";
    public static final String LABEL_NEWEST_APP_VERSION = "Use Newest App Version From HQ";
    public static final String LABEL_AUTO_LOGIN = "Auto-login While Debugging";
    public static final String LABEL_SESSION_SAVING = "Enable Session Saving";
    public static final String LABEL_CSS = "CSS Enabled";
    public static final String LABEL_MARKDOWN = "Markdown Enabled";
    public static final String LABEL_IMAGE_ABOVE_TEXT = "Image Above Question Text Enabled";
    public static final String LABEL_TRIGGERS_ON_SAVE = "Fire triggers on form save";
    public static final String LABEL_ANIMATE_FORM_SUBMIT_BUTTON = "Animate form submit button";
    public static final String LABEL_REPORT_BUTTON_ENABLED = "Home Report Button enabled";
    public static final String LABEL_AUTO_PURGE = "Auto Purge on Save Enabled";
    public static final String LABEL_LOAD_FORM_PAYLOAD_AS = "Load form payload as";
    public static final String LABEL_DETAIL_TAB_SWIPE_ACTION = "Detail tab final swipe action enabled";

    // Labels for ACTION_OPTIONS_MENU_ITEM in CATEGORY_HOME_SCREEN
    public static final String LABEL_SETTINGS = "Settings";
    public static final String LABEL_UPDATE_CC = "Update CommCare";
    public static final String LABEL_REPORT_PROBLEM = "Report Problem";
    public static final String LABEL_VALIDATE_MM = "Validate Media";
    public static final String LABEL_MANAGE_SD = "Manage SD";
    public static final String LABEL_WIFI_DIRECT = "Wifi Direct";
    public static final String LABEL_CONNECTION_TEST = "Connection Test";
    public static final String LABEL_SAVED_FORMS = "Saved Forms";
    public static final String LABEL_ABOUT_CC = "About CommCare";

    // Labels for ACTION_OPTIONS_MENU_ITEM in CATEGORY_FORM_ENTRY
    public static final String LABEL_SAVE_FORM = "Save Form";
    public static final String LABEL_FORM_HIERARCHY = "Form Hierarchy";
    public static final String LABEL_CHANGE_LANGUAGE = "Change Language";
    public static final String LABEL_CHANGE_SETTINGS = "Change Settings";

    // Labels for ACTION_OPTIONS_MENU_ITEM in CATEGORY_CC_PREFS
    public static final String LABEL_CLEAR_USER_DATA = "Clear User Data";
    public static final String LABEL_CLEAR_SAVED_SESSION = "Clear Saved Session";
    public static final String LABEL_FORCE_LOG_SUBMISSION = "Force Log Submission";
    public static final String LABEL_RECOVERY_MODE = "Recovery Mode";
    public static final String LABEL_DEVELOPER_OPTIONS = "Developer Options";

    // Labels for ACTION_FORWARD and ACTION_BACKWARD (in CATEGORY_FORM_ENTRY)
    // and also ACTION_CONTINUE_FROM_DETAIL and ACTION_EXIT_FROM_DETAIL (in CATEGORY_MODULE_NAVIGATION)
    public static final String LABEL_ARROW = "Press Arrow";
    public static final String LABEL_SWIPE = "Swipe";

    // Labels for ACTION_TRIGGER_QUIT_ATTEMPT (in CATEGORY_FORM_ENTRY)
    public static final String LABEL_DEVICE_BUTTON = "Device Back Button";
    public static final String LABEL_NAV_BAR_ARROW = "Nav Bar Back Arrow";
    public static final String LABEL_PROGRESS_BAR_ARROW = "Progress Bar Back Arrow";

    // Labels for ACTION_EXIT_FORM (in CATEGORY_FORM_ENTRY)
    public static final String LABEL_NO_DIALOG = "No Dialog Shown";
    public static final String LABEL_SAVE_AND_EXIT = "Save and Exit";
    public static final String LABEL_EXIT_NO_SAVE = "Exit without Saving";
    public static final String LABEL_BACK_TO_FORM = "Back to Form";

    // Labels for ACTION_USER_SYNC_ATTEMPT and ACTION_AUTO_SYNC_ATTEMPT
    public static String LABEL_SYNC_SUCCESS;
    public static String LABEL_SYNC_FAILURE;

    // Labels for ACTION_VIEW_FORMS_LIST and ACTION_OPEN_ARCHIVED_FORM
    public static final String LABEL_INCOMPLETE = "Incomplete";
    public static final String LABEL_COMPLETE = "Complete (Saved)";


    // Values for LABEL_SYNC_SUCCESS
    public static final int VALUE_JUST_PULL_DATA = 0;
    public static final int VALUE_WITH_SEND_FORMS = 1;

    // Values for LABEL_SYNC_FAILURE
    public static final int VALUE_NO_CONNECTION = 0;
    public static final int VALUE_AUTH_FAILED = 1;
    public static final int VALUE_BAD_DATA = 2;
    public static final int VALUE_SERVER_ERROR = 3;
    public static final int VALUE_UNREACHABLE_HOST = 4;
    public static final int VALUE_CONNECTION_TIMEOUT = 5;
    public static final int VALUE_UNKNOWN_FAILURE = 6;
    public static final int VALUE_STORAGE_FULL = 7;
    public static final int VALUE_BAD_DATA_REQUIRES_INTERVENTION = 8;

    // Values for LABEL_AUTO_UPDATE
    public static final int VALUE_NEVER = 0;
    public static final int VALUE_DAILY = 1;
    public static final int VALUE_WEEKLY = 2;

    // Values for all labels under ACTION_EDIT_PREF in CATEGORY_DEV_PREFS, and for LABEL_FUZZY_SEARCH
    public static final int VALUE_DISABLED = 0;
    public static final int VALUE_ENABLED = 1;

    // Values for LABEL_ARROW and LABEL_SWIPE
    public static final int VALUE_FORM_NOT_DONE = 0;
    public static final int VALUE_FORM_DONE = 1;

    // Values for ACTION_CONTINUE_FROM_DETAIL/ACTION_EXIT_FROM_DETAIL : LABEL_ARROW/LABEL_SWIPE
    public static final int VALUE_DOESNT_HAVE_TABS = 0;
    public static final int VALUE_HAS_TABS = 1;
}

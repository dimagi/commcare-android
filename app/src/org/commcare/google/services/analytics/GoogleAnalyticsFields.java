package org.commcare.google.services.analytics;

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
    public static final String CATEGORY_SERVER_PREFS = "CommCare Server Preferences";
    public static final String CATEGORY_ADVANCED_ACTIONS = "Advanced Actions";
    public static final String CATEGORY_FORM_PREFS = "Form Entry Preferences";
    public static final String CATEGORY_DEV_PREFS = "Developer Preferences";
    public static final String CATEGORY_ARCHIVED_FORMS = "Archived Forms";
    public static final String CATEGORY_TIMED_EVENTS = "Timed Events";
    public static final String CATEGORY_APP_INSTALL = "New App Install";
    public static final String CATEGORY_MODULE_NAVIGATION = "Module Navigation";
    public static final String CATEGORY_APP_MANAGER = "App Manager";
    public static final String CATEGORY_LANGUAGE_STATS = "Language Statistics";

    // Actions for CATEGORY_HOME_SCREEN only
    public static final String ACTION_BUTTON = "Button Press";

    // Actions for multiple categories

    public static final String ACTION_PREF_MENU = "Open Pref Menu";
    public static final String ACTION_VIEW_PREF = "Click on a Pref Item";
    public static final String ACTION_EDIT_PREF = "Edit a Preference";

    // Actions for CATEGORY_FORM_ENTRY only
    public static final String ACTION_FORWARD = "Navigate Forward";
    public static final String ACTION_BACKWARD = "Navigate Backward";
    public static final String ACTION_TRIGGER_QUIT_ATTEMPT = "Trigger Quit Attempt";
    public static final String ACTION_EXIT_FORM = "Form is Exited";

    // Actions for CATEGORY_SAVED_FORMS only
    public static final String ACTION_VIEW_FORMS_LIST = "View Archived Forms List";
    public static final String ACTION_OPEN_ARCHIVED_FORM = "Open an Archived Form";

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

    // Actions for CATEGORY_APP_MANAGER
    public static final String ACTION_OPEN_APP_MANAGER = "Open App Manager";
    public static final String ACTION_ARCHIVE_APP = "Archive an App";
    public static final String ACTION_UNINSTALL_APP = "Uninstall an App";
    public static final String ACTION_INSTALL_FROM_MANAGER = "Install from Manager";

    // Actions for CATEGORY_LANGUAGE_STATS
    public static final String ACTION_LANGUAGE_AT_FORM_ENTRY = "Language at Time of Form Entry";

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
    public static final String LABEL_SUPPORT_EMAIL = "Support Email Address";
    public static final String LABEL_AUTO_UPDATE = "Auto Update Frequency";
    public static final String LABEL_FUZZY_SEARCH = "Fuzzy Search Matches";
    public static final String LABEL_PRINT_TEMPLATE = "Set Print Template";
    public static final String LABEL_DEVELOPER_OPTIONS = "Developer Options";
    public static final String LABEL_UPDATE_TARGET = "Change Update Target";

    // Labels for ACTION_VIEW_PREF and ACTION_EDIT_PREF in CATEGORY_FORM_PREFS
    public static final String LABEL_FONT_SIZE = "Font Size";
    public static final String LABEL_INLINE_HELP = "Rich Help Inline";

    // Labels for ACTION_VIEW_PREF and ACTION_EDIT_PREF in CATEGORY_DEV_PREFS
    public static final String LABEL_DEV_MODE = "Developer Mode Enabled";
    public static final String LABEL_ACTION_BAR = "Action Bar Enabled";
    public static final String LABEL_GRID_MENUS = "Grid Menus Enabled";
    public static final String LABEL_NAV_UI = "Navigation UI";
    public static final String LABEL_ENTITY_LIST_REFRESH = "Entity List Screen Auto-Refresh";
    public static final String LABEL_AUTO_LOGIN = "Auto-login While Debugging";
    public static final String LABEL_SESSION_SAVING = "Enable Session Saving";
    public static final String LABEL_EDIT_SAVED_SESSION = "Edit Saved Session";
    public static final String LABEL_CSS = "CSS Enabled";
    public static final String LABEL_MARKDOWN = "Markdown Enabled";
    public static final String LABEL_IMAGE_ABOVE_TEXT = "Image Above Question Text Enabled";
    public static final String LABEL_TRIGGERS_ON_SAVE = "Fire triggers on form save";
    public static final String LABEL_REPORT_BUTTON_ENABLED = "Home Report Button enabled";
    public static final String LABEL_AUTO_PURGE = "Auto Purge on Save Enabled";
    public static final String LABEL_LOAD_FORM_PAYLOAD_AS = "Load form payload as";
    public static final String LABEL_DETAIL_TAB_SWIPE_ACTION = "Detail tab final swipe action enabled";
    public static final String LABEL_OFFLINE_UPDATE = "Offline Updates enabled";
    public static final String LABEL_CUSTOM_RESTORE = "Custom Restore Requested";
    public static final String LABEL_LOCAL_FORM_PAYLOAD_FILE_PATH = "Form Payload File";
    public static final String LABEL_REMOTE_FORM_PAYLOAD_URL = "Form Payload Server";
    public static final String LABEL_ENFORCE_SECURE_ENDPOINT = "Enforce Secure Endpoint";

    // Labels for ACTION_TRIGGER_QUIT_ATTEMPT (in CATEGORY_FORM_ENTRY)
    public static final String LABEL_DEVICE_BUTTON = "Device Back Button";
    public static final String LABEL_NAV_BAR_ARROW = "Nav Bar Back Arrow";
    public static final String LABEL_PROGRESS_BAR_ARROW = "Progress Bar Back Arrow";

    // Labels for ACTION_EXIT_FORM (in CATEGORY_FORM_ENTRY)
    public static final String LABEL_NO_DIALOG = "No Dialog Shown";
    public static final String LABEL_SAVE_AND_EXIT = "Save and Exit";
    public static final String LABEL_EXIT_NO_SAVE = "Exit without Saving";
    public static final String LABEL_BACK_TO_FORM = "Back to Form";

    // Labels for ACTION_VIEW_FORMS_LIST and ACTION_OPEN_ARCHIVED_FORM
    public static final String LABEL_INCOMPLETE = "Incomplete";
    public static final String LABEL_COMPLETE = "Complete (Saved)";

    // Values for LABEL_AUTO_UPDATE
    public static final int VALUE_NEVER = 0;
    public static final int VALUE_DAILY = 1;
    public static final int VALUE_WEEKLY = 2;

    // Values for LABEL_UPDATE_TARGET
    public static final int VALUE_STARRED = 0;
    public static final int VALUE_BUILD = 1;
    public static final int VALUE_SAVED = 2;

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

package org.commcare.android.analytics;

/**
 * Contains the hierarchy of all labels used to send data to google analytics. The hierarchy of
 * information that a google analytics event contains is, from top to bottom:
 * Category --> Action --> Label --> Value. Label and Value are both optional.
 */
public final class GoogleAnalyticsFields {

    // Categories
    public static String CATEGORY_HOME_SCREEN = "Home Screen";
    public static String CATEGORY_FORM_ENTRY = "Form Entry";
    public static String CATEGORY_CC_PREFS = "CommCare Preferences";
    public static String CATEGORY_FORM_PREFS = "Form Entry Preferences";

    // Actions for multiple categories
    public static String ACTION_OPTIONS_MENU = "Open Options Menu";
    public static String ACTION_OPTIONS_MENU_ITEM = "Enter an Options Menu Item";

    public static String ACTION_PREF_MENU = "Open Pref Menu";
    public static String ACTION_VIEW_PREF = "Click on a Pref Item";
    public static String ACTION_EDIT_PREF = "Edit a Preference";
    public static String ACTION_BUTTON = "Button Press";

    // Actions for CATEGORY_FORM_ENTRY only
    public static String ACTION_FORWARD = "Navigate Forward";
    public static String ACTION_BACKWARD = "Navigate Backward";
    public static String ACTION_QUIT_ATTEMPT = "Attempt Quit Form Entry";

    // Labels for ACTION_BUTTON
    public static String LABEL_START = "Start Button";
    public static String LABEL_SAVED_FORMS = "Saved Forms Button";
    public static String LABEL_INCOMPLETE_FORMS = "Incomplete Forms Button";
    public static String LABEL_SYNC = "Sync Button";
    public static String LABEL_LOGOUT = "Logout Button";

    // Labels for ACTION_VIEW_PREF and ACTION_EDIT_PREF in CATEGORY_CC_PREFS
    public static String LABEL_APP_SERVER = "CC Application Server";
    public static String LABEL_DATA_SERVER = "Data Server";
    public static String LABEL_SUBMISSION_SERVER = "Submission Server";
    public static String LABEL_KEY_SERVER = "Key Server";
    public static String LABEL_FORM_RECORD_SERVER = "Form Record Server";
    public static String LABEL_AUTO_UPDATE = "Auto Update Frequency";
    public static String LABEL_FUZZY_SEARCH = "Fuzzy Search Matches";
    public static String LABEL_PRINT_TEMPLATE = "Set Print Template";
    public static String LABEL_LOCALE = "Change Locale";

    // Labels for ACTION_VIEW_PREF and ACTION_EDIT_PREF in CATEGORY_FORM_PREFS
    public static String LABEL_FONT_SIZE = "Font Size";
    public static String LABEL_INLINE_HELP = "Rich Help Inline";

    // Labels for ACTION_MENU_ITEM in CATEGORY_FORM_ENTRY
    public static String LABEL_SAVE_FORM = "Save Form";
    public static String LABEL_FORM_HIERARCHY = "Form Hierarchy";
    public static String LABEL_CHANGE_LANGUAGE = "Change Language";
    public static String LABEL_CHANGE_SETTINGS = "Change Settings";

    // Labels for ACTION_FORWARD and ACTION_BACKWARD (in CATEGORY_FORM_ENTRY)
    public static String LABEL_ARROW = "Press Arrow";
    public static String LABEL_SWIPE = "Swipe";

    // Labels for ACTION_QUIT_ATTEMPT (in CATEGORY_FORM_ENTRY)
    public static String LABEL_NO_DIALOG = "No Dialog Shown";
    public static String LABEL_SAVE_AND_EXIT = "Save and Exit";
    public static String LABEL_EXIT_NO_SAVE = "Exit without Saving";
    public static String LABEL_BACK_TO_FORM = "Back to Form";

    // Values for LABEL_AUTO_UPDATE
    public static int VALUE_NEVER = 0;
    public static int VALUE_DAILY = 1;
    public static int VALUE_WEEKLY = 2;

    // Values for LABEL_FUZZY_SEARCH
    public static int VALUE_DISABLED = 0;
    public static int VALUE_ENABLED = 1;

    // Values for LABEL_ARROW and LABEL_SWIPE (in ACTION_FORWARD)
    public static int VALUE_NOT_ON_LAST_SCREEN = 0;
    public static int VALUE_ON_LAST_SCREEN = 1;

    // Screen Names
    public static String SCREEN_HOME = "CommCareHomeActivity";

}

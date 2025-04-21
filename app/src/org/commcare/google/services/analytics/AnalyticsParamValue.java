package org.commcare.google.services.analytics;

/**
 * Created by amstone326 on 10/13/17.
 */

public class AnalyticsParamValue {

    // Param values for graphing actions
    static final String GRAPH_ATTACH = "start_viewing_graph";
    static final String GRAPH_DETACH = "stop_viewing_graph";
    static final String GRAPH_FULLSCREEN_OPEN = "graph_fullscreen_open";
    static final String GRAPH_FULLSCREEN_CLOSE = "graph_fullscreen_close";

    // Param values for form entry actions and entity detail navigation
    public static final String DIRECTION_FORWARD = "forward";
    public static final String DIRECTION_BACKWARD = "backward";
    public static final String NAV_BUTTON_PRESS = "nav_button_press";
    public static final String BACK_BUTTON_PRESS = "back_button_press";
    public static final String SWIPE = "swipe";

    // Param values for options menu items
    public static final String ITEM_SETTINGS = "settings";
    public static final String ITEM_UPDATE_CC = "update_commcare";
    public static final String ITEM_ABOUT_CC = "about_commcare";
    public static final String ITEM_SAVED_FORMS = "saved_forms";
    public static final String ITEM_ADVANCED_ACTIONS = "advanced_actions";
    public static final String ITEM_CHANGE_LANGUAGE = "change_language";
    public static final String ITEM_SAVE_FORM = "save_form";
    public static final String ITEM_FORM_HIERARCHY = "form_hierarchy";
    public static final String ITEM_CHANGE_FORM_SETTINGS = "change_settings";
    public static final String ITEM_UPDATE_CC_PLATFORM = "update_commcare_platform";

    // Param values for sync attempts
    public static final String SYNC_TRIGGER_USER = "user_triggered_sync";
    public static final String SYNC_TRIGGER_AUTO = "auto_sync";


    public static final String SYNC_MODE_JUST_PULL_DATA = "sync_just_pull";
    public static final String SYNC_MODE_SEND_FORMS = "sync_pull_and_send";

    public static final String SYNC_FAIL_NO_CONNECTION = "no_connection";
    public static final String SYNC_FAIL_AUTH = "auth_failure";
    public static final String SYNC_FAIL_EMPTY_URL = "empty_url";
    public static final String SYNC_FAIL_RETRY_NEEDED = "retry_needed";
    public static final String SYNC_FAIL_BAD_DATA = "bad_data";
    public static final String SYNC_FAIL_SERVER_ERROR = "server_error";
    public static final String SYNC_FAIL_RATE_LIMITED_SERVER_ERROR = "rate_limited_server_error";
    public static final String SYNC_FAIL_UNREACHABLE_HOST = "unreachable_host";
    public static final String SYNC_FAIL_CONNECTION_TIMEOUT = "connection_timeout";
    public static final String SYNC_FAIL_UNKNOWN = "unknown_failure";
    public static final String SYNC_FAIL_CANCELLED = "cancelled";
    public static final String SYNC_FAIL_ENCRYPTION = "encryption_failure";
    public static final String SYNC_FAIL_SESSION_EXPIRE = "session_expire";
    public static final String SYNC_FAIL_RECOVERY = "recovery_failure";
    public static final String SYNC_FAIL_STORAGE_FULL = "storage_full";
    public static final String SYNC_FAIL_ACTIONABLE = "actionable_failure";
    public static final String SYNC_FAIL_AUTH_OVER_HTTP = "auth_over_http";
    public static final String SYNC_FAIL_CAPTIVE_PORTAL = "captive_portal";
    public static final String SYNC_FAIL_BAD_CERTIFICATE = "bad_certificate";
    public static final String SYNC_SUCCESS = "success";

    // Param values for feature usage
    public static final String FEATURE_SET_PIN = "set_pin";
    public static final String FEATURE_PRINT = "print_from_form_or_detail";
    public static final String FEATURE_CASE_AUTOSELECT = "case_autoselect";
    static final String FEATURE_PRACTICE_MODE = "practice_mode";
    static final String PRACTICE_MODE_CUSTOM = "custom_practice_user";
    static final String PRACTICE_MODE_DEFAULT = "default_practice_user";

    // Param values for app install
    public static final String BARCODE_INSTALL = "barcode_install";
    public static final String OFFLINE_INSTALL = "offline_install";
    public static final String URL_INSTALL = "url_install";
    public static final String SMS_INSTALL = "sms_install";
    public static final String FROM_LIST_INSTALL = "from_list_install";
    public static final String MANAGED_CONFIG_INSTALL = "managed_configuration_install";

    // Param values for app manager actions
    public static final String OPEN_APP_MANAGER = "open_app_manager";
    public static final String ARCHIVE_APP = "archive_app";
    public static final String UNINSTALL_APP = "uninstall_app";
    public static final String INSTALL_FROM_MANAGER = "install_app";

    // Param values for home buttons
    public static final String SAVED_FORMS_BUTTON = "saved_forms";
    public static final String INCOMPLETE_FORMS_BUTTON = "incomplete_forms";
    public static final String SYNC_BUTTON = "sync";
    public static final String START_BUTTON = "start";
    public static final String LOGOUT_BUTTON = "logout";
    public static final String SYNC_SUBTEXT = "sync_subtext";
    public static final String REPORT_BUTTON = "report_an_issue";

    // Param values for form types
    public static final String INCOMPLETE = "incomplete";
    public static final String SAVED = "saved";

    // Param values for advanced actions
    public static final String REPORT_PROBLEM = "report_problem";
    public static final String VALIDATE_MEDIA = "validate_media";
    public static final String MANAGE_SD = "manage_sd";
    public static final String WIFI_DIRECT = "wifi_direct";
    public static final String CONNECTION_TEST = "connection_test";
    public static final String CLEAR_USER_DATA = "clear_user_data";
    public static final String CLEAR_SAVED_SESSION = "clear_saved_session";
    public static final String DISABLE_PRE_UPDATE_SYNC = "disable_pre_update_sync";
    public static final String FORCE_LOG_SUBMISSION = "force_log_submission";
    public static final String RECOVERY_MODE = "recovery_mode";
    public static final String ENABLE_PRIVILEGES = "enable_mobile_privileges";
    public static final String ENABLE_RATE_LIMIT_POPUP = "enable_rate_limit_popup";
    public static final String ENABLE_MANUAL_FORM_QUARANTINE = "enable_manual_form_quarantine";
    public static final String APP_MANAGER_DEVELOPER_OPTIONS = "app_manager_developer_options";

    // Param values for entity detail ui state
    static final String DETAIL_WITH_TABS = "detail_with_tabs";
    static final String DETAIL_NO_TABS = "detail_no_tabs";

    // Param values for timed sessions
    public static final String USER_SESSION = "user_session";
    public static final String FORM_ENTRY_SESSION = "form_entry_session";


    // Param values for video usage event
    public static final String VIDEO_USAGE_IMMEDIATE = "immediate";
    public static final String VIDEO_USAGE_PARTIAL = "partial";
    public static final String VIDEO_USAGE_MOST = "most";
    public static final String VIDEO_USAGE_FULL = "full";
    public static final String VIDEO_USAGE_OTHER = "other";
    public static final String VIDEO_USAGE_LENGTH_UNKNOWN = "length_unknown";


    // Param values for common commcare event
    public static final String STAGE_UPDATE_FAILURE = "stage_update_failure";
    public static final String UPDATE_RESET = "update_reset";
    public static final String CORRUPT_APP_STATE = "corrupt_app_state";

    public static final String UPDATE_RESET_REASON_CORRUPT = "update_corrupt";
    public static final String UPDATE_RESET_REASON_TIMEOUT = "update_timeout";
    public static final String UPDATE_RESET_REASON_OVERSHOOT_TRIALS = "update_overshoot_trials";
    public static final String UPDATE_RESET_REASON_NEWER_VERSION_AVAILABLE = "update_newer_version_available";

    public static final String IN_APP_UPDATE_SUCCESS = "success";

    // Param values for Form Submission Event

    public static final String USER_TRIGGERED = "user_triggered";
    public static final String SYSTEM_TRIGGERED = "system_triggered";


}

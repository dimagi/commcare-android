package org.commcare.google.services.analytics;

/**
 * Created by amstone326 on 10/13/17.
 */

public class CCAnalyticsParamValue {

    // Param values for audio widget interaction
    static final String CHOOSE_AUDIO_FILE = "choose_audio_file";
    static final String START_RECORDING = "start_recording_audio";
    static final String STOP_RECORDING = "stop_recording";
    static final String SAVE_RECORDING = "save_recording";
    static final String PLAY_AUDIO = "play_audio";
    static final String PAUSE_AUDIO = "pause_audio";
    static final String RECORD_AGAIN = "record_again";

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
    public static final String ITEM_settings = "settings";
    public static final String ITEM_updateCommcare = "update_commcare";
    public static final String ITEM_aboutCommcare = "about_commcare";
    public static final String ITEM_savedForms = "saved_forms";
    public static final String ITEM_advancedActions = "advanced_actions";
    public static final String ITEM_changeLanguage = "change_language";
    public static final String ITEM_saveForm = "save_form";
    public static final String ITEM_formHierarchy = "form_hierarchy";
    public static final String ITEM_changeFormSettings = "change_settings";

    // Param values for sync attempts
    public static final String SYNC_TRIGGER_USER = "user_triggered_sync";
    public static final String SYNC_TRIGGER_AUTO = "auto_sync";

    static final String SYNC_SUCCESS = "sync_success";
    static final String SYNC_FAILURE = "sync_failure";

    public static final String SYNC_MODE_justPullData = "sync_just_pull";
    public static final String SYNC_MODE_sendForms = "sync_pull_and_send";

    public static final String SYNC_FAIL_noConnection = "no_connection";
    public static final String SYNC_FAIL_auth = "auth_failure";
    public static final String SYNC_FAIL_badData = "bad_data";
    public static final String SYNC_FAIL_serverError = "server_error";
    public static final String SYNC_FAIL_unreachableHost = "unreachable_host";
    public static final String SYNC_FAIL_connectionTimeout = "connection_timeout";
    public static final String SYNC_FAIL_unknown = "unknown_failure";
    public static final String SYNC_FAIL_storageFull = "storage_full";
    public static final String SYNC_FAIL_actionable = "actionable_failure";
    public static final String SYNC_FAIL_authOverHttp = "auth_over_http";

    // Param values for feature usage
    public static final String FEATURE_setPin = "set_pin";
    public static final String FEATURE_print = "print_from_form_or_detail";
    public static final String FEATURE_resizeImageCapture = "resize_image_capture";
    public static final String FEATURE_caseAutoselect = "case_autoselect";
    public static final String FEATURE_smartImageInflation = "smart_image_inflation";
    public static final String FEATURE_installFromList = "install_from_app_list";
    static final String FEATURE_practiceMode = "practice_mode";
    static final String PRACTICE_MODE_CUSTOM = "custom_practice_user";
    static final String PRACTICE_MODE_DEFAULT = "default_practice_user";

    // Param values for app install
    public static final String BARCODE_INSTALL = "barcode_install";
    public static final String OFFLINE_INSTALL = "offline_install";
    public static final String URL_INSTALL = "url_install";
    public static final String SMS_INSTALL = "sms_install";

    // Param values for app manager actions
    public static final String OPEN_APP_MANAGER = "open_app_manager";
    public static final String ARCHIVE_APP = "archive_app";
    public static final String UNINSTALL_APP = "uninstall_app";
    public static final String INSTALL_FROM_MANAGER = "install_app";

    // Param values for home buttons
    public static final String START_BUTTON = "start";
    public static final String SAVED_FORMS_BUTTON = "saved_forms";
    public static final String INCOMPLETE_FORMS_BUTTON = "incomplete_forms";
    public static final String SYNC_BUTTON = "sync";
    public static final String LOGOUT_BUTTON = "logout";
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
    public static final String FORCE_LOG_SUBMISSION = "force_log_submission";
    public static final String RECOVERY_MODE = "recovery_mode";
    public static final String ENABLE_PRIVILEGES = "enable_mobile_privileges";

    // Param values for entity detail ui state
    static final String DETAIL_WITH_TABS = "detail_with_tabs";
    static final String DETAIL_NO_TABS = "detail_no_tabs";

    // Param values for timed sessions
    public static final String USER_SESSION = "user_session";
    public static final String FORM_ENTRY_SESSION = "form_entry_session";

}

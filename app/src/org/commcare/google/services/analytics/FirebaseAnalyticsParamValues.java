package org.commcare.google.services.analytics;

/**
 * Created by amstone326 on 10/13/17.
 */

public class FirebaseAnalyticsParamValues {

    // Param values for audio widget interaction
    public static final String CHOOSE_AUDIO_FILE = "choose_audio_file";
    public static final String START_RECORDING = "start_recording_audio";
    public static final String STOP_RECORDING = "stop_recording";
    public static final String SAVE_RECORDING = "save_recording";
    public static final String PLAY_AUDIO = "play_audio";
    public static final String PAUSE_AUDIO = "pause_audio";
    public static final String RECORD_AGAIN = "record_again";

    // Param values for graphing actions
    public static final String GRAPH_ATTACH = "start_viewing_graph";
    public static final String GRAPH_DETACH = "stop_viewing_graph";
    public static final String GRAPH_FULLSCREEN_OPEN = "graph_fullscreen_open";
    public static final String GRAPH_FULLSCREEN_CLOSE = "graph_fullscreen_close";

    // Param values for form entry actions
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

    public static final String SYNC_SUCCESS = "sync_success";
    public static final String SYNC_FAILURE = "sync_failure";

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

}

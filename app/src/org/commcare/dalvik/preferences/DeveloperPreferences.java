package org.commcare.dalvik.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.commcare.android.analytics.GoogleAnalyticsFields;
import org.commcare.android.analytics.GoogleAnalyticsUtils;
import org.commcare.android.framework.SessionAwarePreferenceActivity;
import org.commcare.android.session.DevSessionRestorer;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;

public class DeveloperPreferences extends SessionAwarePreferenceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener {
    public final static String SUPERUSER_ENABLED = "cc-superuser-enabled";
    public final static String GRID_MENUS_ENABLED = "cc-grid-menus";
    public final static String NAV_UI_ENABLED = "cc-nav-ui-enabled";
    public final static String CSS_ENABLED = "cc-css-enabled";
    public final static String MARKDOWN_ENABLED = "cc-markdown-enabled";
    public final static String ACTION_BAR_ENABLED = "cc-action-nav-enabled";
    public final static String LIST_REFRESH_ENABLED = "cc-list-refresh";
    public final static String HOME_REPORT_ENABLED = "cc-home-report";
    /**
     * Stores last used password and performs auto-login when that password is
     * present
     */
    public final static String ENABLE_AUTO_LOGIN = "cc-enable-auto-login";
    public final static String ENABLE_SAVE_SESSION = "cc-enable-session-saving";
    /**
     * Does the user want to download the latest app version deployed (built),
     * not just the latest app version released (starred)?
     */
    public final static String NEWEST_APP_VERSION_ENABLED = "cc-newest-version-from-hq";
    /**
     * The current default for constraint checking during form saving (as of
     * CommCare 2.24) is to re-answer all the questions, causing a lot of
     * triggers to fire. We probably don't need to do this, but it is hard to
     * know, so allow the adventurous to use form saving that doesn't re-fire
     * triggers.
     */
    public final static String FIRE_TRIGGERS_ON_SAVE = "cc-fire-triggers-on-save";
    public final static String ALTERNATE_QUESTION_LAYOUT_ENABLED = "cc-alternate-question-text-format";
    public final static String ANALYTICS_ENABLED = "cc-analytics-enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_DEV_OPTIONS);

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));

        addPreferencesFromResource(R.xml.preferences_developer);
        setTitle("Developer Preferences");

        createPreferenceOnClickListeners(prefMgr);
    }

    private void createPreferenceOnClickListeners(PreferenceManager prefManager) {
        String[] prefKeys = {
                SUPERUSER_ENABLED,
                ACTION_BAR_ENABLED,
                GRID_MENUS_ENABLED,
                NAV_UI_ENABLED,
                LIST_REFRESH_ENABLED,
                NEWEST_APP_VERSION_ENABLED,
                ENABLE_AUTO_LOGIN,
                ENABLE_SAVE_SESSION,
                CSS_ENABLED,
                MARKDOWN_ENABLED,
                ALTERNATE_QUESTION_LAYOUT_ENABLED,
                FIRE_TRIGGERS_ON_SAVE,
                HOME_REPORT_ENABLED};
        String[] analyticsLabels = {
                GoogleAnalyticsFields.LABEL_DEV_MODE,
                GoogleAnalyticsFields.LABEL_ACTION_BAR,
                GoogleAnalyticsFields.LABEL_GRID_MENUS,
                GoogleAnalyticsFields.LABEL_NAV_UI,
                GoogleAnalyticsFields.LABEL_ENTITY_LIST_REFRESH,
                GoogleAnalyticsFields.LABEL_NEWEST_APP_VERSION,
                GoogleAnalyticsFields.LABEL_AUTO_LOGIN,
                GoogleAnalyticsFields.LABEL_SESSION_SAVING,
                GoogleAnalyticsFields.LABEL_CSS,
                GoogleAnalyticsFields.LABEL_MARKDOWN,
                GoogleAnalyticsFields.LABEL_IMAGE_ABOVE_TEXT,
                GoogleAnalyticsFields.LABEL_TRIGGERS_ON_SAVE,
                GoogleAnalyticsFields.LABEL_REPORT_BUTTON};

        for (int i = 0; i < prefKeys.length; i++) {
            GoogleAnalyticsUtils.createPreferenceOnClickListener(prefManager,
                    GoogleAnalyticsFields.CATEGORY_DEV_OPTIONS, prefKeys[i], analyticsLabels[i]);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        int editPrefValue = getEditPrefValue(key);
        String editPrefLabel = "";
        switch(key) {
            case SUPERUSER_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_DEV_MODE;
                break;
            case ACTION_BAR_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_ACTION_BAR;
                break;
            case GRID_MENUS_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_GRID_MENUS;
                break;
            case NAV_UI_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_NAV_UI;
                break;
            case LIST_REFRESH_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_ENTITY_LIST_REFRESH;
                break;
            case NEWEST_APP_VERSION_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_NEWEST_APP_VERSION;
                break;
            case ENABLE_AUTO_LOGIN:
                editPrefLabel = GoogleAnalyticsFields.LABEL_AUTO_LOGIN;
                if (!isAutoLoginEnabled()) {
                    DevSessionRestorer.clearPassword(sharedPreferences);
                }
                break;
            case ENABLE_SAVE_SESSION:
                editPrefLabel = GoogleAnalyticsFields.LABEL_SESSION_SAVING;
                if (!isSessionSavingEnabled()) {
                    DevSessionRestorer.clearSession();
                }
                break;
            case CSS_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_CSS;
                break;
            case MARKDOWN_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_MARKDOWN;
                break;
            case ALTERNATE_QUESTION_LAYOUT_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_IMAGE_ABOVE_TEXT;
                break;
            case FIRE_TRIGGERS_ON_SAVE:
                editPrefLabel = GoogleAnalyticsFields.LABEL_TRIGGERS_ON_SAVE;
                break;
            case HOME_REPORT_ENABLED:
                editPrefLabel = GoogleAnalyticsFields.LABEL_REPORT_BUTTON;
                break;
        }

        if (!"".equals(editPrefLabel)) {
            GoogleAnalyticsUtils.reportEditPref(GoogleAnalyticsFields.CATEGORY_DEV_OPTIONS,
                    editPrefLabel, editPrefValue);
        }
    }

    private static int getEditPrefValue(String key) {
        if (CommCareApplication._().getCurrentApp().getAppPreferences().
                getString(key, CommCarePreferences.NO).equals(CommCarePreferences.YES)) {
            return GoogleAnalyticsFields.VALUE_ENABLED;
        } else {
            return GoogleAnalyticsFields.VALUE_DISABLED;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Try to lookup key in app preferences and test equality of the result to
     * matchingValue.  If either the app or preference key don't exist, just
     * compare defaultValue to matchingValue
     *
     * @param key is a potential entry in the app preferences
     * @param defaultValue use this value if key not found
     * @param matchingValue compare this to key lookup or defaultValue
     * @return boolean
     */
    private static boolean doesPropertyMatch(String key, String defaultValue, String matchingValue) {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app == null) {
            return defaultValue.equals(matchingValue);
        }
        SharedPreferences properties = app.getAppPreferences();
        return properties.getString(key, defaultValue).equals(matchingValue);
    }

    /**
     * Lookup superuser preference; if debug build, superuser is enabled by
     * default.
     *
     * @return is the superuser developer preference enabled?
     */
    public static boolean isSuperuserEnabled() {
        return doesPropertyMatch(SUPERUSER_ENABLED, BuildConfig.DEBUG ? CommCarePreferences.YES : CommCarePreferences.NO, CommCarePreferences.YES);
    }

    public static boolean isActionBarEnabled() {
        return doesPropertyMatch(ACTION_BAR_ENABLED, CommCarePreferences.YES, CommCarePreferences.YES);
    }

    public static boolean isGridMenuEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(GRID_MENUS_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }

    public static boolean isNewNavEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(NAV_UI_ENABLED, CommCarePreferences.YES).equals(CommCarePreferences.YES);
    }
    
    public static boolean isCssEnabled(){
        return doesPropertyMatch(CSS_ENABLED, CommCarePreferences.NO, CommCarePreferences.YES);
    }

    public static boolean isListRefreshEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(LIST_REFRESH_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }

    /**
     * @return true if developer option to download the latest app version
     * deployed (built) is enabled.  Otherwise the latest released (starred)
     * app version will be downloaded on upgrade.
     */
    public static boolean isNewestAppVersionEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(NEWEST_APP_VERSION_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }

    public static boolean shouldFireTriggersOnSave() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(FIRE_TRIGGERS_ON_SAVE, CommCarePreferences.YES).equals(CommCarePreferences.YES);
    }

    public static boolean isAutoLoginEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(ENABLE_AUTO_LOGIN, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }

    public static boolean isSessionSavingEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(ENABLE_SAVE_SESSION, CommCarePreferences.NO).
                equals(CommCarePreferences.YES);
    }

    public static boolean isMarkdownEnabled(){
        return doesPropertyMatch(MARKDOWN_ENABLED, CommCarePreferences.NO, CommCarePreferences.YES);
    }

    public static boolean imageAboveTextEnabled() {
        return doesPropertyMatch(ALTERNATE_QUESTION_LAYOUT_ENABLED, CommCarePreferences.NO,
                CommCarePreferences.YES);
    }
                
    public static boolean isHomeReportEnabled() {
        return doesPropertyMatch(HOME_REPORT_ENABLED, CommCarePreferences.NO,
                CommCarePreferences.YES);
    }

    public static boolean areAnalyticsEnabled() {
        return doesPropertyMatch(ANALYTICS_ENABLED, CommCarePreferences.YES,
                CommCarePreferences.YES);
    }

}

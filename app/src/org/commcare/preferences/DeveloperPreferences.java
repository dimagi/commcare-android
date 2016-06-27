package org.commcare.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.SessionAwarePreferenceActivity;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.android.database.user.models.FormRecord;

import java.util.HashMap;
import java.util.Map;

public class DeveloperPreferences extends SessionAwarePreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public final static String SUPERUSER_ENABLED = "cc-superuser-enabled";
    public final static String NAV_UI_ENABLED = "cc-nav-ui-enabled";
    public final static String CSS_ENABLED = "cc-css-enabled";
    public final static String MARKDOWN_ENABLED = "cc-markdown-enabled";
    public final static String ACTION_BAR_ENABLED = "cc-action-nav-enabled";
    public final static String LIST_REFRESH_ENABLED = "cc-list-refresh";
    public final static String HOME_REPORT_ENABLED = "cc-home-report";
    public final static String AUTO_PURGE_ENABLED = "cc-auto-purge";
    public final static String LOAD_FORM_PAYLOAD_AS = "cc-form-payload-status";
    public final static String DETAIL_TAB_SWIPE_ACTION_ENABLED = "cc-detail-final-swipe-enabled";
    /**
     * Stores last used password and performs auto-login when that password is
     * present
     */
    public final static String ENABLE_AUTO_LOGIN = "cc-enable-auto-login";
    public final static String ENABLE_SAVE_SESSION = "cc-enable-session-saving";

    /**
     * Stores the navigation and form entry sessions as one string for user manipulation
     */
    public final static String EDIT_SAVE_SESSION = "__edit_session_save";
    /**
     * Spacer to distinguish between the saved navigation session and form entry session
     */
    private static final String NAV_AND_FORM_SESSION_SPACER = "@@@@@";

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

    public final static String OFFER_PIN_FOR_LOGIN = "cc-offer-pin-for-login";

    private static final Map<String, String> prefKeyToAnalyticsEvent = new HashMap<>();
    private Preference savedSessionEditTextPreference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_DEV_PREFS);

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));

        addPreferencesFromResource(R.xml.preferences_developer);
        setTitle("Developer Preferences");

        populatePrefKeyToEventLabelMapping();
        GoogleAnalyticsUtils.createPreferenceOnClickListeners(
                prefMgr, prefKeyToAnalyticsEvent, GoogleAnalyticsFields.CATEGORY_DEV_PREFS);

        savedSessionEditTextPreference = findPreference(EDIT_SAVE_SESSION);
        setSessionEditText();
    }

    private static void populatePrefKeyToEventLabelMapping() {
        prefKeyToAnalyticsEvent.put(SUPERUSER_ENABLED, GoogleAnalyticsFields.LABEL_DEV_MODE);
        prefKeyToAnalyticsEvent.put(ACTION_BAR_ENABLED, GoogleAnalyticsFields.LABEL_ACTION_BAR);
        prefKeyToAnalyticsEvent.put(NAV_UI_ENABLED, GoogleAnalyticsFields.LABEL_NAV_UI);
        prefKeyToAnalyticsEvent.put(LIST_REFRESH_ENABLED, GoogleAnalyticsFields.LABEL_ENTITY_LIST_REFRESH);
        prefKeyToAnalyticsEvent.put(NEWEST_APP_VERSION_ENABLED, GoogleAnalyticsFields.LABEL_NEWEST_APP_VERSION);
        prefKeyToAnalyticsEvent.put(ENABLE_AUTO_LOGIN, GoogleAnalyticsFields.LABEL_AUTO_LOGIN);
        prefKeyToAnalyticsEvent.put(ENABLE_SAVE_SESSION, GoogleAnalyticsFields.LABEL_SESSION_SAVING);
        prefKeyToAnalyticsEvent.put(EDIT_SAVE_SESSION, GoogleAnalyticsFields.LABEL_EDIT_SAVED_SESSION);
        prefKeyToAnalyticsEvent.put(CSS_ENABLED, GoogleAnalyticsFields.LABEL_CSS);
        prefKeyToAnalyticsEvent.put(MARKDOWN_ENABLED, GoogleAnalyticsFields.LABEL_MARKDOWN);
        prefKeyToAnalyticsEvent.put(ALTERNATE_QUESTION_LAYOUT_ENABLED, GoogleAnalyticsFields.LABEL_IMAGE_ABOVE_TEXT);
        prefKeyToAnalyticsEvent.put(FIRE_TRIGGERS_ON_SAVE, GoogleAnalyticsFields.LABEL_TRIGGERS_ON_SAVE);
        prefKeyToAnalyticsEvent.put(HOME_REPORT_ENABLED, GoogleAnalyticsFields.LABEL_REPORT_BUTTON_ENABLED);
        prefKeyToAnalyticsEvent.put(AUTO_PURGE_ENABLED, GoogleAnalyticsFields.LABEL_AUTO_PURGE);
        prefKeyToAnalyticsEvent.put(LOAD_FORM_PAYLOAD_AS, GoogleAnalyticsFields.LABEL_LOAD_FORM_PAYLOAD_AS);
        prefKeyToAnalyticsEvent.put(DETAIL_TAB_SWIPE_ACTION_ENABLED, GoogleAnalyticsFields.LABEL_DETAIL_TAB_SWIPE_ACTION);
    }

    private void setSessionEditText() {
        if (isSessionSavingEnabled()) {
            getPreferenceScreen().addPreference(savedSessionEditTextPreference);
            ((EditTextPreference)savedSessionEditTextPreference).setText(getSavedSessionStateAsString());
        } else {
            getPreferenceScreen().removePreference(savedSessionEditTextPreference);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        GoogleAnalyticsUtils.reportEditPref(GoogleAnalyticsFields.CATEGORY_DEV_PREFS,
                getEditPrefLabel(key), getEditPrefValue(key));
        switch (key) {
            case ENABLE_AUTO_LOGIN:
                if (!isAutoLoginEnabled()) {
                    DevSessionRestorer.clearPassword(sharedPreferences);
                }
                break;
            case EDIT_SAVE_SESSION:
                String sessionString =
                        CommCareApplication._().getCurrentApp().getAppPreferences().getString(EDIT_SAVE_SESSION, "");
                if (!"".equals(sessionString)) {
                    setSessionStateFromEditText(sessionString);
                }
                break;
            case ENABLE_SAVE_SESSION:
                if (!isSessionSavingEnabled()) {
                    DevSessionRestorer.clearSession();
                }
                setSessionEditText();

                break;
            case LOAD_FORM_PAYLOAD_AS:
                if (!formLoadPayloadStatus().equals(FormRecord.STATUS_SAVED)) {
                    // clear submission server so that 'unsent' forms that are loaded don't get sent to HQ
                    CommCareApplication._().getCurrentApp().getAppPreferences().edit()
                            .putString(CommCareServerPreferences.PREFS_SUBMISSION_URL_KEY, "")
                            .apply();
                }
                break;
        }
    }

    private static String getSavedSessionStateAsString() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String navSession = prefs.getString(CommCarePreferences.CURRENT_SESSION, "");
        String formEntrySession = prefs.getString(CommCarePreferences.CURRENT_FORM_ENTRY_SESSION, "");
        if ("".equals(navSession) && "".equals(formEntrySession)) {
            return "";
        } else {
            return navSession + NAV_AND_FORM_SESSION_SPACER + formEntrySession;
        }
    }

    private static void setSessionStateFromEditText(String sessionString) {
        SharedPreferences.Editor editor =
                CommCareApplication._().getCurrentApp().getAppPreferences().edit();
        String[] sessionParts = sessionString.split(NAV_AND_FORM_SESSION_SPACER);

        editor.putString(CommCarePreferences.CURRENT_SESSION, sessionParts[0]);
        editor.putString(CommCarePreferences.CURRENT_FORM_ENTRY_SESSION, sessionParts[1]);
        editor.commit();
    }

    private static String getEditPrefLabel(String key) {
        return prefKeyToAnalyticsEvent.get(key);
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
     * @param key           is a potential entry in the app preferences
     * @param defaultValue  use this value if key not found
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

    public static boolean isNewNavEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(NAV_UI_ENABLED, CommCarePreferences.YES).equals(CommCarePreferences.YES);
    }

    public static boolean isCssEnabled() {
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
        return properties.getString(FIRE_TRIGGERS_ON_SAVE, CommCarePreferences.NO).equals(CommCarePreferences.YES);
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

    public static boolean isMarkdownEnabled() {
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

    public static boolean shouldOfferPinForLogin() {
        return doesPropertyMatch(OFFER_PIN_FOR_LOGIN, CommCarePreferences.NO,
                CommCarePreferences.YES);
    }

    public static boolean isAutoPurgeEnabled() {
        return doesPropertyMatch(AUTO_PURGE_ENABLED, CommCarePreferences.NO,
                CommCarePreferences.YES);
    }

    public static String formLoadPayloadStatus() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(LOAD_FORM_PAYLOAD_AS, FormRecord.STATUS_SAVED);
    }

    /**
     * Feature flag to control whether swiping in case detail tabs can trigger
     * exit from the case detail screen
     */
    public static boolean isDetailTabSwipeActionEnabled() {
        return doesPropertyMatch(DETAIL_TAB_SWIPE_ACTION_ENABLED, CommCarePreferences.YES, CommCarePreferences.YES);
    }
}

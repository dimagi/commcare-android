package org.commcare.preferences;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.GlobalPrivilegeClaimingActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.javarosa.core.services.locale.Localization;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DeveloperPreferences extends CommCarePreferenceFragment {

    public static final int RESULT_SYNC_CUSTOM = Activity.RESULT_FIRST_USER + 1;
    public static final int RESULT_DEV_OPTIONS_DISABLED = Activity.RESULT_FIRST_USER + 2;

    private static final int MENU_ENABLE_PRIVILEGES = 0;

    // REGION - all Developer Preference keys

    public final static String PREFS_CUSTOM_RESTORE_DOC_LOCATION = "cc-custom-restore-doc-location";
    public static final String SUPERUSER_ENABLED = "cc-superuser-enabled";
    public static final String NAV_UI_ENABLED = "cc-nav-ui-enabled";
    public static final String CSS_ENABLED = "cc-css-enabled";
    public static final String MARKDOWN_ENABLED = "cc-markdown-enabled";
    public static final String ACTION_BAR_ENABLED = "cc-action-nav-enabled";
    public static final String LIST_REFRESH_ENABLED = "cc-list-refresh";
    public static final String HOME_REPORT_ENABLED = "cc-home-report";
    public static final String AUTO_PURGE_ENABLED = "cc-auto-purge";
    public static final String LOAD_FORM_PAYLOAD_AS = "cc-form-payload-status";
    public static final String DETAIL_TAB_SWIPE_ACTION_ENABLED = "cc-detail-final-swipe-enabled";
    public static final String USE_ROOT_MENU_AS_HOME_SCREEN = "cc-use-root-menu-as-home-screen";
    public static final String SHOW_ADB_ENTITY_LIST_TRACES = "cc-show-entity-trace-outputs";
    public static final String USE_OBFUSCATED_PW = "cc-use-pw-obfuscation";
    public static final String ENABLE_BULK_PERFORMANCE = "cc-enable-bulk-performance";
    public static final String SHOW_UPDATE_OPTIONS_SETTING = "cc-show-update-target-options";
    public static final String LOCAL_FORM_PAYLOAD_FILE_PATH = "cc-local-form-payload-file-path";
    public final static String REMOTE_FORM_PAYLOAD_URL = "remote-form-payload-url";
    public final static String HIDE_ISSUE_REPORT = "cc-hide-issue-report";
    public final static String ENFORCE_SECURE_ENDPOINT = "cc-enforce-secure-endpoint";

    public final static String PROJECT_SET_ACCESS_CODE = "cc-dev-prefs-access-code";
    public final static String USER_ENTERED_ACCESS_CODE = "cc-dev-prefs-user-entered-code";

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
    public final static String ALTERNATE_QUESTION_LAYOUT_ENABLED = "cc-alternate-question-text-format";
    public final static String OFFER_PIN_FOR_LOGIN = "cc-offer-pin-for-login";

    // ENDREGION

    private static final Set<String> WHITELISTED_DEVELOPER_PREF_KEYS = new HashSet<>();

    static {
        WHITELISTED_DEVELOPER_PREF_KEYS.add(SUPERUSER_ENABLED);
        WHITELISTED_DEVELOPER_PREF_KEYS.add(SHOW_UPDATE_OPTIONS_SETTING);
        WHITELISTED_DEVELOPER_PREF_KEYS.add(AUTO_PURGE_ENABLED);
        WHITELISTED_DEVELOPER_PREF_KEYS.add(ALTERNATE_QUESTION_LAYOUT_ENABLED);
    }

    /**
     * Spacer to distinguish between the saved navigation session and form entry session
     */
    private static final String NAV_AND_FORM_SESSION_SPACER = "@@@@@";

    private Preference savedSessionEditTextPreference;

    @NonNull
    @Override
    protected String getTitle() {
        return Localization.get("settings.developer.title");
    }

    @Override
    protected void setupPrefClickListeners() {
        // No listeners
    }

    @Nullable
    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return null;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.preferences_developer;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setHasOptionsMenu(true);
    }

    @Override
    protected void loadPrefs() {
        if (userAccessCodeNeeded()) {
            addPreferencesFromResource(R.xml.preferences_developer_access_code);
        } else {
            super.loadPrefs();
        }
    }

    private static boolean userAccessCodeNeeded() {
        if (GlobalPrivilegesManager.isAdvancedSettingsAccessEnabled()) {
            return false;
        }
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String projectAccessCode = prefs.getString(PROJECT_SET_ACCESS_CODE, null);
        if (projectAccessCode == null || "".equals(projectAccessCode)) {
            return false;
        }
        String userAccessCode = prefs.getString(USER_ENTERED_ACCESS_CODE, null);
        return !projectAccessCode.equals(userAccessCode);
    }

    @Override
    protected boolean isPersistentAppPreference() {
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!userAccessCodeNeeded()) {
            configureSettingsAfterLoad();
        }
    }

    private void configureSettingsAfterLoad() {
        savedSessionEditTextPreference = findPreference(EDIT_SAVE_SESSION);
        hideOrShowDangerousSettings();
        setSessionEditText();
    }

    private void setSessionEditText() {
        if (isSessionSavingEnabled()) {
            getPreferenceScreen().addPreference(savedSessionEditTextPreference);
            ((EditTextPreference)savedSessionEditTextPreference).setText(getSavedSessionStateAsString());
        } else {
            if (savedSessionEditTextPreference != null) {
                getPreferenceScreen().removePreference(savedSessionEditTextPreference);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);

        switch (key) {
            case SUPERUSER_ENABLED:
                clearUserEnteredAccessCode();
                this.getActivity().setResult(RESULT_DEV_OPTIONS_DISABLED);
                this.getActivity().finish();
                break;
            case ENABLE_AUTO_LOGIN:
                if (!isAutoLoginEnabled()) {
                    DevSessionRestorer.clearPassword(sharedPreferences);
                }
                break;
            case EDIT_SAVE_SESSION:
                String sessionString =
                        CommCareApplication.instance().getCurrentApp().getAppPreferences().getString(EDIT_SAVE_SESSION, "");
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
            case PREFS_CUSTOM_RESTORE_DOC_LOCATION:
                String filePath = getCustomRestoreDocLocation();
                if (!filePath.isEmpty()) {
                    getActivity().setResult(DeveloperPreferences.RESULT_SYNC_CUSTOM);
                    getActivity().finish();
                }
                break;
            case USER_ENTERED_ACCESS_CODE:
                if (CommCareApplication.instance().getCurrentApp().getAppPreferences().getString(USER_ENTERED_ACCESS_CODE, null) == null) {
                    // if this is being triggered by us clearing it out, don't do anything
                    return;
                } else if (userEnteredAccessCodeMatches()) {
                    getPreferenceScreen().removeAll();
                    loadPrefs();
                    configureSettingsAfterLoad();
                    Toast.makeText(this.getContext(), Localization.get("dev.options.access.granted"),
                            Toast.LENGTH_SHORT).show();
                } else {
                    clearUserEnteredAccessCode();
                    Toast.makeText(this.getContext(), Localization.get("dev.options.code.incorrect"),
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private static boolean userEnteredAccessCodeMatches() {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String projectAccessCode = prefs.getString(PROJECT_SET_ACCESS_CODE, null);
        String userAccessCode = prefs.getString(USER_ENTERED_ACCESS_CODE, null);
        return userAccessCode == null ? false : userAccessCode.equals(projectAccessCode);
    }

    private static void clearUserEnteredAccessCode() {
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putString(USER_ENTERED_ACCESS_CODE, null).apply();
    }

    private static String getSavedSessionStateAsString() {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String navSession = prefs.getString(DevSessionRestorer.CURRENT_SESSION, "");
        String formEntrySession = prefs.getString(DevSessionRestorer.CURRENT_FORM_ENTRY_SESSION, "");
        if ("".equals(navSession) && "".equals(formEntrySession)) {
            return "";
        } else {
            return navSession + NAV_AND_FORM_SESSION_SPACER + formEntrySession;
        }
    }

    private static void setSessionStateFromEditText(String sessionString) {
        SharedPreferences.Editor editor =
                CommCareApplication.instance().getCurrentApp().getAppPreferences().edit();
        String[] sessionParts = sessionString.split(NAV_AND_FORM_SESSION_SPACER);

        editor.putString(DevSessionRestorer.CURRENT_SESSION, sessionParts[0]);
        editor.putString(DevSessionRestorer.CURRENT_FORM_ENTRY_SESSION, sessionParts[1]);
        editor.commit();
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
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
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
        return doesPropertyMatch(SUPERUSER_ENABLED,
                BuildConfig.DEBUG ? PrefValues.YES : PrefValues.NO,
                PrefValues.YES);
    }

    public static boolean isActionBarEnabled() {
        return doesPropertyMatch(ACTION_BAR_ENABLED, PrefValues.YES, PrefValues.YES);
    }

    public static boolean isNewNavEnabled() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(NAV_UI_ENABLED, PrefValues.YES).equals(PrefValues.YES);
    }

    public static boolean isCssEnabled() {
        return doesPropertyMatch(CSS_ENABLED, PrefValues.NO, PrefValues.YES);
    }

    public static boolean isListRefreshEnabled() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(LIST_REFRESH_ENABLED, PrefValues.NO).equals(PrefValues.YES);
    }

    public static boolean isAutoLoginEnabled() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(ENABLE_AUTO_LOGIN, PrefValues.NO).equals(PrefValues.YES);
    }

    public static boolean isSessionSavingEnabled() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            // null check needed for corner case in robolectric tests
            return false;
        } else {
            SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
            return properties.getString(ENABLE_SAVE_SESSION, PrefValues.NO).equals(PrefValues.YES);
        }
    }

    public static void enableSessionSaving() {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putString(DeveloperPreferences.ENABLE_SAVE_SESSION, PrefValues.YES)
                .apply();
    }

    public static boolean isMarkdownEnabled() {
        return doesPropertyMatch(MARKDOWN_ENABLED, PrefValues.NO, PrefValues.YES);
    }

    public static boolean imageAboveTextEnabled() {
        return doesPropertyMatch(ALTERNATE_QUESTION_LAYOUT_ENABLED, PrefValues.NO, PrefValues.YES);
    }

    public static boolean isHomeReportEnabled() {
        return doesPropertyMatch(HOME_REPORT_ENABLED, PrefValues.NO, PrefValues.YES);
    }

    public static boolean shouldOfferPinForLogin() {
        return doesPropertyMatch(OFFER_PIN_FOR_LOGIN, PrefValues.NO, PrefValues.YES);
    }

    public static boolean isAutoPurgeEnabled() {
        return doesPropertyMatch(AUTO_PURGE_ENABLED, PrefValues.NO, PrefValues.YES);
    }

    public static String formLoadPayloadStatus() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(LOAD_FORM_PAYLOAD_AS, FormRecord.STATUS_SAVED);
    }

    /**
     * Feature flag to control whether swiping in case detail tabs can trigger
     * exit from the case detail screen
     */
    public static boolean isDetailTabSwipeActionEnabled() {
        return doesPropertyMatch(DETAIL_TAB_SWIPE_ACTION_ENABLED, PrefValues.YES, PrefValues.YES);
    }

    public static boolean useRootModuleMenuAsHomeScreen() {
        return doesPropertyMatch(USE_ROOT_MENU_AS_HOME_SCREEN, PrefValues.NO, PrefValues.YES);
    }


    public static boolean collectAndDisplayEntityTraces() {
        return doesPropertyMatch(SHOW_ADB_ENTITY_LIST_TRACES, PrefValues.NO, PrefValues.YES);
    }

    public static boolean useObfuscatedPassword() {
        return doesPropertyMatch(USE_OBFUSCATED_PW, PrefValues.NO, PrefValues.YES);
    }

    public static boolean isBulkPerformanceEnabled() {
        return doesPropertyMatch(ENABLE_BULK_PERFORMANCE, PrefValues.NO, PrefValues.YES);
    }

    public static boolean shouldShowUpdateOptionsSetting() {
        return doesPropertyMatch(SHOW_UPDATE_OPTIONS_SETTING, PrefValues.NO,
                PrefValues.YES) || BuildConfig.DEBUG;
    }

    public static String getLocalFormPayloadFilePath() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(LOCAL_FORM_PAYLOAD_FILE_PATH, "");
    }

    public static String getRemoteFormPayloadUrl() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(REMOTE_FORM_PAYLOAD_URL, CommCareApplication.instance().getString(R.string.remote_form_payload_url));
    }

    public static String getCustomRestoreDocLocation() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(PREFS_CUSTOM_RESTORE_DOC_LOCATION, "");
    }

    public static boolean shouldHideReportIssue() {
        return doesPropertyMatch(HIDE_ISSUE_REPORT, PrefValues.NO, PrefValues.YES);
    }

    public static boolean isEnforceSecureEndpointEnabled() {
        return doesPropertyMatch(ENFORCE_SECURE_ENDPOINT, PrefValues.NO, PrefValues.YES);
    }

    private void hideOrShowDangerousSettings() {
        Preference[] onScreenPrefs = getOnScreenPrefs();
        if (!GlobalPrivilegesManager.isAdvancedSettingsAccessEnabled() && !BuildConfig.DEBUG) {
            // Dangerous privileges should not be showing
            PreferenceScreen prefScreen = getPreferenceScreen();
            for (Preference p : onScreenPrefs) {
                if (p != null && !WHITELISTED_DEVELOPER_PREF_KEYS.contains(p.getKey())) {
                    prefScreen.removePreference(p);
                }
            }
        } else {
            // Dangerous privileges should be showing
            if (onScreenPrefs.length == WHITELISTED_DEVELOPER_PREF_KEYS.size()) {
                // If we're currently showing only white-listed prefs, reset
                reset();
            }
        }
    }

    private Preference[] getOnScreenPrefs() {
        PreferenceScreen prefScreen = getPreferenceScreen();
        Preference[] prefs = new Preference[prefScreen.getPreferenceCount()];
        for (int i = 0; i < prefScreen.getPreferenceCount(); i++) {
            prefs[i] = prefScreen.getPreference(i);
        }
        return prefs;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(0, MENU_ENABLE_PRIVILEGES, 0, Localization.get("menu.enable.privileges"))
                .setIcon(android.R.drawable.ic_menu_preferences);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ENABLE_PRIVILEGES:
                Intent i = new Intent(getActivity(), GlobalPrivilegeClaimingActivity.class);
                startActivity(i);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}

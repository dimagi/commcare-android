package org.commcare.preferences;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.support.annotation.NonNull;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCarePreferenceActivity;
import org.commcare.activities.SessionAwarePreferenceActivity;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.commcare.views.PasswordShow;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainConfigurablePreferences
        extends CommCarePreferenceFragment
        implements OnSharedPreferenceChangeListener {

    // Preferences the user has direct control over within CommCare
    public final static String PREFS_PRINT_DOC_LOCATION = "print-doc-location";
    private final static String PREFS_FUZZY_SEARCH_KEY = "cc-fuzzy-search-enabled";
    public final static String GRID_MENUS_ENABLED = "cc-grid-menus";
    public final static String UPDATE_TARGET = "cc-update-target";
    public final static String AUTO_UPDATE_FREQUENCY = "cc-autoup-freq";
    public final static String SHOW_PASSWORD_OPTION = "cc-password-entry-show-behavior";
    public final static String PREFS_LOCALE_KEY = "cur_locale";
    public final static String ANALYTICS_ENABLED = "cc-analytics-enabled";
    public final static String PREF_IS_LOCALE_RTL = "is_cur_locale_rtl";

    // Fake settings that really act as buttons to open a new activity or choice dialog
    private final static String DEVELOPER_SETTINGS = "developer-settings-button";
    private final static String DISABLE_ANALYTICS = "disable-analytics-button";

    private static final int REQUEST_DEVELOPER_PREFERENCES = 1;

    private final static Map<String, String> keyToTitleMap = new HashMap<>();
    static {
        keyToTitleMap.put(DEVELOPER_SETTINGS, "settings.developer.options");
        keyToTitleMap.put(DISABLE_ANALYTICS, "home.menu.disable.analytics");
    }

    @NonNull
    @Override
    protected String getTitle() {
        return Localization.get("settings.main.title");
    }

    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return keyToTitleMap;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.main_preferences;
    }

    @Override
    protected boolean isPersistentAppPreference() {
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        setVisibilityOfUpdateOptionsPref();
    }

    private void setVisibilityOfUpdateOptionsPref() {
        Preference updateOptionsPref = getPreferenceManager().findPreference(UPDATE_TARGET);
        if (!DeveloperPreferences.shouldShowUpdateOptionsSetting() && updateOptionsPref != null) {
            // If the pref is showing and it shouldn't be
            PreferenceScreen prefScreen = getPreferenceScreen();
            prefScreen.removePreference(updateOptionsPref);
        } else if (DeveloperPreferences.shouldShowUpdateOptionsSetting() &&
                updateOptionsPref == null) {
            // If the pref isn't showing and it should be
            reset();
        }
    }

    @Override
    protected void setupPrefClickListeners() {
        configureDevPreferencesButton();

        Preference analyticsButton = findPreference(DISABLE_ANALYTICS);
        if (MainConfigurablePreferences.isAnalyticsEnabled()) {
            analyticsButton.setOnPreferenceClickListener(preference -> {
                showAnalyticsOptOutDialog();
                return true;
            });
        } else {
            getPreferenceScreen().removePreference(analyticsButton);
        }
    }

    private void configureDevPreferencesButton() {
        Preference developerSettingsButton = findPreference(DEVELOPER_SETTINGS);
        if (DeveloperPreferences.isSuperuserEnabled()) {
            developerSettingsButton.setOnPreferenceClickListener(preference -> {
                startDeveloperOptions();
                return true;
            });
        } else {
            getPreferenceScreen().removePreference(developerSettingsButton);
        }
    }

    private void startDeveloperOptions() {
        Intent intent = new Intent(getActivity(), SessionAwarePreferenceActivity.class);
        intent.putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE,CommCarePreferenceActivity.PREF_TYPE_DEVELOPER);
        startActivityForResult(intent, REQUEST_DEVELOPER_PREFERENCES);
    }

    private void showAnalyticsOptOutDialog() {
        StandardAlertDialog f = new StandardAlertDialog(getActivity(),
                Localization.get("analytics.opt.out.title"),
                Localization.get("analytics.opt.out.message"));

        f.setPositiveButton(Localization.get("analytics.disable.button"),
                (dialog, which) -> {
                    dialog.dismiss();
                    MainConfigurablePreferences.disableAnalytics();
                });

        f.setNegativeButton(Localization.get("option.cancel"),
                (dialog, which) -> dialog.dismiss());

        f.showNonPersistentDialog();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_DEVELOPER_PREFERENCES) {
            if (resultCode == DeveloperPreferences.RESULT_SYNC_CUSTOM) {
                getActivity().setResult(DeveloperPreferences.RESULT_SYNC_CUSTOM);
                getActivity().finish();
            }
            else if (resultCode == DeveloperPreferences.RESULT_DEV_OPTIONS_DISABLED) {
                configureDevPreferencesButton();
            }
        }
    }

    public static PasswordShow.PasswordShowOption getPasswordDisplayOption() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return PasswordShow.PasswordShowOption.fromString(properties.getString(SHOW_PASSWORD_OPTION, ""));
    }

    public static boolean isGridMenuEnabled() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(GRID_MENUS_ENABLED, PrefValues.NO).equals(PrefValues.YES);
    }

    public static boolean isFuzzySearchEnabled() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(PREFS_FUZZY_SEARCH_KEY, PrefValues.NO).equals(PrefValues.YES);
    }

    public static boolean isAnalyticsEnabled() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        if (app == null) {
            return true;
        }
        return app.getAppPreferences().getBoolean(ANALYTICS_ENABLED, true);
    }

    public static void disableAnalytics() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        if (app == null) {
            return;
        }
        app.getAppPreferences().edit().putBoolean(ANALYTICS_ENABLED, false).apply();
    }

    public static String getUpdateTargetParam() {
        String updateTarget = getUpdateTarget();
        if (PrefValues.UPDATE_TARGET_BUILD.equals(updateTarget)
                || PrefValues.UPDATE_TARGET_SAVED.equals(updateTarget)) {
            // We only need to add a query param to the update URL if the target is set to
            // something other than the default
            return updateTarget;
        } else {
            return "";
        }
    }

    /**
     * @return the update target set by the user, or default to latest starred build if none set.
     * The 3 options are:
     * 1. Latest starred build (this is the default)
     * 2. Latest build, starred or un-starred
     * 3. Latest saved version (whether or not a build has been created it for it)
     */
    private static String getUpdateTarget() {
        SharedPreferences properties = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return properties.getString(UPDATE_TARGET, PrefValues.UPDATE_TARGET_STARRED);
    }

    public static void setUpdateTarget(String updateTargetValue) {
        if (PrefValues.UPDATE_TARGET_BUILD.equals(updateTargetValue) ||
                PrefValues.UPDATE_TARGET_SAVED.equals(updateTargetValue) ||
                PrefValues.UPDATE_TARGET_STARRED.equals(updateTargetValue)) {
            CommCareApplication.instance().getCurrentApp().getAppPreferences()
                    .edit()
                    .putString(UPDATE_TARGET, updateTargetValue)
                    .apply();
        }
    }

    public static String getGlobalTemplatePath() {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String path = prefs.getString(MainConfigurablePreferences.PREFS_PRINT_DOC_LOCATION, "");
        if ("".equals(path)) {
            return null;
        } else {
            return path;
        }
    }

    public static void setCurrentLocale(String currentLocale) {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        prefs.edit().putString(PREFS_LOCALE_KEY, currentLocale).apply();
        updateLocaleRTLPrefs(currentLocale);
    }

    @SuppressLint("ApplySharedPref")
    private static void updateLocaleRTLPrefs(String locale) {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        prefs.edit().putBoolean(PREF_IS_LOCALE_RTL,
                TextUtilsCompat.getLayoutDirectionFromLocale(new Locale(locale)) == ViewCompat.LAYOUT_DIRECTION_RTL)
                .commit();
    }

    public static boolean isLocaleRTL() {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return prefs.getBoolean(PREF_IS_LOCALE_RTL, false);
    }
}

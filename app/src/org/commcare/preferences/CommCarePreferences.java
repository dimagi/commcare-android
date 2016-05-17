package org.commcare.preferences;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.SessionAwarePreferenceActivity;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.utils.FileUtil;
import org.commcare.utils.TemplatePrinterUtils;
import org.commcare.utils.UriToFilePath;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import java.util.HashMap;
import java.util.Map;

public class CommCarePreferences
        extends SessionAwarePreferenceActivity
        implements OnSharedPreferenceChangeListener {

    private final static String TAG = CommCarePreferences.class.getSimpleName();

    /**
     * Entries used as buttons; aren't actually stored preferences
     */
    private final static String SERVER_SETTINGS = "server-settings";
    private final static String DEVELOPER_SETTINGS = "developer-settings";
    private final static String CLEAR_SAVED_SESSION = "clear-saved-session";
    private final static String DISABLE_ANALYTICS = "disable-analytics";

    /**
     * update/sync frequency settings
     */
    public final static String AUTO_SYNC_FREQUENCY = "cc-autosync-freq";
    public final static String AUTO_UPDATE_FREQUENCY = "cc-autoup-freq";
    public final static String FREQUENCY_NEVER = "freq-never";
    public final static String FREQUENCY_DAILY = "freq-daily";

    public final static String LOG_LAST_DAILY_SUBMIT = "log_prop_last_daily";
    public final static String LOG_NEXT_WEEKLY_SUBMIT = "log_prop_next_weekly";

    /**
     * Stores boolean flag that tells of if an auto-update is in progress, that
     * is, actively checking or with a retry check queued up.
     */
    public final static String AUTO_UPDATE_IN_PROGRESS = "cc-trying-to-auto-update";
    public final static String LAST_UPDATE_ATTEMPT = "cc-last_up";
    public final static String LAST_SYNC_ATTEMPT = "last-ota-restore";

    public final static String ENABLE_SAVED_FORMS = "cc-show-saved";
    public final static String ENABLE_INCOMPLETE_FORMS = "cc-show-incomplete";

    public final static String RESIZING_METHOD = "cc-resize-images";

    private static final String KEY_TARGET_DENSITY = "cc-inflation-target-density";
    private static final String DEFAULT_TARGET_DENSITY = "" + DisplayMetrics.DENSITY_DEFAULT;

    public final static String LAST_LOGGED_IN_USER = "last_logged_in_user";
    public final static String LAST_PASSWORD = "last_password";
    public final static String CURRENT_SESSION = "current_user_session";
    public final static String CURRENT_FORM_ENTRY_SESSION = "current_form_entry_session";
    public final static String CONTENT_VALIDATED = "cc-content-valid";
    public static final String DUMP_FOLDER_PATH = "dump-folder-path";
    public final static String LOG_ENTITY_DETAIL = "cc-log-entity-detail-enabled";
    public final static String LOGIN_DURATION = "cc-login-duration-seconds";
    public final static String BRAND_BANNER_LOGIN = "brand-banner-login";
    public final static String BRAND_BANNER_HOME = "brand-banner-home";
    private final static String PREFS_FUZZY_SEARCH_KEY = "cc-fuzzy-search-enabled";
    public final static String PREFS_LOCALE_KEY = "cur_locale";
    public final static String PREFS_PRINT_DOC_LOCATION = "print-doc-location";
    public final static String ANALYTICS_ENABLED = "cc-analytics-enabled";
    public final static String HAS_DISMISSED_PIN_CREATION = "has-dismissed-pin-creation";
    public final static String GRID_MENUS_ENABLED = "cc-grid-menus";

    public final static String YES = "yes";
    public final static String NO = "no";
    public final static String NONE = "none";

    public final static String TRUE = "True";
    public final static String FALSE = "False";

    private static final int REQUEST_TEMPLATE = 0;

    private final static Map<String, String> prefKeyToAnalyticsEvent = new HashMap<>();
    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(SERVER_SETTINGS, "settings.server.listing");
        keyToTitleMap.put(DEVELOPER_SETTINGS, "settings.developer.options");
        keyToTitleMap.put(CLEAR_SAVED_SESSION, "menu.clear.saved.session");
        keyToTitleMap.put(DISABLE_ANALYTICS, "home.menu.disable.analytics");

        prefKeyToAnalyticsEvent.put(AUTO_UPDATE_FREQUENCY, GoogleAnalyticsFields.LABEL_AUTO_UPDATE);
        prefKeyToAnalyticsEvent.put(PREFS_FUZZY_SEARCH_KEY, GoogleAnalyticsFields.LABEL_FUZZY_SEARCH);
        prefKeyToAnalyticsEvent.put(GRID_MENUS_ENABLED, GoogleAnalyticsFields.LABEL_GRID_MENUS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));
        addPreferencesFromResource(R.xml.commcare_preferences);

        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_CC_PREFS);

        setupUI();

        GoogleAnalyticsUtils.createPreferenceOnClickListeners(prefMgr, prefKeyToAnalyticsEvent,
                GoogleAnalyticsFields.CATEGORY_CC_PREFS);
        // Override the default listener for the print pref key b/c it has extra behavior
        createPrintPrefOnClickListener(prefMgr);
    }

    private void setupUI() {
        setTitle(Localization.get("settings.main.title"));
        addBackButtonToActionBar(this);
        setupLocalizedText(this, keyToTitleMap);
        setupButtons();
    }

    public static void setupLocalizedText(PreferenceActivity activity,
                                             Map<String, String> prefToTitleMap) {
        PreferenceScreen screen = activity.getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            String key = screen.getPreference(i).getKey();
            if (prefToTitleMap.containsKey(key)) {
                try {
                    String localizedString = Localization.get(prefToTitleMap.get(key));
                    screen.getPreference(i).setTitle(localizedString);
                } catch (NoLocalizedTextException nle) {
                    Log.w(TAG, "Unable to localize: " + prefToTitleMap.get(key));
                }
            }
        }
    }

    public static void addBackButtonToActionBar(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    private void setupButtons() {
        Preference serverSettingsButton = findPreference(SERVER_SETTINGS);
        serverSettingsButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                startServerSettings();
                return true;
            }
        });

        Preference developerSettingsButton = findPreference(DEVELOPER_SETTINGS);
        if (DeveloperPreferences.isSuperuserEnabled()) {
            developerSettingsButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    GoogleAnalyticsUtils.reportPrefItemClick(
                            GoogleAnalyticsFields.CATEGORY_CC_PREFS,
                            GoogleAnalyticsFields.LABEL_DEVELOPER_OPTIONS);
                    startDeveloperOptions();
                    return true;
                }
            });
        } else {
            getPreferenceScreen().removePreference(developerSettingsButton);
        }

        Preference clearSavedSessionButton = findPreference(CLEAR_SAVED_SESSION);
        if (DevSessionRestorer.savedSessionPresent()) {
            clearSavedSessionButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    GoogleAnalyticsUtils.reportPrefItemClick(
                            GoogleAnalyticsFields.CATEGORY_CC_PREFS,
                            GoogleAnalyticsFields.LABEL_CLEAR_SAVED_SESSION);
                    DevSessionRestorer.clearSession();
                    return true;
                }
            });
        } else {
            getPreferenceScreen().removePreference(clearSavedSessionButton);
        }

        Preference analyticsButton = findPreference(DISABLE_ANALYTICS);
        if (CommCarePreferences.isAnalyticsEnabled()) {
            analyticsButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showAnalyticsOptOutDialog();
                    return true;
                }
            });
        } else {
            getPreferenceScreen().removePreference(analyticsButton);
        }
    }

    private void showAnalyticsOptOutDialog() {
        StandardAlertDialog f = new StandardAlertDialog(this,
                Localization.get("analytics.opt.out.title"),
                Localization.get("analytics.opt.out.message"));

        f.setPositiveButton(Localization.get("analytics.disable.button"),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        CommCarePreferences.disableAnalytics();
                    }
                });

        f.setNegativeButton(Localization.get("option.cancel"),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        f.showNonPersistentDialog();
    }

    private void createPrintPrefOnClickListener(PreferenceManager prefManager) {
        Preference pref = prefManager.findPreference(PREFS_PRINT_DOC_LOCATION);
        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportPrefItemClick(
                        GoogleAnalyticsFields.CATEGORY_CC_PREFS,
                        GoogleAnalyticsFields.LABEL_PRINT_TEMPLATE);
                startFileBrowser();
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TEMPLATE) {
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                String filePath = UriToFilePath.getPathFromUri(CommCareApplication._(), uri);
                String extension = FileUtil.getExtension(filePath);
                if (extension.equalsIgnoreCase("html")) {
                    SharedPreferences.Editor editor = CommCareApplication._().getCurrentApp().
                            getAppPreferences().edit();
                    editor.putString(PREFS_PRINT_DOC_LOCATION, filePath);
                    editor.commit();
                    Toast.makeText(this, Localization.get("template.success"), Toast.LENGTH_SHORT).show();
                } else {
                    TemplatePrinterUtils.showAlertDialog(this, Localization.get("template.not.set"),
                            Localization.get("template.warning"), false);
                }
            } else {
                //No file selected
                Toast.makeText(this, Localization.get("template.not.set"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        int editPrefValue = -1;
        switch (key) {
            case AUTO_UPDATE_FREQUENCY:
                String freq = sharedPreferences.getString(key, CommCarePreferences.FREQUENCY_NEVER);
                if (CommCarePreferences.FREQUENCY_NEVER.equals(freq)) {
                    editPrefValue = GoogleAnalyticsFields.VALUE_NEVER;
                } else if (CommCarePreferences.FREQUENCY_DAILY.equals(freq)) {
                    editPrefValue = GoogleAnalyticsFields.VALUE_DAILY;
                } else {
                    editPrefValue = GoogleAnalyticsFields.VALUE_WEEKLY;
                }
                break;
            case PREFS_FUZZY_SEARCH_KEY:
                if (isFuzzySearchEnabled()) {
                    editPrefValue = GoogleAnalyticsFields.VALUE_ENABLED;
                } else {
                    editPrefValue = GoogleAnalyticsFields.VALUE_DISABLED;
                }
                break;
        }

        GoogleAnalyticsUtils.reportEditPref(GoogleAnalyticsFields.CATEGORY_CC_PREFS,
                prefKeyToAnalyticsEvent.get(key), editPrefValue);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static boolean isInSenseMode() {
        return (CommCareApplication._().getCommCarePlatform().getCurrentProfile() != null &&
                CommCareApplication._().getCommCarePlatform().getCurrentProfile().isFeatureActive("sense"));
    }

    public static boolean isIncompleteFormsEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        //If there is a setting for form management it takes precedence
        if (properties.contains(ENABLE_INCOMPLETE_FORMS)) {

            return properties.getString(ENABLE_INCOMPLETE_FORMS, YES).equals(YES);
        }

        //otherwise, see if we're in sense mode
        return !isInSenseMode();
    }

    public static boolean isSavedFormsEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        //If there is a setting for form management it takes precedence
        if (properties.contains(ENABLE_SAVED_FORMS)) {
            return properties.getString(ENABLE_SAVED_FORMS, YES).equals(YES);
        }

        //otherwise, see if we're in sense mode
        return !isInSenseMode();
    }

    public static boolean isGridMenuEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(GRID_MENUS_ENABLED, CommCarePreferences.NO).equals(CommCarePreferences.YES);
    }


    public static boolean isFuzzySearchEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(PREFS_FUZZY_SEARCH_KEY, NO).equals(YES);
    }

    public static boolean isEntityDetailLoggingEnabled() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return properties.getString(LOG_ENTITY_DETAIL, FALSE).equals(TRUE);
    }

    /**
     * @return How many seconds should a user session remain open before
     * expiring?
     */
    public static int getLoginDuration() {
        final int oneDayInSecs = 60 * 60 * 24;

        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();

        // try loading setting but default to 24 hours
        try {
            return Integer.parseInt(properties.getString(LOGIN_DURATION,
                    Integer.toString(oneDayInSecs)));
        } catch (NumberFormatException e) {
            return oneDayInSecs;
        }
    }

    public static String getResizeMethod() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        //If there is a setting for form management it takes precedence
        if (properties.contains(RESIZING_METHOD)) {
            return properties.getString(RESIZING_METHOD, CommCarePreferences.NONE);
        }

        //otherwise, see if we're in sense mode
        return CommCarePreferences.NONE;
    }

    public static boolean isSmartInflationEnabled() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app == null) {
            return false;
        }
        String targetDensitySetting = app.getAppPreferences().getString(KEY_TARGET_DENSITY,
                CommCarePreferences.NONE);
        return !targetDensitySetting.equals(CommCarePreferences.NONE);
    }

    public static int getTargetInflationDensity() {
        SharedPreferences properties = CommCareApplication._().getCurrentApp().getAppPreferences();
        return Integer.parseInt(properties.getString(KEY_TARGET_DENSITY, DEFAULT_TARGET_DENSITY));
    }

    public static boolean isAnalyticsEnabled() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app == null) {
            return true;
        }
        return app.getAppPreferences().getBoolean(ANALYTICS_ENABLED, true);
    }

    public static void disableAnalytics() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app == null) {
            return;
        }
        app.getAppPreferences().edit().putBoolean(ANALYTICS_ENABLED, false).commit();
    }

    private void startFileBrowser() {
        Intent chooseTemplateIntent = new Intent()
                .setAction(Intent.ACTION_GET_CONTENT)
                .setType("file/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(chooseTemplateIntent, REQUEST_TEMPLATE);
        } catch (ActivityNotFoundException e) {
            // Means that there is no file browser installed on the device
            TemplatePrinterUtils.showAlertDialog(this, Localization.get("cannot.set.template"),
                    Localization.get("no.file.browser"), false);
        }
    }

    private void startServerSettings() {
        Intent i = new Intent(this, CommCareServerPreferences.class);
        startActivity(i);
    }

    private void startDeveloperOptions() {
        Intent intent = new Intent(this, DeveloperPreferences.class);
        startActivity(intent);
    }
}

package org.commcare.dalvik.preferences;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.commcare.android.analytics.GoogleAnalyticsFields;
import org.commcare.android.analytics.GoogleAnalyticsUtils;
import org.commcare.android.framework.SessionAwarePreferenceActivity;
import org.commcare.android.session.DevSessionRestorer;
import org.commcare.android.util.ChangeLocaleUtil;
import org.commcare.android.util.CommCareUtil;
import org.commcare.android.util.TemplatePrinterUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.RecoveryActivity;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.utils.UriToFilePath;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;
import org.odk.collect.android.utilities.FileUtils;

import java.util.HashMap;
import java.util.Map;

public class CommCarePreferences
        extends SessionAwarePreferenceActivity
        implements OnSharedPreferenceChangeListener {
    //So these are stored in the R files, but I dont' seem to be able to figure out how to pull them
    //out cleanly?
    public final static String AUTO_SYNC_FREQUENCY = "cc-autosync-freq";
    public final static String AUTO_UPDATE_FREQUENCY = "cc-autoup-freq";
    public final static String FREQUENCY_NEVER = "freq-never";
    public final static String FREQUENCY_DAILY = "freq-daily";
    public final static String FREQUENCY_WEEKLY = "freq-weekly";

    public final static String ENABLE_SAVED_FORMS = "cc-show-saved";

    public final static String ENABLE_INCOMPLETE_FORMS = "cc-show-incomplete";

    /**
     * Stores boolean flag that tells of if an auto-update is in progress, that
     * is, actively checking or with a retry check queued up.
     */
    public final static String AUTO_UPDATE_IN_PROGRESS = "cc-trying-to-auto-update";
    public final static String LAST_UPDATE_ATTEMPT = "cc-last_up";
    public final static String LAST_SYNC_ATTEMPT = "last-ota-restore";

    public final static String LOG_WEEKLY_SUBMIT = "log_prop_weekly";
    public final static String LOG_DAILY_SUBMIT = "log_prop_daily";

    public final static String RESIZING_METHOD = "cc-resize-images";

    private static final String KEY_TARGET_DENSITY = "cc-inflation-target-density";
    private static final String DEFAULT_TARGET_DENSITY = "" + DisplayMetrics.DENSITY_DEFAULT;

    public final static String NEVER = "log_never";
    public final static String SHORT = "log_short";
    public final static String FULL = "log_full";

    // TODO PLM: these flags aren't provided by HQ built apps,
    // should be replaced with LOG_WEEKLY_SUBMIT above!
    public final static String LOG_LAST_DAILY_SUBMIT = "log_prop_last_daily";
    public final static String LOG_NEXT_WEEKLY_SUBMIT = "log_prop_next_weekly";

    public final static String LAST_LOGGED_IN_USER = "last_logged_in_user";
    public final static String LAST_PASSWORD = "last_password";
    public final static String CURRENT_SESSION = "current_user_session";
    public final static String CONTENT_VALIDATED = "cc-content-valid";

    public final static String YES = "yes";
    public final static String NO = "no";
    public final static String NONE = "none";

    public final static String TRUE = "True";
    public final static String FALSE = "False";

    public static final String DUMP_FOLDER_PATH = "dump-folder-path";

    public final static String FUZZY_SEARCH = "cc-fuzzy-search-enabled";
    public final static String LOG_ENTITY_DETAIL = "cc-log-entity-detail-enabled";

    public final static String LOGIN_DURATION = "cc-login-duration-seconds";

    public final static String BRAND_BANNER_LOGIN = "brand-banner-login";
    public final static String BRAND_BANNER_HOME = "brand-banner-home";

    public final static String ACTIONBAR_PREFS = "actionbar-prefs";

    private static final int CLEAR_USER_DATA = Menu.FIRST;
    private static final int FORCE_LOG_SUBMIT = Menu.FIRST + 1;
    private static final int RECOVERY_MODE = Menu.FIRST + 2;
    private static final int SUPERUSER_PREFS = Menu.FIRST + 3;
    private static final int MENU_CLEAR_SAVED_SESSION = Menu.FIRST + 4;

    // Fields for setting print template
    private static final int REQUEST_TEMPLATE = 0;
    public final static String PRINT_DOC_LOCATION = "print_doc_location";

    private final static String PREFS_APP_SERVER_KEY = "default_app_server";
    private final static String PREFS_DATA_SERVER_KEY = "ota-restore-url";
    private final static String PREFS_SUBMISSION_URL_KEY = "PostURL";
    private final static String PREFS_KEY_SERVER_KEY = "default_key_server";
    private final static String PREFS_FORM_RECORD_KEY = "form-record-url";
    private final static String PREFS_UPDATE_FREQUENCY_KEY = "cc-autoup-freq";
    private final static String PREFS_FUZZY_SEARCH_KEY = "cc-fuzzy-search-enabled";
    private final static String PREFS_LOCALE_KEY = "cur_locale";
    private final static String PREFS_PRINT_KEY = "print-doc-location";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));
        addPreferencesFromResource(R.xml.server_preferences);

        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_CC_PREFS);

        ListPreference lp = new ListPreference(this);
        lp.setEntries(ChangeLocaleUtil.getLocaleNames());
        lp.setEntryValues(ChangeLocaleUtil.getLocaleCodes());
        lp.setTitle(Localization.get("home.menu.locale.change"));
        lp.setKey(PREFS_LOCALE_KEY);
        lp.setDialogTitle(Localization.get("home.menu.locale.select"));
        this.getPreferenceScreen().addPreference(lp);
        updatePreferencesText();
        setTitle("CommCare" + " > " + "Application Preferences");

        createPreferenceOnClickListeners(prefMgr);
    }

    private void createPreferenceOnClickListeners(PreferenceManager prefManager) {
        String[] prefKeys = {
                PREFS_APP_SERVER_KEY,
                PREFS_DATA_SERVER_KEY,
                PREFS_SUBMISSION_URL_KEY,
                PREFS_KEY_SERVER_KEY,
                PREFS_FORM_RECORD_KEY,
                PREFS_UPDATE_FREQUENCY_KEY,
                PREFS_FUZZY_SEARCH_KEY,
                PREFS_LOCALE_KEY };
        String[] analyticsLabels = {
                GoogleAnalyticsFields.LABEL_APP_SERVER,
                GoogleAnalyticsFields.LABEL_DATA_SERVER,
                GoogleAnalyticsFields.LABEL_SUBMISSION_SERVER,
                GoogleAnalyticsFields.LABEL_KEY_SERVER,
                GoogleAnalyticsFields.LABEL_FORM_RECORD_SERVER,
                GoogleAnalyticsFields.LABEL_AUTO_UPDATE,
                GoogleAnalyticsFields.LABEL_FUZZY_SEARCH,
                GoogleAnalyticsFields.LABEL_LOCALE };

        for (int i = 0; i < prefKeys.length; i++) {
            GoogleAnalyticsUtils.createPreferenceOnClickListener(prefManager, prefKeys[i],
                    GoogleAnalyticsFields.CATEGORY_CC_PREFS, analyticsLabels[i]);
        }

        // Create this listener on its own because it has an extra call in it
        Preference pref = prefManager.findPreference(PREFS_PRINT_KEY);
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
                String extension = FileUtils.getExtension(filePath);
                if (extension.equalsIgnoreCase("html")) {
                    SharedPreferences.Editor editor = CommCareApplication._().getCurrentApp().
                            getAppPreferences().edit();
                    editor.putString(PRINT_DOC_LOCATION, filePath);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, CLEAR_USER_DATA, 0, "Clear User Data").setIcon(
                android.R.drawable.ic_menu_delete);
        menu.add(0, MENU_CLEAR_SAVED_SESSION, 1, Localization.get("menu.clear.saved.session"));
        menu.add(0, FORCE_LOG_SUBMIT, 2, "Force Log Submission").setIcon(
                android.R.drawable.ic_menu_upload);
        menu.add(0, RECOVERY_MODE, 3, "Recovery Mode").setIcon(android.R.drawable.ic_menu_report_image);
        menu.add(0, SUPERUSER_PREFS, 4, "Developer Options").setIcon(android.R.drawable.ic_menu_edit);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        GoogleAnalyticsUtils.reportOptionsMenuEntry(GoogleAnalyticsFields.CATEGORY_CC_PREFS);
        menu.findItem(SUPERUSER_PREFS).setVisible(DeveloperPreferences.isSuperuserEnabled());
        menu.findItem(MENU_CLEAR_SAVED_SESSION).setVisible(DevSessionRestorer.savedSessionPresent());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Map<Integer, String> menuIdToAnalyticsEventLabel = createMenuItemToEventMapping();
        GoogleAnalyticsUtils.reportOptionsMenuItemEntry(
                GoogleAnalyticsFields.CATEGORY_CC_PREFS,
                menuIdToAnalyticsEventLabel.get(item.getItemId()));
        switch (item.getItemId()) {
            case CLEAR_USER_DATA:
                CommCareApplication._().clearUserData();
                this.finish();
                return true;
            case FORCE_LOG_SUBMIT:
                CommCareUtil.triggerLogSubmission(this);
                return true;
            case RECOVERY_MODE:
                Intent i = new Intent(this, RecoveryActivity.class);
                this.startActivity(i);
                return true;
            case SUPERUSER_PREFS:
                Intent intent = new Intent(this, DeveloperPreferences.class);
                this.startActivity(intent);
                return true;
            case MENU_CLEAR_SAVED_SESSION:
                DevSessionRestorer.clearSession();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static Map<Integer, String> createMenuItemToEventMapping() {
        Map<Integer, String> menuIdToAnalyticsEvent = new HashMap<>();
        menuIdToAnalyticsEvent.put(CLEAR_USER_DATA, GoogleAnalyticsFields.LABEL_CLEAR_USER_DATA);
        menuIdToAnalyticsEvent.put(FORCE_LOG_SUBMIT, GoogleAnalyticsFields.LABEL_FORCE_LOG_SUBMISSION);
        menuIdToAnalyticsEvent.put(RECOVERY_MODE, GoogleAnalyticsFields.LABEL_RECOVERY_MODE);
        menuIdToAnalyticsEvent.put(SUPERUSER_PREFS, GoogleAnalyticsFields.LABEL_DEVELOPER_OPTIONS);
        menuIdToAnalyticsEvent.put(MENU_CLEAR_SAVED_SESSION, GoogleAnalyticsFields.LABEL_CLEAR_SAVED_SESSION);
        return menuIdToAnalyticsEvent;
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
        switch(key) {
            case PREFS_APP_SERVER_KEY:
                reportEditPreference(GoogleAnalyticsFields.LABEL_APP_SERVER);
                break;
            case PREFS_DATA_SERVER_KEY:
                reportEditPreference(GoogleAnalyticsFields.LABEL_DATA_SERVER);
                break;
            case PREFS_SUBMISSION_URL_KEY:
                reportEditPreference(GoogleAnalyticsFields.LABEL_SUBMISSION_SERVER);
                break;
            case PREFS_KEY_SERVER_KEY:
                reportEditPreference(GoogleAnalyticsFields.LABEL_KEY_SERVER);
                break;
            case PREFS_FORM_RECORD_KEY:
                reportEditPreference(GoogleAnalyticsFields.LABEL_FORM_RECORD_SERVER);
                break;
            case PREFS_UPDATE_FREQUENCY_KEY:
                String freq = sharedPreferences.getString(key, CommCarePreferences.FREQUENCY_NEVER);
                int freqVal;
                if (CommCarePreferences.FREQUENCY_NEVER.equals(freq)) {
                    freqVal = GoogleAnalyticsFields.VALUE_NEVER;
                } else if (CommCarePreferences.FREQUENCY_DAILY.equals(freq)) {
                    freqVal = GoogleAnalyticsFields.VALUE_DAILY;
                } else {
                    freqVal = GoogleAnalyticsFields.VALUE_WEEKLY;
                }
                reportEditPreference(GoogleAnalyticsFields.LABEL_AUTO_UPDATE, freqVal);
                break;
            case PREFS_FUZZY_SEARCH_KEY:
                if (isFuzzySearchEnabled()) {
                    reportEditPreference(GoogleAnalyticsFields.LABEL_FUZZY_SEARCH,
                            GoogleAnalyticsFields.VALUE_ENABLED);
                } else {
                    reportEditPreference(GoogleAnalyticsFields.LABEL_FUZZY_SEARCH,
                            GoogleAnalyticsFields.VALUE_DISABLED);
                }
                break;
            case PREFS_LOCALE_KEY:
                Localization.setLocale(sharedPreferences.getString(key, "default"));
                reportEditPreference(GoogleAnalyticsFields.LABEL_LOCALE);
                break;
            case PREFS_PRINT_KEY:
                reportEditPreference(GoogleAnalyticsFields.LABEL_PRINT_TEMPLATE);
                break;
        }
    }

    private static void reportEditPreference(String label, int value) {
        GoogleAnalyticsUtils.reportEditPref(GoogleAnalyticsFields.CATEGORY_CC_PREFS, label, value);
    }

    private static void reportEditPreference(String label) {
        GoogleAnalyticsUtils.reportEditPref(GoogleAnalyticsFields.CATEGORY_CC_PREFS, label, -1);
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

    public void updatePreferencesText() {
        PreferenceScreen screen = getPreferenceScreen();
        int i;
        for (i = 0; i < screen.getPreferenceCount(); i++) {
            try {
                String key = screen.getPreference(i).getKey();
                String prependedKey = "preferences.title." + key;
                String localizedString = Localization.get(prependedKey);
                screen.getPreference(i).setTitle(localizedString);
            } catch (NoLocalizedTextException nle) {

            }
        }
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
}

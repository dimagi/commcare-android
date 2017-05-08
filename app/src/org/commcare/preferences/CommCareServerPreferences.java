package org.commcare.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.SessionAwarePreferenceActivity;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.GoogleAnalyticsFields;
import org.commcare.google.services.analytics.GoogleAnalyticsUtils;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

/**
 * Sub-menu for managing server addresses
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class CommCareServerPreferences
        extends SessionAwarePreferenceActivity {

    public final static String PREFS_APP_SERVER_KEY = "default_app_server";
    public final static String PREFS_DATA_SERVER_KEY = "ota-restore-url";
    public final static String PREFS_SUBMISSION_URL_KEY = "PostURL";
    public final static String PREFS_LOG_POST_URL_KEY = "log_receiver_url";
    private final static String PREFS_KEY_SERVER_KEY = "default_key_server";
    public final static String PREFS_FORM_RECORD_KEY = "form-record-url";
    public final static String PREFS_HEARTBEAT_URL_KEY = "heartbeat-url";
    public final static String PREFS_SUPPORT_ADDRESS_KEY = "support-email-address";

    private static final Map<String, String> prefKeyToAnalyticsEvent = new HashMap<>();

    static {
        prefKeyToAnalyticsEvent.put(PREFS_APP_SERVER_KEY, GoogleAnalyticsFields.LABEL_APP_SERVER);
        prefKeyToAnalyticsEvent.put(PREFS_DATA_SERVER_KEY, GoogleAnalyticsFields.LABEL_DATA_SERVER);
        prefKeyToAnalyticsEvent.put(PREFS_SUBMISSION_URL_KEY, GoogleAnalyticsFields.LABEL_SUBMISSION_SERVER);
        prefKeyToAnalyticsEvent.put(PREFS_KEY_SERVER_KEY, GoogleAnalyticsFields.LABEL_KEY_SERVER);
        prefKeyToAnalyticsEvent.put(PREFS_FORM_RECORD_KEY, GoogleAnalyticsFields.LABEL_FORM_RECORD_SERVER);
        prefKeyToAnalyticsEvent.put(PREFS_SUPPORT_ADDRESS_KEY, GoogleAnalyticsFields.LABEL_SUPPORT_EMAIL);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(CommCareApplication.instance().getCurrentApp().getPreferencesFilename());
        addPreferencesFromResource(R.xml.server_preferences);

        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_SERVER_PREFS);

        setTitle(Localization.get("settings.server.title"));
        CommCarePreferences.addBackButtonToActionBar(this);

        GoogleAnalyticsUtils.createPreferenceOnClickListeners(prefMgr, prefKeyToAnalyticsEvent,
                GoogleAnalyticsFields.CATEGORY_SERVER_PREFS);
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

    public static String getSupportEmailAddress() {
        return getServerProperty(PREFS_SUPPORT_ADDRESS_KEY, CommCareApplication.instance().getString(R.string.support_email_address_default)) ;
    }

    private static String getServerProperty(String key, String defaultValue) {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        if (app == null) {
            return defaultValue;
        }
        SharedPreferences properties = app.getAppPreferences();
        return properties.getString(key, defaultValue);
    }


}

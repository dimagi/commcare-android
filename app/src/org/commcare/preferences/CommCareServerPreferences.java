package org.commcare.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MenuItem;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.SessionAwarePreferenceActivity;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
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
        extends CommCarePreferenceFragment {

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

    @NonNull
    @Override
    protected String getTitle() {
        return Localization.get("settings.server.title");
    }

    @NonNull
    @Override
    protected String getAnalyticsCategory() {
        return GoogleAnalyticsFields.CATEGORY_SERVER_PREFS;
    }

    @Nullable
    @Override
    protected Map<String, String> getPrefKeyAnalyticsEventMap() {
        return prefKeyToAnalyticsEvent;
    }

    @Nullable
    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return null;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.server_preferences;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // No listeners
    }

    @Override
    protected void setupPrefClickListeners() {
        // No listeners
    }

    @Override
    protected boolean isPersistentAppPreference() {
        return true;
    }

    public static String getSupportEmailAddress() {
        return getServerProperty(PREFS_SUPPORT_ADDRESS_KEY, CommCareApplication.instance().getString(R.string.support_email_address_default)) ;
    }

    public static String getDataServerKey() {
        return getServerProperty(CommCareServerPreferences.PREFS_DATA_SERVER_KEY, CommCareApplication.instance().getString(R.string.ota_restore_url)) ;
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

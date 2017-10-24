package org.commcare.preferences;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.javarosa.core.services.locale.Localization;

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
    public final static String PREFS_HEARTBEAT_URL_KEY = "heartbeat-url";
    public final static String PREFS_SUPPORT_ADDRESS_KEY = "support-email-address";

    @NonNull
    @Override
    protected String getTitle() {
        return Localization.get("settings.server.title");
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

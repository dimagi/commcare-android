package org.commcare.preferences;

import android.content.SharedPreferences;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by amstone326 on 11/14/17.
 */
public class ServerUrls {

    // Server URL settings keys -- All of these values are sent down via the app's profile
    public final static String PREFS_APP_SERVER_KEY = "default_app_server";
    private final static String PREFS_DATA_SERVER_KEY = "ota-restore-url";
    public final static String PREFS_SUBMISSION_URL_KEY = "PostURL";
    public final static String PREFS_LOG_POST_URL_KEY = "log_receiver_url";
    private final static String PREFS_KEY_SERVER_KEY = "key_server";
    public final static String PREFS_HEARTBEAT_URL_KEY = "heartbeat-url";
    public final static String PREFS_RECOVERY_MEASURES_URL_KEY = "recovery-measures-url";
    private final static String PREFS_SUPPORT_ADDRESS_KEY = "support-email-address";

    public static String getSupportEmailAddress() {
        return getServerProperty(PREFS_SUPPORT_ADDRESS_KEY, CommCareApplication.instance()
                .getString(R.string.support_email_address_default));
    }

    public static String getDataServerKey() {
        return getServerProperty(PREFS_DATA_SERVER_KEY, CommCareApplication.instance()
                .getString(R.string.ota_restore_url)) ;
    }

    /**
     * Builds a complete URL by combining the key server URL with the given path.
     *
     * @param path The path to append to the key server URL
     * @return The complete URL
     * @throws IllegalArgumentException if path is null or key server URL is not set
     * @throws MalformedURLException if the resulting URL is invalid
     */
    public static String buildEndpoint(String path) throws MalformedURLException {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        String keyServer = getKeyServer();
        if (keyServer == null) {
            throw new IllegalArgumentException("Key server URL is not set");
        }
        try {
            URL originalUrl = new URL(keyServer);
            return new URL(originalUrl, path).toString();
        } catch (MalformedURLException e) {
            throw e;
        }
    }

    public static String getKeyServer() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getString(PREFS_KEY_SERVER_KEY, null);
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

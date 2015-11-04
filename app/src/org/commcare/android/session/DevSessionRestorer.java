package org.commcare.android.session;

import android.content.SharedPreferences;
import android.util.Pair;

import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;

/**
 * Logic to save password and auto-login when dev option is enabled
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DevSessionRestorer {

    /**
     * @return Username and password of last login; null if auto-login not
     * enabled
     */
    public static Pair<String, String> getAutoLoginCreds() {
        if (autoLoginEnabled()) {
            SharedPreferences prefs =
                    CommCareApplication._().getCurrentApp().getAppPreferences();
            String lastUser =
                    prefs.getString(CommCarePreferences.LAST_LOGGED_IN_USER, "");
            String lastPass =
                    prefs.getString(CommCarePreferences.LAST_PASSWORD, "");

            if (!"".equals(lastPass)) {
                return new Pair<>(lastUser, lastPass);
            }
        }

        return null;
    }

    /**
     * Save password into app preferences if auto-login is enabled
     */
    public static void tryAutoLoginPasswordSave(String password) {
        if (autoLoginEnabled()) {
            SharedPreferences prefs =
                    CommCareApplication._().getCurrentApp().getAppPreferences();
            prefs.edit().putString(CommCarePreferences.LAST_PASSWORD, password).commit();
        }
    }

    private static boolean autoLoginEnabled() {
        return BuildConfig.DEBUG && DeveloperPreferences.isAutoLoginEnabled();
    }
}

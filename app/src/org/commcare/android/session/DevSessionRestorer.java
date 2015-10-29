package org.commcare.android.session;

import android.content.SharedPreferences;

import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.activities.LoginActivity;
import org.commcare.dalvik.preferences.CommCarePreferences;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DevSessionRestorer {

    public static boolean autoLogin(LoginActivity loginActivity, SharedPreferences prefs) {
        if (BuildConfig.DEBUG) {
            String lastUser = prefs.getString(CommCarePreferences.LAST_LOGGED_IN_USER, "");
            String lastPass = prefs.getString(CommCarePreferences.LAST_PASSWORD, "");

            if (!"".equals(lastPass)) {
                loginActivity.performUILogin(lastUser, lastPass);
                return true;
            }
        }

        return false;
    }

    public static void showAutoLoginBox() {
    }

    public static void saveAutoLoginPassword(SharedPreferences prefs, String password) {
        prefs.edit().putString(CommCarePreferences.LAST_PASSWORD, password).commit();
    }
}

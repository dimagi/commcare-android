package org.commcare.android.util;

import android.content.SharedPreferences;

import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.dalvik.preferences.CommCarePreferences;

import java.util.Date;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class InstallAndUpdateUtils {
    public static void recordUpdateAttempt(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(CommCarePreferences.LAST_UPDATE_ATTEMPT, new Date().getTime());
        editor.commit();
    }

    public static void updateProfileRef(SharedPreferences prefs, String authRef, String profileRef) {
        SharedPreferences.Editor edit = prefs.edit();
        if (authRef != null) {
            edit.putString(ResourceEngineTask.DEFAULT_APP_SERVER, authRef);
        } else {
            edit.putString(ResourceEngineTask.DEFAULT_APP_SERVER, profileRef);
        }
        edit.commit();
    }
}

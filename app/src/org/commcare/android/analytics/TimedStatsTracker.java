package org.commcare.android.analytics;

import android.content.SharedPreferences;

import org.commcare.dalvik.application.CommCareApplication;
import org.joda.time.DateTime;

/**
 * Created by amstone326 on 12/3/15.
 */
public class TimedStatsTracker {

    private static String KEY_LAST_FORM_NAME = "last-form-name";
    private static String KEY_LAST_FORM_START_TIME = "last-form-start-time";

    public static void registerFormStart(String formName, long timeInMs) {
        SharedPreferences.Editor editor =
                CommCareApplication._().getCurrentApp().getAppPreferences().edit();
        editor.putString(KEY_LAST_FORM_NAME, formName).
                putLong(KEY_LAST_FORM_START_TIME, timeInMs);
        editor.commit();
    }

}

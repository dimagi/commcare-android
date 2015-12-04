package org.commcare.android.analytics;

import android.content.SharedPreferences;
import android.util.Log;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;

import java.util.Date;

/**
 * Created by amstone326 on 12/3/15.
 */
public class TimedStatsTracker {

    private static final String TAG = TimedStatsTracker.class.getSimpleName();

    private static String KEY_LAST_FORM_NAME = "last-form-name";
    private static String KEY_LAST_FORM_START_TIME = "last-form-start-time";
    private static String KEY_LAST_SESSION_START_TIME = "last-session-start-time";

    public static void registerEnterForm(String formTitle) {
        SharedPreferences.Editor editor =
                CommCareApplication._().getCurrentApp().getAppPreferences().edit();
        editor.putString(KEY_LAST_FORM_NAME, formTitle).
                putLong(KEY_LAST_FORM_START_TIME, currentTime());
        editor.commit();
    }

    public static void registerExitForm(String formTitle) {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        long enterTime = prefs.getLong(KEY_LAST_FORM_START_TIME, -1);
        if (enterTime != -1) {
            String formLastEntered = prefs.getString(KEY_LAST_FORM_NAME, "");
            if (formLastEntered.equals(formTitle)) {
                reportTimedEvent(GoogleAnalyticsFields.ACTION_TIME_IN_A_FORM, formTitle,
                        computeElapsedTimeInSeconds(enterTime, currentTime()));
            } else {
                Log.i(TAG, "Attempting to log exit form time for a different form than the last " +
                        "form logged as entered");
            }
        } else {
            Log.i(TAG, "Attempting to log exit form time when there was no start form time " +
                    "saved in prefs");
        }
    }

    public static void registerStartSession() {
        SharedPreferences.Editor editor =
                CommCareApplication._().getCurrentApp().getAppPreferences().edit();
        editor.putLong(KEY_LAST_SESSION_START_TIME, (new Date()).getTime());
    }

    public static void registerEndSession() {

    }

    private static int computeElapsedTimeInSeconds(long startTime, long endTime) {
        return (int) (endTime - startTime / 1000);
    }

    private static void reportTimedEvent(String action, String label, int value) {
        GoogleAnalyticsUtils.reportTimedEvent(action, label, value);
    }

    private static void reportTimedEvent(String action, int value) {
        reportTimedEvent(action, "", value);
    }

    private static long currentTime() {
        return (new Date()).getTime();
    }

}

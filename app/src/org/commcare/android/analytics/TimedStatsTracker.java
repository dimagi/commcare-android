package org.commcare.android.analytics;

import android.content.SharedPreferences;
import android.util.Log;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;

import java.util.Date;

/**
 * Utils for keeping track of certain app events and then reporting them to google analytics
 *
 * @author amstone
 */
public class TimedStatsTracker {

    private static final String TAG = TimedStatsTracker.class.getSimpleName();

    private static String KEY_LAST_FORM_NAME = "last-form-name";
    private static String KEY_LAST_FORM_START_TIME = "last-form-start-time";
    private static String KEY_LAST_SESSION_START_TIME = "last-session-start-time";
    private static String KEY_LAST_LOGGED_IN_USER = "last-logged-in-user";

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
                Log.i(TAG, "Attempting to report exit form time for a different form than the " +
                        "last form logged as entered");
            }
        } else {
            Log.i(TAG, "Attempting to report exit form time when there was no start form time " +
                    "saved in prefs");
        }
    }

    public static void registerStartSession() {
        SharedPreferences.Editor editor =
                CommCareApplication._().getCurrentApp().getAppPreferences().edit();
        String currentUserId = CommCareApplication._().getCurrentUserId();
        if (!"".equals(currentUserId)) {
            editor.putLong(KEY_LAST_SESSION_START_TIME, currentTime())
                    .putString(KEY_LAST_LOGGED_IN_USER, currentUserId);
            editor.commit();
        } else {
            Log.i(TAG, "Attempting to report starting a session with no logged in user available");
        }
    }

    public static void registerEndSession(String loggedOutUser) {
        if (loggedOutUser == null || "".equals(loggedOutUser)) {
            Log.i(TAG, "Attempting to report ending a session with no logged in user available");
            return;
        }
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String lastLoggedInUser = prefs.getString(KEY_LAST_LOGGED_IN_USER, "");
        if (!"".equals(lastLoggedInUser)) {
            if (lastLoggedInUser.equals(loggedOutUser)) {
                long startTime = prefs.getLong(KEY_LAST_SESSION_START_TIME, -1);
                reportTimedEvent(GoogleAnalyticsFields.ACTION_SESSION_LENGTH,
                        computeElapsedTimeInSeconds(startTime, currentTime()));
            } else {
                Log.i(TAG, "Attempting to report ending a session for a different user than the " +
                        "last user reported as logged in");
            }
        } else {
            Log.i(TAG, "Attempting to report ending a session when there is no last logged in " +
                    "user in prefs");
        }
    }

    private static int computeElapsedTimeInSeconds(long startTime, long endTime) {
        return (int) (endTime - startTime / 1000);
    }

    private static long currentTime() {
        return (new Date()).getTime();
    }

    /**
     * Report the completion of a timed event and its length
     *
     * @param eventSpecificLabel - some piece of information specific to the given timed event,
     *                           possibly empty
     */
    private static void reportTimedEvent(String timedEvent, String eventSpecificLabel,
                                         int timeInSeconds) {
        GoogleAnalyticsUtils.reportTimedEvent(timedEvent, eventSpecificLabel, timeInSeconds);
    }

    private static void reportTimedEvent(String timedEvent, int timeInSeconds) {
        reportTimedEvent(timedEvent, "", timeInSeconds);
    }

}

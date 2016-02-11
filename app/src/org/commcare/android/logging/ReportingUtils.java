package org.commcare.android.logging;

import android.content.SharedPreferences;
import android.support.v4.util.Pair;

import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.util.SessionStateUninitException;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Profile;

/**
 * Created by amstone326 on 2/11/16.
 */
public class ReportingUtils {

    protected static int getAppBuildNumber() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app != null) {
            Profile profile = app.getCommCarePlatform().getCurrentProfile();
            return profile.getVersion();
        }
        return -1;
    }

    protected static String getAppId() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        if (app != null) {
            Profile profile = app.getCommCarePlatform().getCurrentProfile();
            return profile.getUniqueId();
        }
        return "";
    }

    protected static String getCurrentSession() {
        CommCareSession currentSession;
        try {
            currentSession = CommCareApplication._().getCurrentSession();
            return currentSession.getFrame().toString();
        } catch (SessionStateUninitException e) {
            return "";
        }
    }

    /*
     * Helper methods for ACRA and user reporting. Catch broad exception so we never crash
     * when trying to file a bug.
     */

    public static String getDomain() {
        try {
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            return prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        } catch (Exception e) {
            return "Domain not set.";
        }
    }

    public static String getPostURL() {
        try {
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            return prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        } catch (Exception e) {
            return "PostURL not set.";
        }
    }

    public static String getUser() {
        try {
            return CommCareApplication._().getSession().getLoggedInUser().getUsername();
        } catch (Exception e) {
            return "User not logged in.";
        }
    }

    public static String getVersion() {
        try {
            return CommCareApplication._().getCurrentVersionString();
        } catch (Exception e) {
            return "Version not set.";
        }
    }

}

package org.commcare.android.logging;

import android.content.SharedPreferences;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Profile;
import org.commcare.utils.SessionStateUninitException;

/**
 * Created by amstone326 on 2/11/16.
 */
public class ReportingUtils {

    public static int getAppBuildNumber() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        if (app != null) {
            Profile profile = app.getCommCarePlatform().getCurrentProfile();
            if (profile != null) {
                return profile.getVersion();
            }
        }
        return -1;
    }

    public static String getAppId() {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();
        if (app != null) {
            Profile profile = app.getCommCarePlatform().getCurrentProfile();
            if (profile != null) {
                return profile.getUniqueId();
            }
        }
        return "";
    }

    public static String getCurrentSession() {
        CommCareSession currentSession;
        try {
            currentSession = CommCareApplication.instance().getCurrentSession();
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
            SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
            return prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        } catch (Exception e) {
            return "Domain not set.";
        }
    }

    public static String getPostURL() {
        try {
            SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
            return prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, "not found");
        } catch (Exception e) {
            return "PostURL not set.";
        }
    }

    public static String getUser() {
        try {
            return CommCareApplication.instance().getSession().getLoggedInUser().getUsername();
        } catch (Exception e) {
            return "User not logged in.";
        }
    }

    public static String getVersion() {
        try {
            return AppUtils.getCurrentVersionString();
        } catch (Exception e) {
            return "Version not set.";
        }
    }

}

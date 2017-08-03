package org.commcare.android.logging;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.preferences.CommCarePreferences;
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
        try {
            CommCareApp app = CommCareApplication.instance().getCurrentApp();
            if (app != null) {
                Profile profile = app.getCommCarePlatform().getCurrentProfile();
                if (profile != null) {
                    return profile.getUniqueId();
                }
            }
            return "";
        } catch (NullPointerException npe) {
            // don't fail hard, return empty string
            return "";
        }
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
            String domain = CommCarePreferences.getUserDomain();
            if (domain == null) {
                domain = "not found";
            }
            return domain;
        } catch (Exception e) {
            return "Domain not set.";
        }
    }

    public static String getPostURL() {
        try {
            String domain = CommCarePreferences.getUserDomain();
            if (domain == null) {
                domain = "not found";
            }
            return domain;
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

    public static String getCommCareVersionString() {
        Context c = CommCareApplication.instance();
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

}

package org.commcare.android.logging;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.connect.ConnectIDManager;
import org.commcare.dalvik.BuildConfig;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Profile;
import org.commcare.utils.SessionStateUninitException;
import org.commcare.utils.UrlUtils;

import java.net.URL;

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
            return AppUtils.getCurrentAppId();
        } catch (Exception e) {
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
     * Helper methods for crash and user reporting. Catch broad exception so we never crash
     * when trying to file a bug.
     */

    public static String getDomain() {
        try {
            String domain = HiddenPreferences.getUserDomain();
            if (domain == null) {
                domain = "";
            }
            return domain;
        } catch (Exception e) {
            return "";
        }
    }

    public static String getPostURL() {
        try {
            String domain = HiddenPreferences.getUserDomain();
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
            return "";
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
        return BuildConfig.VERSION_NAME;
    }

    public static int getAppVersion() {
        try {
            return CommCareApplication.instance().getCurrentApp().getAppRecord().getVersionNumber();
        } catch (Exception e) {
            return -1;
        }
    }

    public static String getAppName() {
        try {
            return CommCareApplication.instance().getCurrentApp().getAppRecord().getDisplayName();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getDeviceId() {
        return CommCareApplication.instance().getPhoneId();
    }

    public static String getServerName() {
        try {
            String keyServer = ServerUrls.getKeyServer();
            if (keyServer != null) {
                URL url = new URL(keyServer);
                return url.getHost();
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }

    public static String getAppBuildProfileId() {
        try {
            return CommCareApplication.instance().getCommCarePlatform().getCurrentProfile().getBuildProfileId();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getUserForCrashes() {
        String user = getUser();
        if(user.isEmpty()) {
            try {
                if (ConnectIDManager.getInstance().isloggedIn()) {
                    return ConnectIDManager.getInstance().getUser(CommCareApplication.instance()).getUserId();
                }
            } catch (Exception ignored) {
            }
        }

        return "";
    }
}

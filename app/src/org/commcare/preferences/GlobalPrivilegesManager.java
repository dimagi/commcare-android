package org.commcare.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.google.services.analytics.GoogleAnalyticsUtils;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.AppAvailableForInstall;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Manages privileges/authentications that are global to a device running CommCare,
 * rather than a specific app
 *
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class GlobalPrivilegesManager {

    private static final String GLOBAL_PRIVELEGES_FILENAME = "global-preferences-filename";

    private static final String RETRIEVED_AVAILABLE_APPS = "available-apps-already-retrieved";

    public static final String PRIVILEGE_MULTIPLE_APPS = "multiple_apps_unlimited";

    public static final ArrayList<String> allGlobalPrivilegesList = new ArrayList<>();
    static {
        allGlobalPrivilegesList.add(PRIVILEGE_MULTIPLE_APPS);
    }

    private static SharedPreferences getGlobalPrivilegesRecord() {
        return CommCareApplication.instance().getSharedPreferences(GLOBAL_PRIVELEGES_FILENAME,
                Context.MODE_PRIVATE);
    }

    /**
     * @param username - the HQ web user associated with the privilege being granted
     */
    public static void enablePrivilege(String privilegeName, String username) {
        getGlobalPrivilegesRecord().edit().putBoolean(privilegeName, true).commit();
        GoogleAnalyticsUtils.reportPrivilegeEnabled(privilegeName, username);
    }

    public static void disablePrivilege(String privilegeName) {
        getGlobalPrivilegesRecord().edit().putBoolean(privilegeName, false).commit();
    }

    public static ArrayList<String> getEnabledPrivileges() {
        ArrayList<String> privilegesEnabled = new ArrayList<>();
        for (String privilege : allGlobalPrivilegesList) {
            if (isPrivilegeEnabled(privilege)) {
                privilegesEnabled.add(privilege);
            }
        }
        return privilegesEnabled;
    }

    public static String getEnabledPrivilegesString() {
        StringBuilder builder = new StringBuilder();
        for (String privilege : getEnabledPrivileges()) {
            builder.append("- ").append(getPrivilegeDisplayName(privilege)).append("\n");
        }
        return builder.toString();
    }

    private static boolean isPrivilegeEnabled(String privilegeName) {
        return getGlobalPrivilegesRecord().getBoolean(privilegeName, false);
    }

    public static boolean isMultipleAppsPrivilegeEnabled() {
        return isPrivilegeEnabled(PRIVILEGE_MULTIPLE_APPS);
    }

    private static String getPrivilegeDisplayName(String privilegeName) {
        switch(privilegeName) {
            case PRIVILEGE_MULTIPLE_APPS:
                return "Unlimited Multiple App Install";
            default:
                return "";
        }
    }

    public static void storeRetrievedAvailableApps(Vector<AppAvailableForInstall> availableAppsRetrieved) {
        if (availableAppsRetrieved != null && availableAppsRetrieved.size() > 0) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream serializedStream = new DataOutputStream(baos);
            try {
                ExtUtil.write(serializedStream, new ExtWrapList(availableAppsRetrieved));
                String serializedAppsList = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
                getGlobalPrivilegesRecord().edit()
                        .putString(RETRIEVED_AVAILABLE_APPS, serializedAppsList).commit();
                serializedStream.close();
            } catch (IOException e) {
            }
        }
    }

    public static Vector<AppAvailableForInstall> restorePreviouslyRetrievedAvailableApps() {
        String serializedAppsList = getGlobalPrivilegesRecord().getString(RETRIEVED_AVAILABLE_APPS, null);
        if (serializedAppsList != null) {
            try {
                byte[] appListBytes = Base64.decode(serializedAppsList, Base64.DEFAULT);
                DataInputStream stream = new DataInputStream(new ByteArrayInputStream(appListBytes));

                Vector<AppAvailableForInstall> previouslyRetrievedApps =
                        (Vector<AppAvailableForInstall>) ExtUtil.read(stream,
                                new ExtWrapList((AppAvailableForInstall.class)), ExtUtil.defaultPrototypes());
                return previouslyRetrievedApps;
            } catch (Exception e) {
                // Something went wrong, so clear out whatever is there
                clearPreviouslyRetrivedApps();
            }
        }
        return null;
    }

    public static void clearPreviouslyRetrivedApps() {
        getGlobalPrivilegesRecord().edit().putString(RETRIEVED_AVAILABLE_APPS, null).commit();
    }

}

package org.commcare.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import org.commcare.CommCareApplication;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;

import java.util.ArrayList;

/**
 * Manages privileges/authentications that are global to a device running CommCare,
 * rather than a specific app
 *
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class GlobalPrivilegesManager {

    private static final String GLOBAL_SETTINGS_FILENAME = "global-preferences-filename";

    public static final String PRIVILEGE_MULTIPLE_APPS = "multiple_apps_unlimited";
    public static final String PRIVILEGE_ADVANCED_SETTINGS = "advanced_settings_access";

    public static final ArrayList<String> allGlobalPrivilegesList = new ArrayList<>();
    static {
        allGlobalPrivilegesList.add(PRIVILEGE_MULTIPLE_APPS);
        allGlobalPrivilegesList.add(PRIVILEGE_ADVANCED_SETTINGS);
    }

    public static SharedPreferences getGlobalPrefsRecord() {
        return CommCareApplication.instance().getSharedPreferences(GLOBAL_SETTINGS_FILENAME,
                Context.MODE_PRIVATE);
    }

    /**
     * @param username - the HQ web user associated with the privilege being granted
     */
    public static void enablePrivilege(String privilegeName, String username) {
        getGlobalPrefsRecord().edit().putBoolean(privilegeName, true).commit();
        FirebaseAnalyticsUtil.reportPrivilegeEnabled(privilegeName, username);
    }

    public static void disablePrivilege(String privilegeName) {
        getGlobalPrefsRecord().edit().putBoolean(privilegeName, false).commit();
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
        return getGlobalPrefsRecord().getBoolean(privilegeName, false);
    }

    public static boolean isMultipleAppsPrivilegeEnabled() {
        return isPrivilegeEnabled(PRIVILEGE_MULTIPLE_APPS);
    }
    
    public static boolean isAdvancedSettingsAccessEnabled() {
        return isPrivilegeEnabled(PRIVILEGE_ADVANCED_SETTINGS);
    }

    private static String getPrivilegeDisplayName(String privilegeName) {
        switch(privilegeName) {
            case PRIVILEGE_MULTIPLE_APPS:
                return "Unlimited Multiple App Install";
            case PRIVILEGE_ADVANCED_SETTINGS:
                return "Advanced Settings Access";
            default:
                return "";
        }
    }

}

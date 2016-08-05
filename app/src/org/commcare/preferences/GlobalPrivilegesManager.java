package org.commcare.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.utils.EncryptionUtils;

import java.util.ArrayList;

/**
 * Manages privileges that are global to a device running CommCare, rather than a specific app
 *
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class GlobalPrivilegesManager {

    private static final String GLOBAL_PRIVELEGES_FILENAME = "global-preferences-filename";

    public static final String PRIVILEGE_MULTIPLE_APPS = "multiple_apps_unlimited";

    public static final ArrayList<String> allGlobalPrivilegesList = new ArrayList<>();
    static {
        allGlobalPrivilegesList.add(PRIVILEGE_MULTIPLE_APPS);
    }

    private static SharedPreferences getGlobalPrivilegesRecord() {
        return CommCareApplication._().getSharedPreferences(GLOBAL_PRIVELEGES_FILENAME,
                Context.MODE_PRIVATE);
    }

    /**
     * @param username - the HQ web user associated with the privilege being granted
     */
    public static void enablePrivilege(String privilegeName, String username) {
        getGlobalPrivilegesRecord().edit().putBoolean(privilegeName, true).commit();
        GoogleAnalyticsUtils.reportPrivilegeEnabled(privilegeName,
                EncryptionUtils.getMD5HashAsString(username));
    }

    public static void disablePrivilege(String privilegeName) {
        getGlobalPrivilegesRecord().edit().putBoolean(privilegeName, false).commit();
    }

    public static boolean isPrivilegeEnabled(String privilegeName) {
        return getGlobalPrivilegesRecord().getBoolean(privilegeName, false);
    }

    public static boolean isSuperuserPrivilegeEnabled() {
        return isPrivilegeEnabled(PRIVILEGE_MULTIPLE_APPS);
    }

    public static String getPrivilegeDisplayName(String privilegeName) {
        switch(privilegeName) {
            case PRIVILEGE_MULTIPLE_APPS:
                return "multiple apps";
            default:
                return "";
        }
    }

    public static int getInstructionsTextId(String privilegeName) {
        switch(privilegeName) {
            case PRIVILEGE_MULTIPLE_APPS:
                return R.string.multiple_apps_privilege_instructions;
            default:
                return -1;
        }
    }
}

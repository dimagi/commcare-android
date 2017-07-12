package org.commcare;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.suite.model.Profile;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.EntityFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Utils for getting info about installed apps, initializing them, and uninstalling them.
 */
public class AppUtils {
    /**
     * @return all ApplicationRecords in storage, regardless of their status, in alphabetical order
     */
    public static ArrayList<ApplicationRecord> getInstalledAppRecords() {
        ArrayList<ApplicationRecord> records = new ArrayList<>();
        for (ApplicationRecord r : CommCareApplication.instance().getGlobalStorage(ApplicationRecord.class)) {
            records.add(r);
        }
        Collections.sort(records, new Comparator<ApplicationRecord>() {

            @Override
            public int compare(ApplicationRecord lhs, ApplicationRecord rhs) {
                return lhs.getDisplayName().compareTo(rhs.getDisplayName());
            }

        });
        return records;
    }

    /**
     * @param uniqueId - the uniqueId of the ApplicationRecord being sought
     * @return the ApplicationRecord corresponding to the given id, if it exists. Otherwise,
     * return null
     */
    public static ApplicationRecord getAppById(String uniqueId) {
        for (ApplicationRecord r : getInstalledAppRecords()) {
            if (r.getUniqueId().equals(uniqueId)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Initializes the first "usable" application from the list of globally installed app records,
     * if there is one
     */
    public static void initFirstUsableAppRecord() {
        for (ApplicationRecord record : MultipleAppsUtil.getUsableAppRecords()) {
            CommCareApplication.instance().initializeAppResources(new CommCareApp(record));
            break;
        }
    }

    /**
     * Check if any existing apps were left in a partially deleted state, and finish
     * uninstalling them if so.
     */
    static void checkForIncompletelyUninstalledApps() {
        for (ApplicationRecord record : CommCareApplication.instance().getGlobalStorage(ApplicationRecord.class)) {
            if (record.getStatus() == ApplicationRecord.STATUS_DELETE_REQUESTED) {
                try {
                    CommCareApplication.instance().uninstall(record);
                } catch (RuntimeException e) {
                    Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "Unable to uninstall an app " +
                            "during startup that was previously left partially-deleted");
                }
            }
        }
    }

    /**
     * Assumes that there is an active session when it is called, and wipes out all local user
     * data (users, referrals, etc) for the user with an active session, but leaves application
     * resources in place.
     *
     * It makes no attempt to make sure this is a safe operation when called, so
     * it shouldn't be used lightly.
     */
    public static void clearUserData() {
        wipeSandboxForUser(CommCareApplication.instance().getSession().getLoggedInUser().getUsername());
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putString(CommCarePreferences.LAST_LOGGED_IN_USER, null).commit();
        CommCareApplication.instance().closeUserSession();
    }

    public static void wipeSandboxForUser(final String username) {
        // manually clear file-backed fixture storage to ensure files are removed
        try {
            CommCareApplication.instance().getFileBackedUserStorage("fixture", FormInstance.class).removeAll();
        } catch (SessionUnavailableException e) {
            // this will sometimes get called from outside of a session; we want to proceed with
            // the other parts of wiping the sandbox that haven't already been done by logging out
        }

        // wipe the user's db
        final Set<String> dbIdsToRemove = new HashSet<>();
        CommCareApplication.instance().getAppStorage(UserKeyRecord.class).removeAll(new EntityFilter<UserKeyRecord>() {
            @Override
            public boolean matches(UserKeyRecord ukr) {
                if (ukr.getUsername().equalsIgnoreCase(username.toLowerCase())) {
                    dbIdsToRemove.add(ukr.getUuid());
                    return true;
                }
                return false;
            }
        });
        for (String id : dbIdsToRemove) {
            CommCareApplication.instance().getDatabasePath(DatabaseUserOpenHelper.getDbName(id)).delete();
        }
    }

    public static String getCurrentVersionString() {
        CommCareApplication application = CommCareApplication.instance();
        PackageManager pm = application.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(application.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "ERROR! Incorrect package version requested";
        }
        int[] versions = application.getCommCareVersion();
        String ccv = "";
        for (int vn : versions) {
            if (!"".equals(ccv)) {
                ccv += ".";
            }
            ccv += vn;
        }

        Profile p = CommCareApplication.instance().getCurrentApp() == null ? null : CommCareApplication.instance().getCommCarePlatform().getCurrentProfile();
        String profileVersion = "";
        if (p != null) {
            profileVersion = String.valueOf(p.getVersion());
        }
        String buildDate = BuildConfig.BUILD_DATE;
        String buildNumber = BuildConfig.BUILD_NUMBER;

        return Localization.get(application.getString(R.string.app_version_string), new String[]{pi.versionName, String.valueOf(pi.versionCode), ccv, buildNumber, buildDate, profileVersion});
    }
}

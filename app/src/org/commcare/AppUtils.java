package org.commcare;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.heartbeat.ApkVersion;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.models.database.HybridFileBackedSqlStorage;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.suite.model.Profile;
import org.commcare.util.LogTypes;
import org.commcare.utils.AppLifecycleUtils;
import org.commcare.utils.FileUtil;
import org.commcare.utils.MultipleAppsUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.EntityFilter;

import java.io.File;
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
        Collections.sort(records, (lhs, rhs) -> lhs.getDisplayName().compareTo(rhs.getDisplayName()));
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
                    AppLifecycleUtils.uninstall(record);
                } catch (RuntimeException e) {
                    Logger.log(LogTypes.TYPE_ERROR_STORAGE, "Unable to uninstall an app " +
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
        wipeSandboxForUser(getLoggedInUserName());
        CommCareApplication.instance().getCurrentApp().getAppPreferences().edit()
                .putString(HiddenPreferences.LAST_LOGGED_IN_USER, null).apply();
        CommCareApplication.instance().closeUserSession();
    }

    /**
     * Deletes the db sandbox for the given user; this IS safe to execute even if the user in
     * question is not currently logged in
     */
    public static void wipeSandboxForUser(final String username) {
        DataChangeLogger.log(new DataChangeLog.WipeUserSandbox());
        // Get the uuids that match this username
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

        // Wipe the file-backed fixture storage for all matching UKRs
        wipeFileBackedFixtureStorage(dbIdsToRemove);

        // Wipe the user db for all matching UKRs
        for (String id : dbIdsToRemove) {
            CommCareApplication.instance().getDatabasePath(DatabaseUserOpenHelper.getDbName(id)).delete();
        }
    }

    private static void wipeFileBackedFixtureStorage(Set<String> matchingUkrUuids) {
        for (String ukrUuid : matchingUkrUuids) {
            File fixtureStorageFile = HybridFileBackedSqlStorage.getStorageFile(ukrUuid,
                    HybridFileBackedSqlStorage.FIXTURE_STORAGE_TABLE_NAME,
                    CommCareApplication.instance().getCurrentApp());
            FileUtil.deleteFileOrDir(fixtureStorageFile);
        }
    }

    // Returns CommCare version without app version info
    public static String getCommCareVersionString() {
        return Localization.get("commcare.version",
                new String[]{BuildConfig.VERSION_NAME, String.valueOf(BuildConfig.VERSION_CODE), BuildConfig.BUILD_DATE});
    }

    public static String getCurrentVersionString() {
        CommCareApplication application = CommCareApplication.instance();
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

        String appVersionTag = HiddenPreferences.getAppVersionTag();
        if (!TextUtils.isEmpty(appVersionTag)) {
            profileVersion += " (" + appVersionTag + ")";
        }

        String buildDate = BuildConfig.BUILD_DATE;
        String buildNumber = BuildConfig.BUILD_NUMBER;

        return Localization.get(application.getString(R.string.app_version_string), new String[]{
                ccv, String.valueOf(BuildConfig.VERSION_CODE), buildNumber, buildDate, profileVersion});
    }

    public static String getCurrentAppId() {
        return CommCareApplication.instance()
                .getCurrentApp()
                .getUniqueId();
    }

    /**
     * @return version number of the currently seated app
     */
    public static int getCurrentAppVersion() {
        return CommCareApplication.instance().getCurrentApp().getAppRecord().getVersionNumber();
    }

    public static boolean notOnLatestAppVersion() {
        return getCurrentAppVersion() < HiddenPreferences.getLatestAppVersion();
    }

    public static boolean notOnLatestCCVersion() {
        return new ApkVersion(ReportingUtils.getCommCareVersionString()).compareTo(
                new ApkVersion(HiddenPreferences.getLatestCommcareVersion())) < 0;
    }

    public static String getLoggedInUserName() {
        return CommCareApplication.instance().getSession().getLoggedInUser().getUsername();
    }
}

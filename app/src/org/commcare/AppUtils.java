package org.commcare;

import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.utils.MultipleAppsUtil;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.EntityFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

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
        CommCareApplication.instance().getFileBackedUserStorage("fixture", FormInstance.class).removeAll();

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
}

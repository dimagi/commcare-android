package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.suite.model.SignedPermission;

import java.util.ArrayList;

/**
 * Utility methods associated with multiple app seating functionality.
 *
 * @author Aliza Stone (astone@dimagi.com)
 */
public class MultipleAppsUtil {

    /**
     * @return all ApplicationRecords that have status installed and are NOT archived
     */
    private static ArrayList<ApplicationRecord> getVisibleAppRecords() {
        ArrayList<ApplicationRecord> visible = new ArrayList<>();
        for (ApplicationRecord r : CommCareApplication._().getInstalledAppRecords()) {
            if (r.isVisible()) {
                visible.add(r);
            }
        }
        return visible;
    }

    /**
     * @return all ApplicationRecords that are installed AND are not archived AND have MM verified
     */
    public static ArrayList<ApplicationRecord> getUsableAppRecords() {
        ArrayList<ApplicationRecord> ready = new ArrayList<>();
        for (ApplicationRecord r : CommCareApplication._().getInstalledAppRecords()) {
            if (r.isUsable()) {
                ready.add(r);
            }
        }
        return ready;
    }

    /**
     * @return whether the user should be sent to CommCareVerificationActivity. Current logic is
     * that this should occur only if there is exactly one visible app and it is missing its MM
     * (because we are then assuming the user is not currently using multiple apps functionality)
     */
    public static boolean shouldSeeMMVerification() {
        return getVisibleAppRecords().size() == 1 && getUsableAppRecords().size() == 0;
    }

    public static boolean usableAppsPresent() {
        return getUsableAppRecords().size() > 0;
    }

    /**
     * @return the list of all installed apps as an array
     */
    public static ApplicationRecord[] appRecordArray() {
        ArrayList<ApplicationRecord> appList = CommCareApplication._().getInstalledAppRecords();
        ApplicationRecord[] appArray = new ApplicationRecord[appList.size()];
        int index = 0;
        for (ApplicationRecord r : appList) {
            appArray[index++] = r;
        }
        return appArray;
    }

    /**
     * @param uniqueId - the uniqueId of the ApplicationRecord being sought
     * @return the ApplicationRecord corresponding to the given id, if it exists. Otherwise,
     * return null
     */
    public static ApplicationRecord getAppById(String uniqueId) {
        for (ApplicationRecord r : CommCareApplication._().getInstalledAppRecords()) {
            if (r.getUniqueId().equals(uniqueId)) {
                return r;
            }
        }
        return null;
    }

    /**
     * @return Is the installation of a new app (that is NOT marked as "ignore", since these can
     * always be installed no matter what) allowed in the current environment?
     */
    public static boolean appInstallationAllowed() {
        for (ApplicationRecord record : CommCareApplication._().getInstalledAppRecords()) {
            // TODO: This check is a bit overkill in the present state, because currently it's not
            // possible that an installed app would have this value, unless it were the only app
            // installed. However, if/once we are pinging the server for this value, it could have changed
            if (record.getMultipleAppsCompatibility().equals(SignedPermission.MULT_APPS_DISABLED_VALUE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return Would an app that we are attempting to install need to be multiple-apps compatible
     * in order to be installable in the current environment?
     */
    public static boolean multipleAppsCompatibilityRequiredForInstall() {
        for (ApplicationRecord record : CommCareApplication._().getInstalledAppRecords()) {
            // TODO: in all likelihood we will change this so that for each app, we first ping
            // something on the server to check if there is an updated value, and then fall
            // back to the value that's already in the ApplicationRecord if that's unavailable
            if (!record.getMultipleAppsCompatibility().equals(SignedPermission.MULT_APPS_IGNORE_VALUE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return Does the new version of an app that we are attempting to upgrade need to be
     * multiple-apps compatible in order for the update to be allowed?
     */
    public static boolean multipleAppsCompatibilityRequiredForUpgrade(String idOfAppBeingUpgraded) {
        for (ApplicationRecord record : CommCareApplication._().getInstalledAppRecords()) {
            if (!record.getUniqueId().equals(idOfAppBeingUpgraded) &&
                    !record.getMultipleAppsCompatibility().equals(SignedPermission.MULT_APPS_IGNORE_VALUE)) {
                return true;
            }
        }
        return false;
    }
}

package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;

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

}

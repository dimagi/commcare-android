package org.commcare.android.util;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ResourceDownloadStats {

    /**
     * When set CommCarePlatform.stageUpgradeTable() will clear the last
     * version of the upgrade table and start over. Otherwise install reuses
     * the last version of the upgrade table.
     */
    public static boolean calcResourceFreshness() {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        /*
        long lastInstallTime = app.getAppPreferences().getLong(CommCareSetupActivity.KEY_LAST_INSTALL, -1);
        if (System.currentTimeMillis() - lastInstallTime > START_OVER_THRESHOLD) {
            // If we are triggering a start over install due to the time
            // threshold when there is a partial resource table that we could
            // be using, send a message to log this.
            ResourceTable temporary = app.getCommCarePlatform().getUpgradeResourceTable();
            if (temporary.getTableReadiness() == ResourceTable.RESOURCE_TABLE_PARTIAL) {
                Logger.log(AndroidLogger.TYPE_RESOURCES, "A start-over on installation has been "
                        + "triggered by the time threshold when there is an existing partial "
                        + "resource table that could be used.");
            }
            return true;
        } else {
            return app.getAppPreferences().getBoolean(KEY_START_OVER, true);
        }
        */
        return false;
    }

}

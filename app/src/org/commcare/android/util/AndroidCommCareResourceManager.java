package org.commcare.android.util;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.ResourceTable;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareResourceManager;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidCommCareResourceManager extends CommCareResourceManager {
    private ResourceDownloadStats installStatListener;
    private final CommCareApp app;

    public AndroidCommCareResourceManager(CommCarePlatform platform,
                                          ResourceTable masterTable,
                                          ResourceTable upgradeTable,
                                          ResourceTable tempTable) {
        super(platform, masterTable, upgradeTable, tempTable);

        app = CommCareApplication._().getCurrentApp();

        installStatListener = ResourceDownloadStats.loadPersistentStats(app);
        upgradeTable.setInstallStatListener(installStatListener);
    }

    public void clearUpgradeTable() {
        upgradeTable.clear();
        ResourceDownloadStats.clearPersistedStats(app);
    }

    public void saveDownloadStats() {
        ResourceDownloadStats.saveStatsPersistently(app, installStatListener);
    }
}

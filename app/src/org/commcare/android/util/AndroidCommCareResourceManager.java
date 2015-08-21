package org.commcare.android.util;

import android.util.Log;

import org.commcare.android.analytics.DownloadStatUtils;
import org.commcare.android.analytics.ResourceDownloadStats;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareResourceManager;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidCommCareResourceManager extends CommCareResourceManager {
    private final String TAG = AndroidCommCareResourceManager.class.getSimpleName();
    private ResourceDownloadStats installStatListener;
    private final CommCareApp app;

    public AndroidCommCareResourceManager(AndroidCommCarePlatform platform) {
        super(platform, platform.getGlobalResourceTable(), platform.getUpgradeResourceTable(), platform.getRecoveryTable());

        app = CommCareApplication._().getCurrentApp();

        installStatListener = DownloadStatUtils.loadPersistentStats(app);
        upgradeTable.setInstallStatListener(installStatListener);
    }

    public void clearUpgradeTable() {
        upgradeTable.clear();
        Log.i(TAG, "Clearing upgrade table, here are the stats collected");
        Log.i(TAG, installStatListener.toString());
        DownloadStatUtils.clearPersistedStats(app);
    }

    public void upgradeCancelled() {
        if (!isUpgradeTableStaged()) {
            DownloadStatUtils.saveStatsPersistently(app, installStatListener);
        } else {
            Log.i(TAG, "Upgrade cancelled, but already finished with these stats");
            Log.i(TAG, installStatListener.toString());
        }
    }

    /**
     * Load the latest profile into the upgrade table.
     */
    public void instantiateLatestProfile(String profileRef)
            throws UnfullfilledRequirementsException,
            UnresolvedResourceException,
            InstallCancelledException {

        ensureValidState();

        if (installStatListener.isUpgradeStale()) {
            Log.i(TAG, "Clearing upgrade table because resource downloads " +
                    "failed too many times or started too long ago");
            upgradeTable.destroy();
        }

        Resource upgradeProfile =
                upgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

        if (upgradeProfile == null) {
            loadProfile(upgradeTable, profileRef);
        } else {
            loadProfileViaTemp(profileRef, upgradeProfile);
        }
    }

    private void loadProfileViaTemp(String profileRef, Resource upgradeProfile)
            throws UnfullfilledRequirementsException,
            UnresolvedResourceException,
            InstallCancelledException {
        // TODO PLM: this doesn't collect any resource download stats because
        // the resources are first being downloaded into tempTable which isn't
        // being tracked by ResourceDownloadStats
        if (!tempTable.isEmpty()) {
            throw new RuntimeException("Expected temp table to be empty");
        }
        tempTable.destroy();
        loadProfile(tempTable, profileRef);
        Resource tempProfile =
                tempTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

        if (tempProfile != null && tempProfile.isNewer(upgradeProfile)) {
            upgradeTable.destroy();
            tempTable.copyToTable(upgradeTable);
        }

        tempTable.destroy();
    }
}

package org.commcare.android.util;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.CommCareResourceManager;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidCommCareResourceManager extends CommCareResourceManager {
    private ResourceDownloadStats installStatListener;
    private final CommCareApp app;

    public AndroidCommCareResourceManager(AndroidCommCarePlatform platform) {
        super(platform, platform.getGlobalResourceTable(), platform.getUpgradeResourceTable(), platform.getRecoveryTable());

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

    /**
     * Load the latest profile into the upgrade table.
     */
    public void instantiateLatestProfile(String profileRef)
            throws UnfullfilledRequirementsException,
            UnresolvedResourceException,
            InstallCancelledException {

        ensureValidState();

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

        ResourceTable tempUpgradeTable =
                ResourceTable.RetrieveTable(app.getStorage("TEMP_UPGRADE_RESOURCE_TABLE", Resource.class),
                new AndroidResourceInstallerFactory(app));
        tempUpgradeTable.destroy();
        loadProfile(tempUpgradeTable, profileRef);
        Resource tempProfile =
                tempUpgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

        if (tempProfile != null && tempProfile.isNewer(upgradeProfile)) {
            upgradeTable.destroy();
            tempUpgradeTable.copyToTable(upgradeTable);
        }

        tempUpgradeTable.destroy();
    }

}

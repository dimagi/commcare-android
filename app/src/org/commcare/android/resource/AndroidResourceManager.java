package org.commcare.android.resource;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.resource.analytics.UpdateStats;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.AndroidResourceInstallerFactory;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.ResourceManager;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

/**
 * Manages app installations and updates. Extends the ResourceManager with the
 * ability to stage but not apply updates.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidResourceManager extends ResourceManager {
    private final static String TAG = AndroidResourceManager.class.getSimpleName();
    public final static String TEMP_UPGRADE_TABLE_KEY = "TEMP_UPGRADE_RESOURCE_TABLE";
    private final UpdateStats updateStats;
    private final CommCareApp app;
    private String profileRef;
    private final ResourceTable tempUpgradeTable;

    // 5 minutes
    private final static long UPDATE_RETRY_DELAY_IN_MS = 1000 * 60 * 5;

    public AndroidResourceManager(AndroidCommCarePlatform platform) {
        super(platform, platform.getGlobalResourceTable(),
                platform.getUpgradeResourceTable(),
                platform.getRecoveryTable());

        app = CommCareApplication._().getCurrentApp();

        tempUpgradeTable =
                ResourceTable.RetrieveTable(app.getStorage(TEMP_UPGRADE_TABLE_KEY, Resource.class),
                        new AndroidResourceInstallerFactory(app));

        updateStats = UpdateStats.loadUpdateStats(app);
        upgradeTable.setInstallStatsLogger(updateStats);
        tempUpgradeTable.setInstallStatsLogger(updateStats);
    }

    /**
     * Download the latest profile; if it is new, download and stage the entire
     * update.
     *
     * @param profileRef Reference that resolves to the profile file used to
     *                   seed the update
     * @return UpdateStaged upon update download, UpToDate if no new update,
     * otherwise an error status.
     */
    public AppInstallStatus checkAndPrepareUpgradeResources(String profileRef) {
        synchronized (updateLock) {
            this.profileRef = profileRef;
            try {
                instantiateLatestUpgradeProfile();

                if (isUpgradeTableStaged()) {
                    return AppInstallStatus.UpdateStaged;
                }

                if (updateIsntNewer(getMasterProfile())) {
                    Logger.log(AndroidLogger.TYPE_RESOURCES, "App Resources up to Date");
                    upgradeTable.clear();
                    return AppInstallStatus.UpToDate;
                }

                prepareUpgradeResources();
            } catch (InstallCancelledException e) {
                // The user cancelled the upgrade check process. The calling task
                // should have caught and handled the cancellation
                return AppInstallStatus.UnknownFailure;
            } catch (LocalStorageUnavailableException e) {
                ResourceInstallUtils.logInstallError(e,
                        "Couldn't install file to local storage|");
                return AppInstallStatus.NoLocalStorage;
            } catch (UnfullfilledRequirementsException e) {
                if (e.isDuplicateException()) {
                    return AppInstallStatus.DuplicateApp;
                } else {
                    ResourceInstallUtils.logInstallError(e,
                            "App resources are incompatible with this device|");
                    return AppInstallStatus.IncompatibleReqs;
                }
            } catch (UnresolvedResourceException e) {
                return ResourceInstallUtils.processUnresolvedResource(e);
            }

            return AppInstallStatus.UpdateStaged;
        }
    }

    /**
     * Load the latest profile into the upgrade table. Clears the upgrade table
     * if it's partially populated with an out-of-date version.
     */
    private void instantiateLatestUpgradeProfile()
            throws UnfullfilledRequirementsException,
            UnresolvedResourceException,
            InstallCancelledException {

        ensureMasterTableValid();

        if (updateStats.isUpgradeStale()) {
            Log.i(TAG, "Clearing upgrade table because resource downloads " +
                    "failed too many times or started too long ago");
            upgradeTable.destroy();
        }

        Resource upgradeProfile =
                upgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

        if (upgradeProfile == null) {
            loadProfile(upgradeTable, profileRef);
        } else {
            loadProfileViaTemp(upgradeProfile);
        }
    }

    /**
     * Download the latest profile into the temporary table and if the version
     * higher than the upgrade table's profile, copy it into the upgrade table.
     *
     * @param upgradeProfile the profile currently in the upgrade table.
     */
    private void loadProfileViaTemp(Resource upgradeProfile)
            throws UnfullfilledRequirementsException,
            UnresolvedResourceException,
            InstallCancelledException {
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

    /**
     * Set listeners and checkers that enable communication between low-level
     * resource installation and top-level app update/installation process.
     *
     * @param tableListener  allows resource table to report its progress to the
     *                       launching process
     * @param cancelCheckker allows resource installers to check if the
     *                       launching process was cancelled
     */
    @Override
    public void setUpgradeListeners(TableStateListener tableListener,
                                    InstallCancelled cancelCheckker) {
        super.setUpgradeListeners(tableListener, cancelCheckker);

        tempUpgradeTable.setStateListener(tableListener);
        tempUpgradeTable.setInstallCancellationChecker(cancelCheckker);
    }

    /**
     * Save upgrade stats if the upgrade was cancelled and wasn't complete at
     * that time.
     */
    public void upgradeCancelled() {
        if (!isUpgradeTableStaged()) {
            UpdateStats.saveStatsPersistently(app, updateStats);
        } else {
            Log.i(TAG, "Upgrade cancelled, but already finished with these stats");
            Log.i(TAG, updateStats.toString());
        }
    }

    public void incrementUpdateAttempts() {
        updateStats.registerStagingAttempt();
    }

    /**
     * Log update failure that occurs while trying to install the staged update table
     */
    public void recordUpdateInstallFailure(Exception exception) {
        updateStats.registerUpdateException(exception);
    }

    public void recordUpdateInstallFailure(AppInstallStatus result) {
        updateStats.registerUpdateException(new Exception(result.toString()));
    }

    /**
     * Clear update table, log failure with update stats,
     * and, if appropriate, schedule a update retry
     *
     * @param result update attempt result
     * @param ctx    Used for showing pinned notification of update task retry
     */
    public void processUpdateFailure(AppInstallStatus result, Context ctx) {
        updateStats.registerUpdateException(new Exception(result.toString()));

        boolean reusePartialTable =
                (result == AppInstallStatus.UnknownFailure ||
                        result == AppInstallStatus.NoLocalStorage);

        if (!reusePartialTable) {
            upgradeTable.clear();
        }

        retryUpdateOrGiveUp(ctx);
    }

    private void retryUpdateOrGiveUp(Context ctx) {
        if (updateStats.isUpgradeStale()) {
            Log.i(TAG, "Stop trying to download update. Here are the update stats:");
            Log.i(TAG, updateStats.toString());

            UpdateStats.clearPersistedStats(app);

            upgradeTable.clear();
        } else {
            Log.w(TAG, "Retrying auto-update");
            UpdateStats.saveStatsPersistently(app, updateStats);
            scheduleUpdateTaskRetry(ctx);
        }
    }

    private void scheduleUpdateTaskRetry(final Context ctx) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String ref = ResourceInstallUtils.getDefaultProfileRef();
                try {
                    UpdateTask updateTask = UpdateTask.getNewInstance();
                    updateTask.startPinnedNotification(ctx);
                    updateTask.execute(ref);
                } catch (IllegalStateException e) {
                    // The user may have started the update process in the meantime
                    Log.w(TAG, "Trying trigger an auto-update retry when it is already running");
                }
            }
        }, UPDATE_RETRY_DELAY_IN_MS);
    }
}

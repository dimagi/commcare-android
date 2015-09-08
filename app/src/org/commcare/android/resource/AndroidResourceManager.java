package org.commcare.android.resource;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.resource.analytics.UpdateStatPersistence;
import org.commcare.android.resource.analytics.UpdateStats;
import org.commcare.android.resource.installers.LocalStorageUnavailableException;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.ResourceManager;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.Resource;
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
    private final String TAG = AndroidResourceManager.class.getSimpleName();
    private final UpdateStats updateStats;
    private final CommCareApp app;
    private String profileRef;

    // 5 minutes
    private final static long UPDATE_RETRY_DELAY_IN_MS = 1000 * 60 * 5;

    public AndroidResourceManager(AndroidCommCarePlatform platform) {
        super(platform, platform.getGlobalResourceTable(),
                platform.getUpgradeResourceTable(),
                platform.getRecoveryTable());

        app = CommCareApplication._().getCurrentApp();

        updateStats = UpdateStatPersistence.loadUpdateStats(app);
        upgradeTable.setInstallStatsLogger(updateStats);
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
                instantiateLatestProfile();

                if (isUpgradeTableStaged()) {
                    return AppInstallStatus.UpdateStaged;
                }

                if (updateIsntNewer(getMasterProfile())) {
                    Logger.log(AndroidLogger.TYPE_RESOURCES, "App Resources up to Date");
                    upgradeTable.clear();
                    return AppInstallStatus.UpToDate;
                }

                updateStats.registerStagingAttempt();

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
    private void instantiateLatestProfile()
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

    /**
     * Save upgrade stats if the upgrade was cancelled and wasn't complete at
     * that time.
     */
    public void upgradeCancelled() {
        if (!isUpgradeTableStaged()) {
            UpdateStatPersistence.saveStatsPersistently(app, updateStats);
        } else {
            Log.i(TAG, "Upgrade cancelled, but already finished with these stats");
            Log.i(TAG, updateStats.toString());
        }
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
     * @param exception Log error with update statistics
     * @param context   Used for showing pinned notification of update task retry
     */
    public void processUpdateFailure(Exception exception, Context context) {
        updateStats.registerUpdateException(exception);

        upgradeTable.clear();

        retryUpdateOrGiveUp(context);
    }

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

            UpdateStatPersistence.clearPersistedStats(app);

            upgradeTable.clear();
        } else {
            Log.w(TAG, "Retrying auto-update");
            UpdateStatPersistence.saveStatsPersistently(app, updateStats);
            scheduleUpdateTask(ctx);
        }
    }

    private void scheduleUpdateTask(final Context ctx) {
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

package org.commcare.engine.resource;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.logging.analytics.UpdateStats;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.PrefValues;
import org.commcare.resources.ResourceManager;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.InstallCancelledException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.UpdateTask;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.AndroidResourceInstallerFactory;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.reference.ReleasedOnTimeSupportedReference;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.text.ParseException;
import java.util.Date;

import static org.commcare.google.services.analytics.AnalyticsParamValue.UPDATE_RESET_REASON_CORRUPT;
import static org.commcare.google.services.analytics.AnalyticsParamValue.UPDATE_RESET_REASON_NEWER_VERSION_AVAILABLE;
import static org.commcare.google.services.analytics.AnalyticsParamValue.UPDATE_RESET_REASON_OVERSHOOT_TRIALS;
import static org.commcare.google.services.analytics.AnalyticsParamValue.UPDATE_RESET_REASON_TIMEOUT;

/**
 * Manages app installations and updates. Extends the ResourceManager with the
 * ability to stage but not apply updates.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AndroidResourceManager extends ResourceManager {
    private final static String TAG = AndroidResourceManager.class.getSimpleName();
    public final static String TEMP_UPGRADE_TABLE_KEY = "TEMP_UPGRADE_RESOURCE_TABLE";
    // 60 minutes
    private final static long MAX_UPDATE_RETRY_DELAY_IN_MS = 1000 * 60 * 60;
    private final CommCareApp app;
    private final UpdateStats updateStats;
    private final ResourceTable tempUpgradeTable;
    private String profileRef;

    public AndroidResourceManager(AndroidCommCarePlatform platform) {
        super(platform, platform.getGlobalResourceTable(),
                platform.getUpgradeResourceTable(),
                platform.getRecoveryTable());

        app = CommCareApplication.instance().getCurrentApp();

        tempUpgradeTable =
                new AndroidResourceTable(app.getStorage(TEMP_UPGRADE_TABLE_KEY, Resource.class),
                        new AndroidResourceInstallerFactory());

        updateStats = UpdateStats.loadUpdateStats(app);
        upgradeTable.setInstallStatsLogger(updateStats);
        tempUpgradeTable.setInstallStatsLogger(updateStats);
    }

    /**
     * Download the latest profile; if it is new, download and stage the entire
     * update.
     *
     * @param profileRef       Reference that resolves to the profile file used to
     *                         seed the update
     * @param profileAuthority The authority from which the app resources for the update are
     *                         coming (local vs. remote)
     * @return UpdateStaged upon update download, UpToDate if no new update,
     * otherwise an error status.
     */
    public AppInstallStatus checkAndPrepareUpgradeResources(String profileRef, int profileAuthority)
            throws UnfullfilledRequirementsException, UnresolvedResourceException, InstallCancelledException {
        synchronized (platform) {
            this.profileRef = profileRef;
            instantiateLatestUpgradeProfile(profileAuthority);

            if (isUpgradeTableStaged()) {
                return AppInstallStatus.UpdateStaged;
            }

            if (updateNotNewer(getMasterProfile())) {
                Logger.log(LogTypes.TYPE_RESOURCES, "App Resources up to Date");
                clearUpgrade();
                return AppInstallStatus.UpToDate;
            }

            prepareUpgradeResources();
            return AppInstallStatus.UpdateStaged;
        }
    }

    /**
     * Load the latest profile into the upgrade table. Clears the upgrade table
     * if it's partially populated with an out-of-date version.
     */
    private void instantiateLatestUpgradeProfile(int authority)
            throws UnfullfilledRequirementsException,
            UnresolvedResourceException,
            InstallCancelledException {

        ensureMasterTableValid();

        if (updateStats.isUpgradeStale()) {
            Log.i(TAG, "Clearing upgrade table because resource downloads " +
                    "failed too many times or started too long ago");

            FirebaseAnalyticsUtil.reportUpdateReset(updateStats.hasUpdateTrialsMaxedOut() ?
                    UPDATE_RESET_REASON_OVERSHOOT_TRIALS : UPDATE_RESET_REASON_TIMEOUT);

            upgradeTable.destroy();
            updateStats.resetStats(app);
        }

        Resource upgradeProfile =
                upgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

        if (upgradeProfile == null) {
            loadProfileIntoTable(upgradeTable, profileRef, authority);
        } else {
            loadProfileViaTemp(upgradeProfile, authority);
        }
    }

    /**
     * Download the latest profile into the temporary table and if the version
     * higher than the upgrade table's profile, copy it into the upgrade table.
     *
     * @param upgradeProfile the profile currently in the upgrade table.
     */
    private void loadProfileViaTemp(Resource upgradeProfile, int profileAuthority)
            throws UnfullfilledRequirementsException,
            UnresolvedResourceException,
            InstallCancelledException {
        tempUpgradeTable.destroy();
        loadProfileIntoTable(tempUpgradeTable, profileRef, profileAuthority);
        Resource tempProfile =
                tempUpgradeTable.getResourceWithId(CommCarePlatform.APP_PROFILE_RESOURCE_ID);

        if (tempProfile != null && tempProfile.isNewer(upgradeProfile)) {
            upgradeTable.destroy();

            FirebaseAnalyticsUtil.reportUpdateReset(UPDATE_RESET_REASON_NEWER_VERSION_AVAILABLE);

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
     * @param result       update attempt result
     * @param ctx          Used for showing pinned notification of update task retry
     * @param isAutoUpdate When set keep retrying update with delay and max retry count
     */
    public void processUpdateFailure(AppInstallStatus result,
                                     Context ctx,
                                     boolean isAutoUpdate) {
        updateStats.registerUpdateException(new Exception(result.toString()));
        FirebaseAnalyticsUtil.reportStageUpdateAttemptFailure(result.toString());

        if (!result.canReusePartialUpdateTable()) {
            clearUpgrade();
            FirebaseAnalyticsUtil.reportUpdateReset(UPDATE_RESET_REASON_CORRUPT);
        }

        retryUpdateOrGiveUp(ctx, isAutoUpdate);
    }

    private void retryUpdateOrGiveUp(Context ctx, boolean isAutoUpdate) {
        if (updateStats.isUpgradeStale()) {
            Logger.log(LogTypes.TYPE_RESOURCES,
                    "Update was stale, stopped trying to download update. Update Stats: " + updateStats.toString());


            FirebaseAnalyticsUtil.reportUpdateReset(updateStats.hasUpdateTrialsMaxedOut() ?
                    UPDATE_RESET_REASON_OVERSHOOT_TRIALS : UPDATE_RESET_REASON_TIMEOUT);

            UpdateStats.clearPersistedStats(app);

            if (isAutoUpdate) {
                ResourceInstallUtils.recordAutoUpdateCompletion(app);
            }

            clearUpgrade();

        } else {
            Logger.log(LogTypes.TYPE_RESOURCES, "Retrying auto-update");
            UpdateStats.saveStatsPersistently(app, updateStats);
            if (isAutoUpdate) {
                scheduleUpdateTaskRetry(ctx, updateStats.getRestartCount());
            }
        }
    }

    private void scheduleUpdateTaskRetry(final Context ctx, int numberOfRestarts) {
        final Handler handler = new Handler();
        handler.postDelayed(() -> launchRetryTask(ctx), exponentionalRetryDelay(numberOfRestarts));
    }

    private void launchRetryTask(Context ctx) {
        String ref = ResourceInstallUtils.getDefaultProfileRef();
        try {
            if (canUpdateRetryRun()) {
                UpdateTask updateTask = UpdateTask.getNewInstance();
                updateTask.startPinnedNotification(ctx);
                updateTask.setAsAutoUpdate();
                updateTask.executeParallel(ref);
            }
        } catch (IllegalStateException e) {
            // The user may have started the update process in the meantime
            Log.w(TAG, "Trying trigger an auto-update retry when it is already running");
        }
    }

    /**
     * @return Logged into an app that has begun the auto-update process
     */
    private static boolean canUpdateRetryRun() {
        try {
            CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
            // NOTE PLM: Doesn't distinguish between two apps currently in the
            // auto-update process.
            return CommCareApplication.instance().getSession().isActive() &&
                    ResourceInstallUtils.shouldAutoUpdateResume(currentApp);
        } catch (SessionUnavailableException e) {
            return false;
        }
    }

    /**
     * Retry delay that ranges between 30 seconds and 60 minutes.
     * At 3 retries the delay is 35 seconds, at 5 retries it is at 30 minutes.
     *
     * @param numberOfRestarts used as the exponent for the delay calculation
     * @return delay in MS, which grows exponentially over the number of restarts.
     */
    private long exponentionalRetryDelay(int numberOfRestarts) {
        final Double base = 10 * (1.78);
        final long thirtySeconds = 30 * 1000;
        long exponentialDelay = thirtySeconds + (long)Math.pow(base, numberOfRestarts);
        return Math.min(exponentialDelay, MAX_UPDATE_RETRY_DELAY_IN_MS);
    }

    @Override
    public void clearUpgrade() {
        super.clearUpgrade();
        HiddenPreferences.setReleasedOnTimeForOngoingAppDownload((AndroidCommCarePlatform)platform, 0);
        HiddenPreferences.setPreUpdateSyncNeeded(PrefValues.NO);
    }
}

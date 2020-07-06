package org.commcare.logging.analytics;

import org.commcare.CommCareApp;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.tasks.ResultAndError;

import java.io.Serializable;
import java.util.Date;

/**
 * Statistics associated with attempting to stage resources into the app's
 * update table.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateStats implements Serializable {
    private static final String UPGRADE_STATS_KEY = "upgrade_table_stats";
    private static final long TWO_WEEKS_IN_MS = 1000 * 60 * 60 * 24 * 24;
    private static final int ATTEMPTS_UNTIL_UPDATE_STALE = 5;

    private long startInstallTime;
    private int resetCounter = 0;
    private ResultAndError<AppInstallStatus> lastStageUpdateResult;

    private UpdateStats() {
        startInstallTime = new Date().getTime();
    }

    /**
     * Load update statistics associated with upgrade table from app
     * preferences
     *
     * @return Persistently-stored update stats or if no stats found then a new
     * update stats object.
     */
    public static UpdateStats loadUpdateStats(CommCareApp app) {
        Object stats = PrefStats.loadStats(app, UPGRADE_STATS_KEY);
        if (stats != null) {
            return (UpdateStats)stats;
        } else {
            return new UpdateStats();
        }
    }

    /**
     * Save update stats to app preferences for reuse if the update is ever
     * resumed.
     */
    public static void saveStatsPersistently(CommCareApp app,
                                             UpdateStats stats) {
        PrefStats.saveStatsPersistently(app, UPGRADE_STATS_KEY, stats);
    }

    public void resetStats(CommCareApp app) {
        clearPersistedStats(app);
        startInstallTime = new Date().getTime();
        resetCounter = 0;
    }

    /**
     * Wipe stats associated with upgrade table from app preferences.
     */
    public static void clearPersistedStats(CommCareApp app) {
        PrefStats.clearPersistedStats(app, UPGRADE_STATS_KEY);
    }

    public void registerUpdateFailure(AppInstallStatus result) {
        if (result.causeUpdateReset()) {
            resetCounter++;
        }
    }

    /**
     * Register result of a staging attempt
     */
    public void registerStagingUpdateResult(ResultAndError<AppInstallStatus> resultAndError) {
        lastStageUpdateResult = resultAndError;
    }

    /**
     * @return Should the update be considered stale due to elapse time or too
     * many unsuccessful installs?
     */
    public boolean isUpgradeStale() {
        return hasUpdateTrialsMaxedOut() || hasUpdateTimedOut();
    }

    private boolean hasUpdateTimedOut() {
        long currentTime = new Date().getTime();
        return (currentTime - startInstallTime) > TWO_WEEKS_IN_MS;
    }

    public boolean hasUpdateTrialsMaxedOut() {
        return resetCounter > ATTEMPTS_UNTIL_UPDATE_STALE;
    }

    public ResultAndError<AppInstallStatus> getLastStageUpdateResult() {
        return lastStageUpdateResult;
    }

    @Override
    public String toString() {
        return "Update first started: " +
                new Date(startInstallTime).toString() +
                ".\n" +
                "Reset Count " +
                resetCounter +
                " times.\n";
    }

}

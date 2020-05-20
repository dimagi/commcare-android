package org.commcare.logging.analytics;

import org.commcare.CommCareApp;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.resources.model.InstallStatsLogger;
import org.commcare.tasks.ResultAndError;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Date;
import java.util.Hashtable;

/**
 * Statistics associated with attempting to stage resources into the app's
 * update table.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateStats implements InstallStatsLogger, Serializable {
    private static final String TOP_LEVEL_STATS_KEY = "top-level-update-exceptions";
    private static final String UPGRADE_STATS_KEY = "upgrade_table_stats";
    private static final long TWO_WEEKS_IN_MS = 1000 * 60 * 60 * 24 * 24;
    private static final int ATTEMPTS_UNTIL_UPDATE_STALE = 10;

    private final Hashtable<String, InstallAttempts<String>> resourceInstallStats;
    private long startInstallTime;
    private int restartCount = 0;
    private ResultAndError<AppInstallStatus> lastStageUpdateResult;

    private UpdateStats() {
        startInstallTime = new Date().getTime();
        resourceInstallStats = new Hashtable<>();
        resourceInstallStats.put(TOP_LEVEL_STATS_KEY,
                new InstallAttempts<>(TOP_LEVEL_STATS_KEY));
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
        resourceInstallStats.clear();
        restartCount = 0;
    }

    /**
     * Wipe stats associated with upgrade table from app preferences.
     */
    public static void clearPersistedStats(CommCareApp app) {
        PrefStats.clearPersistedStats(app, UPGRADE_STATS_KEY);
    }

    /**
     * Register attempt to download resources into update table.
     */
    public void registerStagingAttempt() {
        restartCount++;
    }

    /**
     * Register stack trace for exception raised during update.
     */
    public void registerUpdateException(Exception e) {
        recordResourceInstallFailure(TOP_LEVEL_STATS_KEY, e);
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
        return restartCount > ATTEMPTS_UNTIL_UPDATE_STALE;
    }

    @Override
    public void recordResourceInstallSuccess(String resourceName) {
        InstallAttempts<String> attempts =
                resourceInstallStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            resourceInstallStats.put(resourceName, attempts);
        }
        attempts.registerSuccesfulInstall();
    }

    @Override
    public void recordResourceInstallFailure(String resourceName,
                                             Exception errorMsg) {
        InstallAttempts<String> attempts =
                resourceInstallStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            resourceInstallStats.put(resourceName, attempts);
        }
        String stackTrace = getStackTraceString(errorMsg);
        attempts.addFailure(stackTrace);
    }

    private static String getStackTraceString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public ResultAndError<AppInstallStatus> getLastStageUpdateResult() {
        return lastStageUpdateResult;
    }

    @Override
    public String toString() {
        StringBuilder statsStringBuilder = new StringBuilder();

        statsStringBuilder.append("Update first started: ")
                .append(new Date(startInstallTime).toString())
                .append(".\n")
                .append("Update restarted ")
                .append(restartCount)
                .append(" times.\n")
                .append("Failures logged to the update table: \n")
                .append(resourceInstallStats.get(TOP_LEVEL_STATS_KEY).toString())
                .append("\n");

        for (String resourceName : resourceInstallStats.keySet()) {
            if (!resourceName.equals(TOP_LEVEL_STATS_KEY)) {
                statsStringBuilder.append(resourceInstallStats.get(resourceName).toString()).append("\n");
            }
        }

        return statsStringBuilder.toString();
    }

}

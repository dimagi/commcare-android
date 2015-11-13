package org.commcare.android.analytics;

import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.resources.model.InstallStatsLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    private static final String TAG = UpdateStats.class.getSimpleName();
    private final Hashtable<String, InstallAttempts<String>> resourceInstallStats;
    private final long startInstallTime;
    private int restartCount = 0;
    private final static String TOP_LEVEL_STATS_KEY = "top-level-update-exceptions";
    private static final String UPGRADE_STATS_KEY = "upgrade_table_stats";

    private static final long TWO_WEEKS_IN_MS = 1000 * 60 * 60 * 24 * 24;
    private static final int ATTEMPTS_UNTIL_UPDATE_STALE = 5;

    private UpdateStats() {
        startInstallTime = new Date().getTime();
        resourceInstallStats = new Hashtable<>();
        resourceInstallStats.put(TOP_LEVEL_STATS_KEY,
                new InstallAttempts<String>(TOP_LEVEL_STATS_KEY));
    }

    /**
     * Load update statistics associated with upgrade table from app preferences
     *
     * @return Persistently-stored update stats or if no stats found then a new
     * update stats object.
     */
    public static UpdateStats loadUpdateStats(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        if (prefs.contains(UPGRADE_STATS_KEY)) {
            try {
                String serializedObj = prefs.getString(UPGRADE_STATS_KEY, "");
                return (UpdateStats)UpdateStats.deserialize(serializedObj);
            } catch (Exception e) {
                Log.w(TAG, "Failed to deserialize update stats, defaulting to new instance.");
                e.printStackTrace();
                clearPersistedStats(app);
                return new UpdateStats();
            }
        } else {
            return new UpdateStats();
        }
    }

    private static Object deserialize(String s) throws IOException,
            ClassNotFoundException {
        byte[] data = Base64.decode(s, Base64.DEFAULT);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    /**
     * Wipe stats associated with upgrade table from app preferences.
     */
    public static void clearPersistedStats(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(UPGRADE_STATS_KEY);
        editor.commit();
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
     * @return Should the update be considered stale due to elapse time or too
     * many unsuccessful installs?
     */
    public boolean isUpgradeStale() {
        long currentTime = new Date().getTime();
        return (restartCount > ATTEMPTS_UNTIL_UPDATE_STALE ||
                (currentTime - startInstallTime) > TWO_WEEKS_IN_MS);
    }

    @Override
    public void recordResourceInstallSuccess(String resourceName) {
        InstallAttempts<String> attempts = resourceInstallStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            resourceInstallStats.put(resourceName, attempts);
        }
        attempts.registerSuccesfulInstall();
    }

    @Override
    public void recordResourceInstallFailure(String resourceName,
                                             Exception errorMsg) {
        InstallAttempts<String> attempts = resourceInstallStats.get(resourceName);
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

    @Override
    public String toString() {
        StringBuilder statsStringBuilder = new StringBuilder();

        statsStringBuilder.append("Update first started: ").append(new Date(startInstallTime).toString()).append(".\n");
        statsStringBuilder.append("Update restarted ").append(restartCount).append(" times.\n");

        statsStringBuilder.append("Failures logged to the update table: \n");
        statsStringBuilder.append(resourceInstallStats.get(TOP_LEVEL_STATS_KEY).toString()).append("\n");

        for (String resourceName : resourceInstallStats.keySet()) {
            if (!resourceName.equals(TOP_LEVEL_STATS_KEY)) {
                statsStringBuilder.append(resourceInstallStats.get(resourceName).toString()).append("\n");
            }
        }

        return statsStringBuilder.toString();
    }

    /**
     * Save update stats to app preferences for reuse if the update is ever resumed.
     */
    public static void saveStatsPersistently(CommCareApp app,
                                             UpdateStats updateStats) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        try {
            String serializedObj = UpdateStats.serialize(updateStats);
            editor.putString(UPGRADE_STATS_KEY, serializedObj);
            editor.commit();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to serialize and store resource installation stats");
        }
    }

    private static String serialize(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    public int getRestartCount() {
        return restartCount;
    }
}

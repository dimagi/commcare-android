package org.commcare.android.resource.analytics;

import android.util.Base64;

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
    private final Hashtable<String, InstallAttempts<String>> resourceInstallStats;
    private final long startInstallTime;
    private int restartCount = 0;
    private final static String TOP_LEVEL_STATS_KEY = "top-level-update-exceptions";

    private static final long TWO_WEEKS_IN_MS = 1000 * 60 * 60 * 24 * 24;
    private static final int ATTEMPTS_UNTIL_UPDATE_STALE = 5;

    public UpdateStats() {
        startInstallTime = new Date().getTime();
        resourceInstallStats = new Hashtable<>();
        resourceInstallStats.put(TOP_LEVEL_STATS_KEY,
                new InstallAttempts<String>(TOP_LEVEL_STATS_KEY));
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
        // TODO PLM: test this!
        long currentTime = new Date().getTime();
        return (restartCount > ATTEMPTS_UNTIL_UPDATE_STALE ||
                (currentTime - startInstallTime) > TWO_WEEKS_IN_MS);
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

    private String getStackTraceString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
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

    public static Object deserialize(String s) throws IOException,
            ClassNotFoundException {
        byte[] data = Base64.decode(s, Base64.DEFAULT);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public static String serialize(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }
}

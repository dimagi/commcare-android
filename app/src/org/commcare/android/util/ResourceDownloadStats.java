package org.commcare.android.util;

import android.content.SharedPreferences;
import android.util.Log;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.resources.model.InstallStatListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ResourceDownloadStats implements InstallStatListener, Serializable {
    private static final String TAG = ResourceDownloadStats.class.getSimpleName();

    private Hashtable<String, InstallAttempts<Exception>> installStats;
    private long startInstallTime;
    private int restartCount = 0;

    private static final String UPGRADE_STATS = "upgrade_table_stats";

    public ResourceDownloadStats() {
        startInstallTime = new Date().getTime();
        installStats = new Hashtable<>();
    }

    @Override
    public void recordResourceInstallFailure(String resourceName,
                                             Exception e) {
        InstallAttempts<Exception> attempts = installStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            installStats.put(resourceName, attempts);
        }
        attempts.add(e);
    }

    @Override
    public void recordResourceInstallSuccess(String resourceName) {
        InstallAttempts<Exception> attempts = installStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            installStats.put(resourceName, attempts);
        }
        attempts.wasSuccessful = true;
    }


    public static ResourceDownloadStats loadPersistentStats(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        if (prefs.contains(UPGRADE_STATS)) {
            try {
                return ResourceDownloadStats.deserialize(prefs.getString(UPGRADE_STATS, ""));
            } catch (Exception e) {
                clearPersistedStats(app);
                return new ResourceDownloadStats();
            }
        } else {
            return new ResourceDownloadStats();
        }
    }

    public static void saveStatsPersistently(CommCareApp app, ResourceDownloadStats installStatListener) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        try {
            editor.putString(UPGRADE_STATS, installStatListener.serialize());
            editor.commit();
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "Failed to serialize and store resource installation stats");
        }
    }

    public static void clearPersistedStats(CommCareApp app) {
        SharedPreferences prefs = app.getAppPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(UPGRADE_STATS);
        editor.commit();
    }

    private class InstallAttempts<A> implements Serializable {
        public boolean wasSuccessful = false;

        private final String resourceName;
        private final Vector<FailureEvent<A>> failures;

        public InstallAttempts(String resourceName) {
            failures = new Vector<>();
            this.resourceName = resourceName;
        }

        public int attempts() {
            return failures.size();
        }

        public void add(A failureData) {
            failures.add(new FailureEvent<>(failureData));
        }

        public String toString() {
            StringBuilder failureLog = new StringBuilder(resourceName);
            if (wasSuccessful) {
                failureLog.append(" succesfully installed");
            } else {
                failureLog.append(" wasn't installed");
            }

            if (failures.size() > 0) {
                failureLog.append("\n")
                        .append(failures.size())
                        .append(" failed attempts:");
            }

            for (FailureEvent<A> event : failures) {
                failureLog.append(event.time.toString())
                        .append(": ")
                        .append(event.data.toString())
                        .append("\n");
            }
            return failureLog.toString();
        }
    }

    public String serialize() throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ObjectOutputStream so = new ObjectOutputStream(bo);
        so.writeObject(this);
        so.flush();
        return bo.toString();
    }

    public static ResourceDownloadStats deserialize(String serializedObject)
            throws Exception {
        byte b[] = serializedObject.getBytes();
        ByteArrayInputStream bi = new ByteArrayInputStream(b);
        ObjectInputStream si = new ObjectInputStream(bi);
        return (ResourceDownloadStats)si.readObject();
    }

    private class FailureEvent<A> implements Serializable {
        public A data;
        public Date time;
        public FailureEvent(A data) {
            this.data = data;
            time = new Date();
        }
    }
}

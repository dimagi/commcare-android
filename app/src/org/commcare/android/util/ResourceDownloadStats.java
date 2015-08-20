package org.commcare.android.util;

import android.util.Pair;

import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.InstallStatListener;
import org.commcare.resources.model.Resource;

import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ResourceDownloadStats implements InstallStatListener {
    private Hashtable<Resource, InstallAttempts<Exception>> installStats;
    private long startInstallTime;

    public ResourceDownloadStats() {
        installStats = new Hashtable<>();
    }

    @Override
    public void recordResourceInstallFailure(Resource resource,
                                             Exception e) {
        InstallAttempts<Exception> attempts = installStats.get(resource);
        if (attempts == null) {
            attempts = new InstallAttempts<>();
            installStats.put(resource, attempts);
        }
        attempts.add(e);
    }

    private class InstallAttempts<A> {
        private Vector<Pair<A, Date>> failures;

        public InstallAttempts() {
            failures = new Vector<>();
        }

        public int attempts() {
            return failures.size();
        }

        public void add(A failureData) {
            failures.add(new Pair<>(failureData, new Date()));
        }

        public String toString() {
            StringBuilder failureLog = new StringBuilder();
            for (Pair<A, Date> msgAndTime : failures) {
                failureLog.append(msgAndTime.second.toString())
                        .append(": ")
                        .append(msgAndTime.first.toString())
                        .append("\n");
            }
            return failureLog.toString();
        }
    }
}


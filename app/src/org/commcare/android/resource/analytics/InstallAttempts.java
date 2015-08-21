package org.commcare.android.resource.analytics;

import java.io.Serializable;
import java.util.Date;
import java.util.Vector;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class InstallAttempts<A> implements Serializable {
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

    private class FailureEvent<A> implements Serializable {
        public A data;
        public Date time;

        public FailureEvent(A data) {
            this.data = data;
            time = new Date();
        }
    }
}

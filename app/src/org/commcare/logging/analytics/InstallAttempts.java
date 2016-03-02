package org.commcare.logging.analytics;

import java.io.Serializable;
import java.util.Date;
import java.util.Vector;

/**
 * Stores install attempt data for a given resource in the update table.
 *
 * @param <A> Type of data stored at each install attempt. Must be
 *            instantiated with a class that implements Serializeable.
 * @author Phillip Mates (pmates@dimagi.com)
 */
class InstallAttempts<A> implements Serializable {
    private boolean wasSuccessful = false;

    private final String resourceName;
    private final Vector<FailureEvent<A>> failures;

    public InstallAttempts(String resourceName) {
        failures = new Vector<>();
        this.resourceName = resourceName;
    }

    public void addFailure(A failureData) {
        failures.add(new FailureEvent<>(failureData));
    }

    public void registerSuccesfulInstall() {
        wasSuccessful = true;
    }

    @Override
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

    /**
     * @param <B> Type of data stored in the failure event. Must be
     *            instantiated with a class that implements Serializeable.
     */
    private static class FailureEvent<B> implements Serializable {
        public final B data;
        public final Date time;

        public FailureEvent(B data) {
            this.data = data;
            time = new Date();
        }
    }
}

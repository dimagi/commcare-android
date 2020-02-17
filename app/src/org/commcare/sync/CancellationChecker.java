package org.commcare.sync;

public interface CancellationChecker {

    /**
     * @return if the process implementing the CancellationChecker was cancelled
     */
    boolean wasProcessCancelled();
}

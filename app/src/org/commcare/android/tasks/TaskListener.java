package org.commcare.android.tasks;

/**
 * For reporting update, completion, and cancellation task events.
 *
 * @param <B> type of update values reported by task
 * @param <C> type of result value reported by task
 */
public interface TaskListener<B, C> {
    void handleTaskUpdate(B... updateVals);

    void handleTaskCompletion(C result);

    void handleTaskCancellation(C result);
}


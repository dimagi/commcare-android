package org.commcare.android.tasks;

public interface TaskListener<B, C> {
    void handleTaskUpdate(B... updateVals);
    void handleTaskCompletion(C result);
    void handleTaskCancellation(C result);
}


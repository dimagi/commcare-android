package org.commcare.android.tasks;

public interface TaskListener<B, C> {
    void processTaskUpdate(B... updateVals);
    void processTaskResult(C result);
    void processTaskCancel(C result);
}


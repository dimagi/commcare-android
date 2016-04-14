package org.commcare.android.util;

import org.commcare.engine.resource.AppInstallStatus;

/**
 * Created by amstone326 on 4/14/16.
 */
public class TestResourceEngineTaskListener {

    boolean taskCompleted;
    AppInstallStatus taskResult;

    public void onTaskCompletion(AppInstallStatus status) {
        taskCompleted = true;
        taskResult = status;
    }

    public boolean taskCompleted() {
        return taskCompleted;
    }

    public AppInstallStatus getResult() {
        return taskResult;
    }
}

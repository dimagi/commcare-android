package org.commcare.android.tasks;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.SystemClock;

import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.tasks.templates.CommCareTaskConnector;
import org.commcare.dalvik.application.CommCareApp;

public abstract class UpgradeAppTask<R> extends CommCareTask<String, int[], Boolean, R> {
    private static final int DIALOG_ID = 1;
    private final CommCareApp commCareApp;
    private static UpgradeAppTask latestRunningTask = null;

    public UpgradeAppTask(CommCareApp app, boolean startInBackground) {
        commCareApp = app;

        if (startInBackground) {
            taskId = -1;
        } else {
            taskId = DIALOG_ID;
        }

        TAG = UpgradeAppTask.class.getSimpleName();
    }

    @Override
    protected Boolean doTaskBackground(String... profileRefs) {
        SystemClock.sleep(2000);
    }

    public static boolean registerActivityWithRunningTask(CommCareTaskConnector connector) {
        if (latestRunningTask != null && latestRunningTask.getStatus() == Status.RUNNING) {
            latestRunningTask.connect(connector);
            return true;
        }
        return false;
    }
}

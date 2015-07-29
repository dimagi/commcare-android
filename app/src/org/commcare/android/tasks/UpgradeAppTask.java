package org.commcare.android.tasks;

import android.os.SystemClock;

import org.commcare.android.tasks.templates.ManagedAsyncTask;
import org.commcare.dalvik.application.CommCareApp;

public class UpgradeAppTask extends ManagedAsyncTask<String, int[], Boolean> {
    private static final String TAG = UpgradeAppTask.class.getSimpleName();

    private final CommCareApp commCareApp;
    private static UpgradeAppTask latestRunningTask = null;

    public enum UpgradeTaskState {
        notRunning,
        checking,
        downloading
    }
    private UpgradeTaskState taskState;

    public UpgradeAppTask(CommCareApp app) {
        commCareApp = app;
    }

    @Override
    protected final Boolean doInBackground(String... params) {
        SystemClock.sleep(2000);
        publishProgress(new int[]{0, 100});
        return false;
    }

    @Override
    protected void onProgressUpdate(int[]... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
    }

    public static UpgradeAppTask getSingleRunningTask() {
        if (latestRunningTask != null && latestRunningTask.getStatus() == Status.RUNNING) {
            return latestRunningTask;
        } else {
            return null;
        }
    }

    public UpgradeTaskState getUprgradeState() {
        return taskState;
    }
}

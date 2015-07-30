package org.commcare.android.tasks;

import android.os.SystemClock;

import org.commcare.android.tasks.templates.ManagedAsyncTask;

public class UpgradeAppTask extends ManagedAsyncTask<String, int[], Boolean> {
    private static final String TAG = UpgradeAppTask.class.getSimpleName();

    private TaskListener<int[], Boolean> taskListener = null;

    public enum UpgradeTaskState {
        notRunning,
        checking,
        downloading
    }
    private UpgradeTaskState taskState;

    private static UpgradeAppTask latestRunningTask = null;
    private int progress = 0;

    private UpgradeAppTask() {
        taskState = UpgradeTaskState.notRunning;
    }

    public static UpgradeAppTask getInstance() {
        if (latestRunningTask == null ||
                latestRunningTask.getStatus() == Status.FINISHED) {
            latestRunningTask = new UpgradeAppTask();
        }
        return latestRunningTask;
    }

    public static UpgradeAppTask getRunningInstance() {
        if (latestRunningTask != null &&
                latestRunningTask.getStatus() == Status.RUNNING) {
            return latestRunningTask;
        }
        return null;
    }

    @Override
    protected final Boolean doInBackground(String... params) {
        taskState = UpgradeTaskState.checking;
        while (progress < 101) {
            SystemClock.sleep(500);
            publishProgress(new int[]{progress++, 100});
        }
        return true;
    }

    @Override
    protected void onProgressUpdate(int[]... values) {
        super.onProgressUpdate(values);

        if (taskListener != null) {
            taskListener.processTaskUpdate(values);
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if (taskListener != null) {
            taskListener.processTaskResult(result);
        }

        latestRunningTask = null;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        latestRunningTask = null;
    }

    public UpgradeTaskState getUprgradeState() {
        return taskState;
    }

    public void registerTaskListener(TaskListener<int[], Boolean> listener) throws TaskListenerException {
        if (taskListener != null) {
            throw new TaskListenerException("This " + TAG + " was already registered with a TaskListener");
        }
        taskListener = listener;
    }

    public void unregisterTaskListener(TaskListener<int[], Boolean> listener) throws TaskListenerException {
        if (listener != taskListener) {
            throw new TaskListenerException("The provided listener wasn't registered with this " + TAG);
        }
        taskListener = null;
    }
}

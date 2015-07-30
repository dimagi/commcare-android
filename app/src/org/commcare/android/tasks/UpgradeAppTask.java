package org.commcare.android.tasks;

import android.os.SystemClock;

import org.commcare.android.tasks.templates.ManagedAsyncTask;

public class UpgradeAppTask extends ManagedAsyncTask<String, int[], Boolean> {
    private static final String TAG = UpgradeAppTask.class.getSimpleName();

    private TaskListener<int[], Boolean> taskListener = null;

    private static UpgradeAppTask singletonRunningInstance = null;
    private int progress = 0;

    private UpgradeAppTask() {
    }

    public static UpgradeAppTask getInstance() {
        if (singletonRunningInstance == null ||
                singletonRunningInstance.getStatus() == Status.FINISHED) {
            singletonRunningInstance = new UpgradeAppTask();
        }
        return singletonRunningInstance;
    }

    public static UpgradeAppTask getRunningInstance() {
        if (singletonRunningInstance != null &&
                singletonRunningInstance.getStatus() == Status.RUNNING) {
            return singletonRunningInstance;
        }
        return null;
    }

    @Override
    protected final Boolean doInBackground(String... params) {
        while (progress < 101) {
            if (isCancelled()) {
                SystemClock.sleep(3000);
                return false;
            }

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

        singletonRunningInstance = null;
    }

    @Override
    protected void onCancelled(Boolean result) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            super.onCancelled(result);
        } else {
            super.onCancelled();
        }

        if (taskListener != null) {
            taskListener.processTaskCancel(result);
        }

        singletonRunningInstance = null;
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

    public int getProgress() {
        return progress;
    }
}

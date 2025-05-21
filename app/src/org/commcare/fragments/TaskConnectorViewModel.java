package org.commcare.fragments;

import android.content.Context;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.ViewModel;

import org.commcare.activities.CommCareActivity;
import org.commcare.tasks.templates.CommCareTask;

/**
 * Hold a reference to current task to report its progress and results.
 * An activity is responsible to attach and deattach itself to the current task
 * by calling related hooks in this class.
 */
public class TaskConnectorViewModel<R> extends ViewModel {

    @Nullable
    private CommCareTask<?, ?, ?, R> currentTask;

    private WakeLock wakelock;

    /**
     * Must be called from the created activity that might want to attach to an existing task
     * @param context
     */
    public void attach(Context context) {
        if (context instanceof CommCareActivity) {
            if (isCurrentTaskRunning()) {
                CommCareActivity activity = (CommCareActivity)context;
                if (currentTask != null) {
                    this.currentTask.connect(activity);
                }
            }
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        releaseWakeLock();
    }

    public boolean isCurrentTaskRunning() {
        return this.currentTask != null &&
                this.currentTask.getStatus() == AsyncTask.Status.RUNNING;
    }

    /**
     * Detaches the current Task from the existing activity connector.
     * An activity should call this method before destroying to release the current connector.
     */
    public void detach() {
        if (currentTask != null) {
            currentTask.disconnect();
        }
        releaseWakeLock();
    }

    public void cancelTask() {
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask.tryAbort();
        }
    }

    private synchronized void acquireWakeLock(CommCareActivity activity) {
        int lockLevel = activity.getWakeLockLevel();
        if (lockLevel != CommCareTask.DONT_WAKELOCK) {
            releaseWakeLock();

            PowerManager pm = (PowerManager)activity.getSystemService(Context.POWER_SERVICE);
            wakelock = pm.newWakeLock(lockLevel, "CommCareLock");
            wakelock.acquire();
        }
    }

    public synchronized void releaseWakeLock() {
        if (wakelock != null && wakelock.isHeld()) {
            wakelock.release();
        }
        wakelock = null;
    }

    public void connectTask(CommCareTask<?, ?, ?, R> task, CommCareActivity activity) {
        acquireWakeLock(activity);
        this.currentTask = task;
    }

    @VisibleForTesting
    public CommCareTask getCurrentTask() {
        return currentTask;
    }
}

package org.commcare.android.framework;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.Fragment;
import android.util.Log;

import org.commcare.android.tasks.templates.CommCareTask;

/**
 * Hold a reference to the parent Activity so we can report the
 * task's current progress and results. The Android framework
 * will pass us a reference to the newly created Activity after
 * each configuration change.
 *
 * @author ctsims
 */
public class StateFragment<R> extends Fragment {
    private CommCareActivity<R> boundActivity;
    private CommCareActivity<R> lastActivity;

    private CommCareTask<?, ?, ?, R> currentTask;

    private WakeLock wakelock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof CommCareActivity) {
            this.boundActivity = (CommCareActivity)context;
            this.boundActivity.stateHolder = this;

            if (isCurrentTaskRunning()) {
                this.currentTask.connect(boundActivity);
            }
        }
    }

    private boolean isCurrentTaskRunning() {
        return this.currentTask != null &&
                this.currentTask.getStatus() == AsyncTask.Status.RUNNING;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (this.boundActivity != null) {
            lastActivity = boundActivity;
        }

        if (currentTask != null) {
            Log.i("CommCareUI", "Detaching activity from current task: " + this.currentTask);
            currentTask.disconnect();
            releaseWakeLock();
        }
    }

    public void cancelTask() {
        if (currentTask != null) {
            currentTask.cancel(false);
        }
    }

    private synchronized void acquireWakeLock() {
        int lockLevel = boundActivity.getWakeLockLevel();
        if (lockLevel != CommCareTask.DONT_WAKELOCK) {
            releaseWakeLock();

            PowerManager pm = (PowerManager) boundActivity.getSystemService(Context.POWER_SERVICE);
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

    public CommCareActivity getPreviousState() {
        return lastActivity;
    }

    public void connectTask(CommCareTask<?, ?, ?, R> task) {
        acquireWakeLock();
        this.currentTask = task;
    }
}

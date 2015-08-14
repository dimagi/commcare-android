package org.commcare.android.framework;

import org.commcare.android.tasks.templates.CommCareTask;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.Fragment;
import android.util.Log;

/**
 * Hold a reference to the parent Activity so we can report the
 * task's current progress and results. The Android framework
 * will pass us a reference to the newly created Activity after
 * each configuration change.
 *
 * @author ctsims
 */
public class StateFragment extends Fragment {
    private CommCareActivity boundActivity;
    private CommCareActivity lastActivity;

    private CommCareTask currentTask;

    private WakeLock wakelock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (activity instanceof CommCareActivity) {
            this.boundActivity = (CommCareActivity)activity;
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

            PowerManager pm = (PowerManager)boundActivity.getSystemService(Context.POWER_SERVICE);
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

    public void connectTask(CommCareTask task) {
        acquireWakeLock();
        this.currentTask = task;
    }
}

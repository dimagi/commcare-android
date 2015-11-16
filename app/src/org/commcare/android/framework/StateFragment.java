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
            if (isCurrentTaskRunning()) {
                CommCareActivity activity = (CommCareActivity)context;
                // connecting to a task requires the activity's state holder to
                // be set; which we're in the middle of, so take a shortcut
                activity.stateHolder = this;
                this.currentTask.connect(activity);
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

    private synchronized void acquireWakeLock(CommCareActivity activity) {
        int lockLevel = activity.getWakeLockLevel();
        if (lockLevel != CommCareTask.DONT_WAKELOCK) {
            releaseWakeLock();

            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
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
}

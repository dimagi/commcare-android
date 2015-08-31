package org.commcare.android.tasks.templates;

import android.os.AsyncTask;

import java.util.ArrayList;

/**
 * AsyncTask that is registered, enabling the task to be cancelled if the
 * session ends. Useful since the session ending might close resources that the
 * task depends on to proceed.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */

public abstract class ManagedAsyncTask<A, B, C> extends AsyncTask<A, B, C> {

    /**
     * List of running tasks. Tasks add/remove themselves automatically upon
     * start, cancellation, and completion.
     */
    private static final ArrayList<ManagedAsyncTask<?, ?, ?>> livingTasks =
            new ArrayList<ManagedAsyncTask<?, ?, ?>>();

    /**
     * Call cancel on all tasks and then wipe the living task list.
     */
    public static void cancelTasks() {
        synchronized (livingTasks) {
            for (AsyncTask task : livingTasks) {
                task.cancel(true);
            }
            livingTasks.clear();
        }
    }

    /**
     * Before executing add the task to list of managed tasks.
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        synchronized (livingTasks) {
            livingTasks.add(this);
        }
    }

    /**
     * On execution completion remove task from managed task list.
     */
    @Override
    protected void onPostExecute(C result) {
        super.onPostExecute(result);

        synchronized (livingTasks) {
            livingTasks.remove(this);
        }
    }

    /**
     * On task cancellation remove task from managed task list.
     */
    @Override
    protected void onCancelled() {
        super.onCancelled();

        synchronized (livingTasks) {
            livingTasks.remove(this);
        }
    }
}

package org.commcare.tasks.templates;

import android.os.AsyncTask;
import android.os.Build;

import java.util.ArrayList;

/**
 * AsyncTask that is registered, enabling the task to be cancelled if the
 * session ends. Useful since the session ending might close resources that the
 * task depends on to proceed.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class ManagedAsyncTask<Params, Progress, Result>
        extends AsyncTask<Params, Progress, Result> {

    /**
     * List of running tasks. Tasks add/remove themselves automatically upon
     * start, cancellation, and completion.
     */
    private static final ArrayList<ManagedAsyncTask<?, ?, ?>> livingTasks =
            new ArrayList<>();

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
    protected void onPostExecute(Result result) {
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

    /**
     * Uses true parallelization to execute async tasks, requires extreme care
     * with data synchronization!
     */
    public void executeParallel(Params... params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        } else {
            execute(params);
        }
    }
}

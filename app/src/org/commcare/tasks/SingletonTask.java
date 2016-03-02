package org.commcare.tasks;

import org.commcare.tasks.templates.ManagedAsyncTask;

/**
 * Base implementation for a singleton task that reports progress and results
 * through a TaskListener.  Handles clearing pointer to static task via
 * extender's implementation of clearTaskInstance.
 */
public abstract class SingletonTask<Params, Progress, Result>
        extends ManagedAsyncTask<Params, Progress, Result> {

    protected static String TAG;
    private TaskListener<Progress, Result> taskListener = null;

    /**
     * Clears pointer to the singleton class instance in a thread-safe manner
     */
    protected abstract void clearTaskInstance();

    @Override
    protected void onProgressUpdate(Progress... values) {
        super.onProgressUpdate(values);

        if (taskListener != null) {
            taskListener.handleTaskUpdate(values);
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        super.onPostExecute(result);

        if (taskListener != null) {
            taskListener.handleTaskCompletion(result);
        }

        clearTaskInstance();
    }

    @Override
    protected void onCancelled(Result result) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            super.onCancelled(result);
        } else {
            super.onCancelled();
        }

        if (taskListener != null) {
            taskListener.handleTaskCancellation(result);
        }

        clearTaskInstance();
    }

    /**
     * Start reporting progress with a listener process.
     *
     * @throws TaskListenerRegistrationException If this task was already
     *                                           registered with a listener
     */
    public void registerTaskListener(TaskListener<Progress, Result> listener)
            throws TaskListenerRegistrationException {
        if (taskListener != null) {
            throw new TaskListenerRegistrationException("This " + TAG +
                    " was already registered with a TaskListener");
        }
        taskListener = listener;
    }

    /**
     * Stop reporting progress with a listener process
     *
     * @throws TaskListenerRegistrationException If this task wasn't registered
     *                                           with the unregistering listener.
     */
    public void unregisterTaskListener(TaskListener<Progress, Result> listener)
            throws TaskListenerRegistrationException {
        if (listener != taskListener) {
            throw new TaskListenerRegistrationException("The provided listener wasn't " +
                    "registered with this " + TAG);
        }
        taskListener = null;
    }
}

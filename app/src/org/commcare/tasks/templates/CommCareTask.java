package org.commcare.tasks.templates;

import android.os.AsyncTask;
import android.util.Log;

import org.acra.ACRA;
import org.javarosa.core.services.Logger;

/**
 * @author ctsims
 */
public abstract class CommCareTask<Params, Progress, Result, Receiver>
        extends ManagedAsyncTask<Params, Progress, Result> {
    protected static String TAG;

    public static final int GENERIC_TASK_ID = 32;
    public static final int DONT_WAKELOCK = -1;

    private final Object connectorLock = new Object();
    private CommCareTaskConnector<Receiver> connector;
    private boolean canDismissOnCancel;
    private boolean showCancelButton;

    private Exception unknownError;

    protected int taskId = GENERIC_TASK_ID;

    //Wait for 2 seconds for something to reconnnect for now (very high)
    private static final int ALLOWABLE_CONNECTOR_ACQUISITION_DELAY = 2000;

    public CommCareTask() {
        TAG = CommCareTask.class.getSimpleName();
    }

    @Override
    protected final Result doInBackground(Params... params) {
        //Never have to wrap the entirety of your task.
        try {
            enableCancelButton();
            setup(params);
            enableDismissDialogOnCancel();

            if (isCancelled()) {
                return null;
            }

            volatileWork(params);

            if (isCancelled()) {
                return null;
            }

            disableCancelButton();

            return commit(params);
        } catch (Exception e) {
            Logger.log(TAG, e.getMessage());
            e.printStackTrace();

            // Report to unified crash report dashboard
            ACRA.getErrorReporter().handleException(e);

            // Save error for reporting during post-execute
            unknownError = e;

            return null;
        }
    }

    protected void setup(Params... params) {
    }

    protected void volatileWork(Params... params) {
    }

    protected Result commit(Params... params) {
        return doTaskBackground(params);
    }

    private void enableCancelButton() {
        // call UI thread to enable cancel button on dialog
        showCancelButton = true;
        publishProgress();
    }

    private void enableDismissDialogOnCancel() {
        canDismissOnCancel = true;
    }

    private void disableCancelButton() {
        canDismissOnCancel = false;
        showCancelButton = false;
        // call UI thread to disable cancel button on dialog
        publishProgress();
    }

    public boolean canDismissOnCancel() {
        // UI thread calls this to see if they should dismiss blocking dialog and detach from task
        return canDismissOnCancel;
    }

    /**
     * Catch-wrapped computation to be performed in background thread.
     * Dispatched by doInBackground
     */
    protected abstract Result doTaskBackground(Params... params);

    @Override
    protected void onCancelled() {
        super.onCancelled();

        synchronized (connectorLock) {
            CommCareTaskConnector<Receiver> connector = getConnector();

            if (connector != null) {
                connector.startTaskTransition();
                connector.stopBlockingForTask(getTaskId());
                connector.taskCancelled();
                connector.stopTaskTransition();
            }
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        super.onPostExecute(result);

        synchronized (connectorLock) {
            //TODO: extend blocking here?
            CommCareTaskConnector<Receiver> connector = getConnector();
            if (connector != null) {
                connector.startTaskTransition();
                connector.stopBlockingForTask(taskId);
                if (unknownError != null) {
                    deliverError(connector.getReceiver(), unknownError);
                } else {
                    deliverResult(connector.getReceiver(), result);
                }
                connector.stopTaskTransition();
            }
        }
    }

    protected abstract void deliverResult(Receiver receiver, Result result);

    protected abstract void deliverUpdate(Receiver receiver, Progress... update);

    protected abstract void deliverError(Receiver receiver, Exception e);

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        synchronized (connectorLock) {
            CommCareTaskConnector<Receiver> connector = getConnector();
            if (connector != null) {
                connector.startBlockingForTask(getTaskId());
            }
        }
    }

    @Override
    protected void onProgressUpdate(Progress... values) {
        super.onProgressUpdate(values);
        synchronized (connectorLock) {
            CommCareTaskConnector<Receiver> connector = getConnector(false);
            if (connector != null) {
                if (values.length == 0) {
                    connector.setTaskCancelable(showCancelButton);
                } else {
                    this.deliverUpdate(connector.getReceiver(), values);
                }
            }
        }
    }

    private CommCareTaskConnector<Receiver> getConnector() {
        return getConnector(true);
    }

    private CommCareTaskConnector<Receiver> getConnector(boolean required) {
        //So there might have been some transfer of ownership happening.
        //We wanna hold off on anything that requires the connector
        //until there is one present, up until some specified limit
        long requested = System.currentTimeMillis();
        while (System.currentTimeMillis() - requested < ALLOWABLE_CONNECTOR_ACQUISITION_DELAY) {
            //See if we've gotten a connector
            synchronized (connectorLock) {
                if (connector != null) {
                    return connector;
                } else {
                    //We might just be updating progress or something
                    if (!required) {
                        return null;
                    }
                }
            }
        }
        //Otherwise we're orphaned and we should cancel
        synchronized (connectorLock) {
            //Ok, so check one last time (so we can lock the connection still and prevent
            //something from connecting in the mean time
            if (connector != null) {
                return connector;
            }

            if (this.getStatus() == AsyncTask.Status.RUNNING && taskId != -1) {
                // If the connector is null and the task is associated with a
                // dialog/activity (i.e. task id != -1) then cancel because the
                // task isn't expected to live past the associated
                // dialog/activity
                Log.d(TAG, "Cancelling " + TAG + " because the activity it was connected to is gone");
                this.cancel(false);
            }

            return null;
        }
    }

    public void connect(CommCareTaskConnector<Receiver> connector) {
        synchronized (connectorLock) {
            //TODO: Maybe notify the old thing that we're disconnecting?
            this.connector = connector;
            this.connector.connectTask(this);
        }
    }

    protected void transitionPhase(int newTaskId) {
        synchronized (connectorLock) {
            if (newTaskId != taskId) {
                CommCareTaskConnector<Receiver> connector = this.getConnector(true);
                if (connector != null) {
                    connector.stopBlockingForTask(taskId);
                    connector.startBlockingForTask(newTaskId);
                    this.taskId = newTaskId;
                }
            }
        }
    }

    public int getTaskId() {
        return taskId;
    }

    /**
     * Disconnect this task from its current connector.
     * Used when the user interface has to give up its hook to the
     * current task.
     */
    public void disconnect() {
        synchronized (connectorLock) {
            connector = null;
        }
    }
}

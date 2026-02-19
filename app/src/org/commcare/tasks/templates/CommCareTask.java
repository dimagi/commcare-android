package org.commcare.tasks.templates;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.logging.UserCausedRuntimeException;
import org.commcare.services.NetworkNotificationService;
import org.commcare.utils.CrashUtil;
import org.javarosa.core.services.Logger;

import static org.commcare.services.NetworkNotificationService.PROGRESS_TEXT_KEY_INTENT_EXTRA;
import static org.commcare.services.NetworkNotificationService.START_NOTIFICATION_ACTION;
import static org.commcare.services.NetworkNotificationService.STOP_NOTIFICATION_ACTION;
import static org.commcare.services.NetworkNotificationService.TASK_ID_INTENT_EXTRA;
import static org.commcare.services.NetworkNotificationService.UPDATE_PROGRESS_NOTIFICATION_ACTION;

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

    private Exception unknownError;

    protected int taskId = GENERIC_TASK_ID;
    protected boolean runNotificationService = false;
    protected String notificationServiceProgressTextKey = null;

    //Wait for 2 seconds for something to reconnnect for now (very high)
    private static final int ALLOWABLE_CONNECTOR_ACQUISITION_DELAY = 2000;

    private int connectionTimout = ALLOWABLE_CONNECTOR_ACQUISITION_DELAY;

    protected CommCareTask() {
        TAG = CommCareTask.class.getSimpleName();
    }

    @Override
    protected final Result doInBackground(Params... params) {
        //Never have to wrap the entirety of your task.
        try {
            return doTaskBackground(params);
        } catch (Exception e) {
            if (!(e instanceof UserCausedRuntimeException)) {
                // Report crashes we know weren't caused by user misconfiguration
                Logger.exception("Error during task execution: ", e);
            } else {
                Logger.log(TAG, "Error during task execution: " + e.getMessage());
            }

            // Save error for reporting during post-execute
            unknownError = e;
            return null;
        }
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
                handleCancellation(connector.getReceiver());
                connector.stopTaskTransition(taskId);
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
                connector.stopTaskTransition(taskId);
            }
        }

        if (NetworkNotificationService.Companion.isServiceRunning()) {
            CommCareApplication.instance().startForegroundService(getNotificationStopIntent());
        }
    }

    protected abstract void deliverResult(Receiver receiver, Result result);

    protected abstract void deliverUpdate(Receiver receiver, Progress... update);

    protected abstract void deliverError(Receiver receiver, Exception e);

    protected void handleCancellation(Receiver receiver) {
        // Do nothing by default
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        synchronized (connectorLock) {
            CommCareTaskConnector<Receiver> connector = getConnector();
            if (connector != null) {
                connector.startBlockingForTask(getTaskId());
            }
        }

        if (shouldRunNetworkNotificationService()) {
            CommCareApplication.instance().startForegroundService(getNotificationStartIntent());
        }
    }

    private boolean shouldRunNetworkNotificationService() {
        return !CommCareApplication.isSessionActive() && runNotificationService &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    private Intent getNetworkServiceBaseIntent() {
        return new Intent(CommCareApplication.instance(), NetworkNotificationService.class)
                .putExtra(TASK_ID_INTENT_EXTRA, taskId);
    }

    @Override
    protected void onProgressUpdate(Progress... values) {
        super.onProgressUpdate(values);
        synchronized (connectorLock) {
            CommCareTaskConnector<Receiver> connector = getConnector(false);
            if (connector != null) {
                this.deliverUpdate(connector.getReceiver(), values);
            }
        }

        if (NetworkNotificationService.Companion.isServiceRunning()) {
            CommCareApplication.instance().startForegroundService(getNotificationUpdateIntent());
        }
    }

    private Intent getNotificationStopIntent() {
        return getNetworkServiceBaseIntent().setAction(STOP_NOTIFICATION_ACTION);
    }

    private Intent getNotificationStartIntent() {
        return getNetworkServiceBaseIntent().setAction(START_NOTIFICATION_ACTION);
    }

    private Intent getNotificationUpdateIntent() {
        Intent intent = getNetworkServiceBaseIntent().setAction(UPDATE_PROGRESS_NOTIFICATION_ACTION);
        if (notificationServiceProgressTextKey != null) {
            intent.putExtra(PROGRESS_TEXT_KEY_INTENT_EXTRA, notificationServiceProgressTextKey);
        }
        return intent;
    }

    /**
     * Adjust the time that the task will wait for a connector before cancelling
     * or proceeding (if headless)
     */
    protected void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimout = connectionTimeout;
    }

    private CommCareTaskConnector<Receiver> getConnector() {
        return getConnector(true);
    }

    private CommCareTaskConnector<Receiver> getConnector(boolean required) {
        //So there might have been some transfer of ownership happening.
        //We wanna hold off on anything that requires the connector
        //until there is one present, up until some specified limit
        long requested = System.currentTimeMillis();
        while (System.currentTimeMillis() - requested < connectionTimout) {
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

    /**
     * Attempts to kill long running processes prematurely in the task
     */
    public void tryAbort() {
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

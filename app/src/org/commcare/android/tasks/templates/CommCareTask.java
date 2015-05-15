package org.commcare.android.tasks.templates;

import android.os.AsyncTask;

/**
 * @author ctsims
 *
 */
public abstract class CommCareTask<A, B, C, R> extends ManagedAsyncTask<A, B, C> {
    
    public static final int GENERIC_TASK_ID = 32;
    
    private Object connectorLock = new Object();
    private CommCareTaskConnector<R> connector;
    
    private boolean isLostOrphan = false;
    private Exception unknownError;
    
    protected int taskId = GENERIC_TASK_ID;
    
    public CommCareTask() {
    }

    /* (non-Javadoc)
     * @see android.os.AsyncTask#doInBackground(Params[])
     */
    @Override
    protected final C doInBackground(A... params) {
        //Never have to wrap the entirety of your task.
        try {
            return doTaskBackground(params);
        } catch(Exception e) {
            e.printStackTrace();
            unknownError = e;
            return null;
        }
    }

    /**
     * Catch-wrapped computation to be performed in background thread.
     * Dispatched by doInBackground
     */
    protected abstract C doTaskBackground(A... params);

    /* (non-Javadoc)
     * @see android.os.AsyncTask#onCancelled()
     */
    @Override
    protected void onCancelled() {
        super.onCancelled();
        synchronized(connectorLock) {
            CommCareTaskConnector<R> connector = getConnector();
            if(connector == null) {
                //TODO: FailedConnection
                return;
            }
            connector.startTaskTransition();
            connector.stopBlockingForTask(getTaskId());
            connector.taskCancelled(getTaskId());
            connector.stopTaskTransition();
        }
    }

    /* (non-Javadoc)
     * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
     */
    @Override
    protected void onPostExecute(C result) {
        super.onPostExecute(result);
        synchronized(connectorLock) {
            //TODO: extend blocking here?
            CommCareTaskConnector<R> connector = getConnector();
            if(connector == null) {
                //TODO: FailedConnection
                return;
            }
            connector.startTaskTransition();
            connector.stopBlockingForTask(getTaskId());
            if(unknownError != null) { 
                deliverError(connector.getReceiver(), unknownError);
                return;
            }
            this.deliverResult(connector.getReceiver(), result);
            connector.stopTaskTransition();
        }
    }

    protected abstract void deliverResult(R receiver, C result);
    
    protected abstract void deliverUpdate(R receiver, B... update);
    
    protected abstract void deliverError(R receiver, Exception e);

    /* (non-Javadoc)
     * @see android.os.AsyncTask#onPreExecute()
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        synchronized(connectorLock) {
            CommCareTaskConnector<R> connector = getConnector();
            if(connector == null) {
                //TODO: FailedConnection
                return;
            }
            connector.startBlockingForTask(getTaskId());
        }
    }

    /* (non-Javadoc)
     * @see android.os.AsyncTask#onProgressUpdate(Progress[])
     */
    @Override
    protected void onProgressUpdate(B... values) {
        super.onProgressUpdate(values);
        synchronized(connectorLock) {
            CommCareTaskConnector<R> connector = getConnector(false);
            if(connector != null) {
                this.deliverUpdate(connector.getReceiver(), values);
            }
        }
    }
    
    //Wait for 2 seconds for something to reconnnect for now (very high)
    private int allowableDelay = 2000;
    
    
    protected CommCareTaskConnector<R> getConnector() {
        return getConnector(true);
    }
    protected CommCareTaskConnector<R> getConnector(boolean required) {
        //So there might have been some transfer of ownership happening.
        //We wanna hold off on anything that requires the connector
        //until there is one present, up until some specified limit
        long requested = System.currentTimeMillis();
        while(System.currentTimeMillis() - requested < allowableDelay) {
            //See if we've gotten a connector
            synchronized(connectorLock) {
                if(connector != null) {
                    return connector;
                } else {
                    //We might just be updating progress or something
                    if(!required) {
                        return null;
                    }
                }
            }
        }
        //Otherwise we're orphaned and we should cancel
        synchronized(connectorLock) {
            //Ok, so check one last time (so we can lock the connection still and prevent
            //something from connecting in the mean time
            if(connector != null) { return connector; }
            
            //Otherwise, cancel if possible
            if(this.getStatus() == AsyncTask.Status.RUNNING) {
                this.cancel(false);
            }
            //Mark this as a lost orphan
            this.isLostOrphan = true;
            
            //and return null;
            return null;
        }
    }
    
    public void connect(CommCareTaskConnector<R> connector) {
        synchronized(connectorLock) {
            //TODO: Maybe notify the old thing that we're disconnecting?
            this.connector = connector;
            this.connector.connectTask(this);
        }
    }
    
    protected void transitionPhase(int newTaskId) {
        synchronized(connectorLock) {
            CommCareTaskConnector<R> connector = this.getConnector(true);
            connector.stopBlockingForTask(taskId);
            connector.startBlockingForTask(newTaskId);
            this.taskId = newTaskId;
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
        synchronized(connectorLock) {
            connector = null;
        }
    }
}

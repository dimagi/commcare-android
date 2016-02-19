package org.commcare.android.tasks.templates;

/**
 * @author ctsims
 */
public interface CommCareTaskConnector<R> {

    /**
     * IMPORTANT: Any implementing class of CommCareTaskConnector should be
     * implemented such that it will only automatically manage the dialog of a
     * connected task IF the task id is non-negative. If the user does NOT
     * want to implement a dialog, or is implementing the dialog in a
     * different way, they should be able to use a negative task id in order
     * to avoid this.
     */
    <A, B, C> void connectTask(CommCareTask<A, B, C, R> task);

    /**
     * Should call showProgressDialog() for a task if its id is non-negative,
     * and if there is no dialog already showing
     */
    void startBlockingForTask(int id);

    /**
     * Should call dismissProgressDialog() for a task if its id is
     * non-negative, and if shouldDismissDialog is true (this flag should be
     * controlled by the task transition)
     */
    void stopBlockingForTask(int id);

    void taskCancelled();

    R getReceiver();

    /**
     * Should be called at the beginning of onPostExecute or onCancelled for
     * any CommCareTask, indicating that we are starting a potential transition
     * from one task to another (if the end of the first task triggers the
     * start of another)
     */
    void startTaskTransition();

    /**
     * Should be called at the end of onPreExecute or onCancelled for any
     * CommCareTask, indicating that we are ending a potential transition from
     * one task to another
     */
    void stopTaskTransition();
}

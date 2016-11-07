package org.commcare.tasks;

/**
 * Created by amstone326 on 5/10/16.
 */
public interface PullTaskResultReceiver {

    void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndError,
                              boolean userTriggeredSync, boolean formsToSend);

    void handlePullTaskUpdate(Integer... update);

    void handlePullTaskError();

}

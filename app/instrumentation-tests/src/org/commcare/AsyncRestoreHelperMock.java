package org.commcare;

import org.commcare.network.RemoteDataPullResponse;
import org.commcare.tasks.AsyncRestoreHelper;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;

/**
 * @author $|-|!˅@M
 */
public class AsyncRestoreHelperMock extends AsyncRestoreHelper {

    private static boolean retry;
    private static boolean serverProgressReporting;

    public AsyncRestoreHelperMock(DataPullTask task) {
        super(task);
    }

    @Override
    protected ResultAndError<DataPullTask.PullTaskResult> handleRetryResponseCode(RemoteDataPullResponse response) {
        retry = true;
        return super.handleRetryResponseCode(response);
    }

    @Override
    protected void startReportingServerProgress() {
        serverProgressReporting = true;
        super.startReportingServerProgress();
    }

    public static boolean isRetryCalled() {
        return retry;
    }

    public static boolean isServerProgressReportingStarted() {
        return serverProgressReporting;
    }
}
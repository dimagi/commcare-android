package org.commcare.android.tasks.network;

import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.tasks.DataPullTask;

import java.io.IOException;

public class DataPullResponseFactory implements PullResponseBuilder {
    public DataPullResponseFactory() {
    }

    @Override
    public RemoteDataPullResponse buildResponse(DataPullTask task, HttpRequestGenerator requestor, String server, boolean useRequestFlags) throws IOException {
        return new RemoteDataPullResponse(task, requestor, server, useRequestFlags);
    }
}

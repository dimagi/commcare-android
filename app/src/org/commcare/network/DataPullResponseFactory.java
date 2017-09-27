package org.commcare.network;

import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.tasks.DataPullTask;

import java.io.IOException;

import retrofit2.Response;

/**
 * Builds data pulling object that requests remote data and handles the response.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public enum DataPullResponseFactory implements DataPullRequester {

    INSTANCE;

    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      CommcareRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken) throws IOException {
        Response response = requestor.makeCaseFetchRequest(server, includeSyncToken);
        return new RemoteDataPullResponse(task, response);
    }

    @Override
    public CommcareRequestGenerator getHttpGenerator(String username, String password, String userId) {
        return new CommcareRequestGenerator(username, password, userId);
    }
}

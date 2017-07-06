package org.commcare.network;

import org.apache.http.HttpResponse;
import org.commcare.interfaces.HttpRequestEndpoints;
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
                                                      HttpRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken) throws IOException {
        Response response = requestor.makeCaseFetchRequest(includeSyncToken);
        return new RemoteDataPullResponse(task, response);
    }

    @Override
    public HttpRequestGenerator getHttpGenerator(String username, String password, String userId) {
        return new HttpRequestGenerator(username, password, userId);
    }
}

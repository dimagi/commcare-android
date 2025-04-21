package org.commcare.network;

import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.tasks.DataPullTask;

import java.io.IOException;

import okhttp3.ResponseBody;
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
                                                      boolean includeSyncToken,
                                                      boolean skipFixtures) throws IOException {
        Response<ResponseBody> response = requestor.makeCaseFetchRequest(server, includeSyncToken, skipFixtures);
        return new RemoteDataPullResponse(task, response);
    }

    @Override
    public CommcareRequestGenerator getHttpGenerator(String username, String password, String userId) {
        return new CommcareRequestGenerator(username, password, userId);
    }
}

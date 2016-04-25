package org.commcare.tasks.network;

import org.apache.http.HttpResponse;
import org.commcare.android.mocks.HttpRequestEndpointsMock;
import org.commcare.interfaces.HttpRequestEndpoints;
import org.commcare.network.DataPullRequester;
import org.commcare.network.RemoteDataPullResponse;
import org.commcare.tasks.DataPullTask;

import java.io.IOException;

/**
 * Builds data pull requester that gets data from a local reference.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DebugDataPullResponseFactory implements DataPullRequester {
    private final String xmlPayloadReference;

    public DebugDataPullResponseFactory(String xmlPayloadReference) {
        this.xmlPayloadReference = xmlPayloadReference;
    }

    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      HttpRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken) throws IOException {
        HttpResponse response = requestor.makeCaseFetchRequest(server, includeSyncToken);
        return new DebugDataPullResponse(xmlPayloadReference, response);
    }

    @Override
    public HttpRequestEndpoints getHttpGenerator(String username, String password) {
        return new HttpRequestEndpointsMock();
    }
}

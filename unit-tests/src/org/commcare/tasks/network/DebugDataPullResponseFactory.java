package org.commcare.tasks.network;

import org.apache.http.HttpResponse;
import org.commcare.android.mocks.HttpRequestEndpointsMock;
import org.commcare.interfaces.HttpRequestEndpoints;
import org.commcare.network.DataPullRequester;
import org.commcare.network.RemoteDataPullResponse;
import org.commcare.tasks.DataPullTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds data pull requester that gets data from a local reference.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public enum DebugDataPullResponseFactory implements DataPullRequester {
    INSTANCE;

    // data pull requests will pop off and use the top reference in this list
    private final List<String> xmlPayloadReferences = new ArrayList<>();

    public static void setRequestPayloads(String[] payloadReferences) {
        INSTANCE.xmlPayloadReferences.clear();
        Collections.addAll(INSTANCE.xmlPayloadReferences, payloadReferences);
    }

    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      HttpRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken) throws IOException {
        HttpResponse response = requestor.makeCaseFetchRequest(server, includeSyncToken);
        return new DebugDataPullResponse(xmlPayloadReferences.remove(0), response);
    }

    @Override
    public HttpRequestEndpoints getHttpGenerator(String username, String password) {
        return new HttpRequestEndpointsMock();
    }
}

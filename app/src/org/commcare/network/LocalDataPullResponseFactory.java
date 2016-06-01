package org.commcare.network;

import org.apache.http.HttpResponse;
import org.commcare.interfaces.HttpRequestEndpoints;
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
public class LocalDataPullResponseFactory implements DataPullRequester {
    // data pull requests will pop off and use the top reference in this list
    private final List<String> xmlPayloadReferences = new ArrayList<>();

    public LocalDataPullResponseFactory(String xmlPayloadReference) {
        xmlPayloadReferences.add(xmlPayloadReference);
    }

    public LocalDataPullResponseFactory(String[] payloadReferences) {
        Collections.addAll(xmlPayloadReferences, payloadReferences);
    }

    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      HttpRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken) throws IOException {
        HttpResponse response = requestor.makeCaseFetchRequest(server, includeSyncToken);
        return new LocalDataPullResponse(xmlPayloadReferences.remove(0), response);
    }

    @Override
    public HttpRequestEndpoints getHttpGenerator(String username, String password) {
        return new HttpRequestEndpointsMock();
    }
}

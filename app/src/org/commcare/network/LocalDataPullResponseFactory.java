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
public enum LocalDataPullResponseFactory implements DataPullRequester {
    INSTANCE;

    // data pull requests will pop off and use the top reference in this list
    private final List<String> xmlPayloadReferences = new ArrayList<>();
    private boolean isAsyncRestore;

    public static void setRequestPayloads(String[] payloadReferences) {
        INSTANCE.xmlPayloadReferences.clear();
        Collections.addAll(INSTANCE.xmlPayloadReferences, payloadReferences);
    }

    public static void setAsyncRestore(boolean b) {
        INSTANCE.isAsyncRestore = b;
    }

    // this is what DataPullTask will call when it's being run in a test
    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      HttpRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken) throws IOException {
        HttpResponse response;
        if (isAsyncRestore) {
            response = requestor.makeAsyncCaseFetchRequest();
        } else {
            response = requestor.makeCaseFetchRequest(server, includeSyncToken);
        }
        return new LocalDataPullResponse(xmlPayloadReferences.remove(0), response);
    }

    @Override
    public HttpRequestEndpoints getHttpGenerator(String username, String password) {
        return new HttpRequestEndpointsMock();
    }
}

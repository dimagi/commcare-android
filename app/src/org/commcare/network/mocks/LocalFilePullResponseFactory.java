package org.commcare.network.mocks;

import org.apache.http.HttpResponse;
import org.commcare.interfaces.HttpRequestEndpoints;
import org.commcare.network.DataPullRequester;
import org.commcare.network.HttpRequestEndpointsMock;
import org.commcare.network.RemoteDataPullResponse;
import org.commcare.tasks.DataPullTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds data pull requester that gets data from a local file on the android filesystem.
 *
 * @author Clayton Sims (csims@dimagi.com)
 */
public enum LocalFilePullResponseFactory implements DataPullRequester {
    INSTANCE;

    // data pull requests will pop off and use the top reference in this list
    private final List<File> xmlPayloadReferences = new ArrayList<>();
    public int numTries = 0;

    public static void setRequestPayloads(File[] payloadReferences) {
        INSTANCE.xmlPayloadReferences.clear();
        Collections.addAll(INSTANCE.xmlPayloadReferences, payloadReferences);
    }

    public static int getNumRequestsMade() {
        return INSTANCE.numTries;
    }

    // this is what DataPullTask will call when it's being run in a test
    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      HttpRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken) throws IOException {
        numTries++;
        HttpResponse response = requestor.makeCaseFetchRequest(server, includeSyncToken);
//   todo     return new LocalFilePullResponse(xmlPayloadReferences.remove(0), response);
        return null;
    }

    @Override
    public HttpRequestEndpoints getHttpGenerator(String username, String password, String userId) {
        return new HttpRequestEndpointsMock();
    }
}

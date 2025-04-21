package org.commcare.network;

import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.tasks.DataPullTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Builds data pull requester that gets data from a local CommCare reference.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public enum LocalReferencePullResponseFactory implements DataPullRequester {
    INSTANCE;

    // data pull requests will pop off and use the top reference in this list
    private final List<String> xmlPayloadReferences = new ArrayList<>();
    private int numTries = 0;

    public static void setRequestPayloads(String[] payloadReferences) {
        INSTANCE.xmlPayloadReferences.clear();
        Collections.addAll(INSTANCE.xmlPayloadReferences, payloadReferences);
    }

    public static int getNumRequestsMade() {
        return INSTANCE.numTries;
    }

    // this is what DataPullTask will call when it's being run in a test
    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      CommcareRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken,
                                                      boolean skipFixtures) throws IOException {
        numTries++;
        Response<ResponseBody> response = requestor.makeCaseFetchRequest(server, includeSyncToken, skipFixtures);
        return new LocalReferencePullResponse(xmlPayloadReferences.remove(0), response);
    }

    @Override
    public CommcareRequestEndpoints getHttpGenerator(String username, String password, String userId) {
        return new CommcareRequestEndpointsMock();
    }
}

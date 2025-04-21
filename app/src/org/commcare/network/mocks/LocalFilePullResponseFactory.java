package org.commcare.network.mocks;

import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.network.DataPullRequester;
import org.commcare.network.CommcareRequestEndpointsMock;
import org.commcare.network.RemoteDataPullResponse;
import org.commcare.tasks.DataPullTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Builds data pull requester that gets data from a local file on the android filesystem.
 *
 * @author Clayton Sims (csims@dimagi.com)
 */
public enum LocalFilePullResponseFactory implements DataPullRequester {
    INSTANCE;

    // data pull requests will pop off and use the top reference in this list
    private final List<File> xmlPayloadReferences = new ArrayList<>();

    public static void setRequestPayloads(File[] payloadReferences) {
        INSTANCE.xmlPayloadReferences.clear();
        Collections.addAll(INSTANCE.xmlPayloadReferences, payloadReferences);
    }

    // this is what DataPullTask will call when it's being run in a test
    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      CommcareRequestEndpoints requestor,
                                                      String server,
                                                      boolean includeSyncToken,
                                                      boolean skipFixtures) throws IOException {
        Response<ResponseBody> response = requestor.makeCaseFetchRequest(server, includeSyncToken, skipFixtures);
        return new LocalFilePullResponse(xmlPayloadReferences.remove(0), response);
    }

    @Override
    public CommcareRequestEndpoints getHttpGenerator(String username, String password, String userId) {
        return new CommcareRequestEndpointsMock();
    }
}

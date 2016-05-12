package org.commcare.network;

import org.commcare.tasks.DataPullTask;

import java.io.IOException;

/**
 * Builds data pull requester that gets data from a local reference.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class LocalDataPullResponseFactory implements DataPullRequester {
    private final String xmlPayloadReference;

    public LocalDataPullResponseFactory(String xmlPayloadReference) {
        this.xmlPayloadReference = xmlPayloadReference;
    }

    @Override
    public RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                                      HttpRequestGenerator requestor,
                                                      String server,
                                                      boolean includeSyncToken) throws IOException {
        return new LocalDataPullResponse(xmlPayloadReference);
    }
}

package org.commcare.network;

import org.commcare.interfaces.HttpRequestEndpoints;
import org.commcare.tasks.DataPullTask;

import java.io.IOException;

/**
 * Delegates data pulling requests to allow for modularity in data sources
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface DataPullRequester {
    /**
     * Makes a data pulling request and returns an object for locally caching
     * the response data.
     *
     * @param task             For reporting data download progress
     * @param requestor        For making the data pulling request
     * @param server           Address of the request target
     * @param includeSyncToken Add sync token to the request
     * @return Instance to handle the response data
     */
    RemoteDataPullResponse makeDataPullRequest(DataPullTask task,
                                               HttpRequestEndpoints requestor,
                                               String server,
                                               boolean includeSyncToken) throws IOException;

    HttpRequestEndpoints getHttpGenerator(String username, String password);
}

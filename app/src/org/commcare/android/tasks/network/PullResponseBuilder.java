package org.commcare.android.tasks.network;

import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.tasks.DataPullTask;

import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public interface PullResponseBuilder {
    RemoteDataPullResponse buildResponse(DataPullTask task, HttpRequestGenerator requestor, String server, boolean useRequestFlags) throws IOException;
}

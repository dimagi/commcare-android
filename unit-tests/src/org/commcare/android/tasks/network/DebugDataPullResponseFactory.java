package org.commcare.android.tasks.network;

import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.tasks.DataPullTask;

import java.io.IOException;

public class DebugDataPullResponseFactory implements PullResponseBuilder {
    private final String xmlPayloadReference;

    public DebugDataPullResponseFactory(String xmlPayloadReference) {
        this.xmlPayloadReference = xmlPayloadReference;
    }

    @Override
    public RemoteDataPullResponse buildResponse(DataPullTask task,
                                                HttpRequestGenerator requestor,
                                                String server,
                                                boolean useRequestFlags) throws IOException {
        return new DebugDataPullResponse(xmlPayloadReference);
    }
}

package org.commcare.network;

import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.IOException;
import java.io.InputStream;

import retrofit2.Response;

/**
 * Data pulling requester that gets data from a local CommCare reference.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class LocalReferencePullResponse extends RemoteDataPullResponse {
    private InputStream debugStream;

    public LocalReferencePullResponse(String xmlPayloadReference,
                                      Response response) throws IOException {
        super(null, response);

        try {
            debugStream = ReferenceManager.instance().DeriveReference(xmlPayloadReference).getStream();
        } catch (InvalidReferenceException ire) {
            throw new IOException("No payload available at " + xmlPayloadReference);
        }
    }

    @Override
    protected InputStream getInputStream() {
        return debugStream;
    }
}

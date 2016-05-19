package org.commcare.network;

import org.apache.http.HttpResponse;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.IOException;
import java.io.InputStream;

/**
 * Data pulling requester that gets data from a local reference.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class LocalDataPullResponse extends RemoteDataPullResponse {
    private InputStream debugStream = null;

    public LocalDataPullResponse(String xmlPayloadReference,
                                 HttpResponse response) throws IOException {
        super(null, response);

        try {
            debugStream =
                    ReferenceManager._().DeriveReference(xmlPayloadReference).getStream();
        } catch (InvalidReferenceException ire) {
            throw new IOException("No payload available at " + xmlPayloadReference);
        }
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        return debugStream;
    }

    @Override
    protected long guessDataSize() {
        try {
            //Note: this is really stupid, but apparently you can't 
            //retrieve the size of Assets due to some bullshit, so
            //this is the closest you get.
            return debugStream.available();
        } catch (IOException e) {
            return -1;
        }
    }
}

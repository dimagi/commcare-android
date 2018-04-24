package org.commcare.network.mocks;

import org.commcare.network.RemoteDataPullResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import retrofit2.Response;

/**
 * Data pulling requester that gets data from a local file on the android filesystem.
 *
 * @author Clayton Sims (csims@dimagi.com).
 */
public class LocalFilePullResponse extends RemoteDataPullResponse {
    private InputStream debugStream = null;

    public LocalFilePullResponse(File xmlPayload,
                                 Response response) throws IOException {
        super(null, response);

        try {
            debugStream =
                    new FileInputStream(xmlPayload);
        } catch (IOException ire) {
            throw new IOException("No payload available at " + xmlPayload.toString(), ire);
        }
    }

    @Override
    protected InputStream getInputStream() {
        return debugStream;
    }
}

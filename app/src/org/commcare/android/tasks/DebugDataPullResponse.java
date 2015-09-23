package org.commcare.android.tasks;

import org.apache.http.HttpResponse;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.IOException;
import java.io.InputStream;

public class DebugDataPullResponse extends RemoteDataPullResponse {
    private InputStream debugStream = null;

    public DebugDataPullResponse() throws IOException {
        super(200);

        try {
            debugStream = ReferenceManager._().DeriveReference("jr://asset/payload.xml").getStream();
        } catch(InvalidReferenceException ire) {
            throw new IOException("No payload available at jr://asset/payload.xml");
        }
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        return debugStream;
    }

    @Override
    protected long guessDataSize(HttpResponse response) {
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

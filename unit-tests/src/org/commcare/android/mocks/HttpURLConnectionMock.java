package org.commcare.android.mocks;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpURLConnectionMock extends HttpURLConnection {
    private final int responseCode;

    public HttpURLConnectionMock(URL url, int responseCode) {
        super(url);
        this.responseCode = responseCode;
    }

    @Override
    public void disconnect() {

    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    @Override
    public void connect() throws IOException {

    }

    @Override
    public int getResponseCode() throws IOException {
        return responseCode;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream("".getBytes());
    }
}

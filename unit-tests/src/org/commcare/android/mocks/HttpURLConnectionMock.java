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
    private final InputStream inputStream;
    public final static String ioErrorMessage = "uhh ohh, io error oh";

    private HttpURLConnectionMock(URL url, int responseCode,
                                  InputStream inputStream) {
        super(url);

        this.responseCode = responseCode;
        this.inputStream = inputStream;
    }

    public static HttpURLConnectionMock mockWithStream(URL url, int responseCode,
                                                       InputStream inputStream) {
        return new HttpURLConnectionMock(url, responseCode, inputStream);
    }

    public static HttpURLConnectionMock mockWithEmptyStream(URL url, int responseCode) {
        return new HttpURLConnectionMock(url, responseCode,
                new ByteArrayInputStream("".getBytes()));
    }

    public static HttpURLConnectionMock mockWithErroringStream(URL url, int responseCode) {
        return new HttpURLConnectionMock(url, responseCode, null);
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
        if (inputStream == null) {
            throw new IOException(ioErrorMessage);
        } else {
            return inputStream;
        }
    }
}

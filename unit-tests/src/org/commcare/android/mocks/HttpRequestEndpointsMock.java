package org.commcare.android.mocks;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MultipartEntity;
import org.commcare.interfaces.HttpRequestEndpoints;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpRequestEndpointsMock implements HttpRequestEndpoints{
    public static int caseFetchResponseCode = 500;

    @Override
    public HttpResponse makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws ClientProtocolException, IOException {
        return HttpResponseMock.buildHttpResponseMock(caseFetchResponseCode);
    }

    @Override
    public HttpResponse makeKeyFetchRequest(String baseUri, Date lastRequest) throws ClientProtocolException, IOException {
        throw new RuntimeException("Not yet mocked");
    }

    @Override
    public HttpResponse postData(String url, MultipartEntity entity) throws ClientProtocolException, IOException {
        throw new RuntimeException("Not yet mocked");
    }

    @Override
    public InputStream simpleGet(URL url) throws IOException {
        throw new RuntimeException("Not yet mocked");
    }

}

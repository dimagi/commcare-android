package org.commcare.network;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MultipartEntity;
import org.commcare.interfaces.HttpRequestEndpoints;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Mocks for different types of http requests commcare mobile makes to the server
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpRequestEndpointsMock implements HttpRequestEndpoints {
    private final static List<Integer> caseFetchResponseCodeStack = new ArrayList<>();

    /**
     * Set the response code for the next N requests
     */
    public static void setCaseFetchResponseCodes(Integer[] responseCodes) {
        caseFetchResponseCodeStack.clear();
        Collections.addAll(caseFetchResponseCodeStack, responseCodes);
    }

    @Override
    public HttpResponse makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws ClientProtocolException, IOException {
        int responseCode;
        if (caseFetchResponseCodeStack.size() > 0) {
            responseCode = caseFetchResponseCodeStack.remove(0);
        } else {
            responseCode = 200;
        }
        return HttpResponseMock.buildHttpResponseMock(responseCode);
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

    @Override
    public void abortCurrentRequest() {
        throw new RuntimeException("Not yet mocked");
    }
}

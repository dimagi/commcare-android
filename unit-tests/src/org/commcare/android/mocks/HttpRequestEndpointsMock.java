package org.commcare.android.mocks;

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
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpRequestEndpointsMock implements HttpRequestEndpoints {
    public static List<Integer> caseFetchResponseCodeStack = new ArrayList<>();

    public static void setCaseFetchResponseCodes(Integer[] responseCodes) {
        caseFetchResponseCodeStack.clear();
        Collections.addAll(caseFetchResponseCodeStack, responseCodes);
    }

    @Override
    public HttpResponse makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws ClientProtocolException, IOException {
        return HttpResponseMock.buildHttpResponseMock(caseFetchResponseCodeStack.remove(0));
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

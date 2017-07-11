package org.commcare.network;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MultipartEntity;
import org.commcare.interfaces.HttpRequestEndpoints;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Mocks for different types of http requests commcare mobile makes to the server
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpRequestEndpointsMock implements HttpRequestEndpoints {
    private final static List<Integer> caseFetchResponseCodeStack = new ArrayList<>();
    private static String errorMessagePayload;

    /**
     * Set the response code for the next N requests
     */
    public static void setCaseFetchResponseCodes(Integer[] responseCodes) {
        caseFetchResponseCodeStack.clear();
        Collections.addAll(caseFetchResponseCodeStack, responseCodes);
    }

    /**
     * Set the response body for the next 406 request
     */
    public static void setErrorResponseBody(String body) {
        errorMessagePayload = body;
    }

    // TODO: 07/07/17 Implement mock
    @Override
    public Response<ResponseBody> makeCaseFetchRequest(String baseUri, boolean includeStateFlags)
            throws IOException {
        int responseCode;
        if (caseFetchResponseCodeStack.size() > 0) {
            responseCode = caseFetchResponseCodeStack.remove(0);
        } else {
            responseCode = 200;
        }
        if (responseCode == 202) {
//            return HttpResponseMock.buildHttpResponseMockForAsyncRestore();
            return null;
        } else if (responseCode == 406) {
//            return HttpResponseMock.buildHttpResponseMock(responseCode, new ByteArrayInputStream(errorMessagePayload.getBytes("UTF-8")));
            return null;
        } else {
//            return HttpResponseMock.buildHttpResponseMock(responseCode, null);
            return null;
        }
    }

    @Override
    public Response<ResponseBody> makeKeyFetchRequest(String baseUri, Date lastRequest) throws IOException {
        throw new RuntimeException("Not yet mocked");
    }

    @Override
    public HttpResponse postData(String url, MultipartEntity entity) throws ClientProtocolException, IOException {
        throw new RuntimeException("Not yet mocked");
    }

    @Override
    public Response<ResponseBody> simpleGet(String uri) throws IOException {
        throw new RuntimeException("Not yet mocked");
    }

    @Override
    public void abortCurrentRequest() {
        throw new RuntimeException("Not yet mocked");
    }
}

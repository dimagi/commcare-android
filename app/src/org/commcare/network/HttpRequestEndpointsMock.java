package org.commcare.network;

import org.commcare.core.network.OkHTTPResponseMock;
import org.commcare.interfaces.HttpRequestEndpoints;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
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
            Headers headers = new Headers.Builder()
                    .add("Retry-After", "2")
                    .build();
            return OkHTTPResponseMock.createResponse(202, headers);
        } else if (responseCode == 406) {
            ResponseBody responseBody = ResponseBody.create(MediaType.parse("application/json"), errorMessagePayload);
            return Response.error(responseCode, responseBody);
        } else {
            return OkHTTPResponseMock.createResponse(responseCode);
        }
    }

    @Override
    public Response<ResponseBody> makeKeyFetchRequest(String baseUri, @Nullable Date lastRequest) throws IOException {
        throw new RuntimeException("Not yet mocked");
    }

    @Override
    public Response<ResponseBody> postData(String url, List<MultipartBody.Part> parts) throws IOException {
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

package org.commcare.android.mocks;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;

import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.core.network.OkHTTPResponseMock;
import org.commcare.core.network.bitcache.BitCacheFactory;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpRequesterMock extends ModernHttpRequester {

    public final static String ioErrorMessage = "uhh ohh, io error oh";

    private static final List<Integer> responseCodeStack = new ArrayList<>();
    private static final List<String> expectedUrlStack = new ArrayList<>();
    private static final List<String> requestPayloadStack = new ArrayList<>();

    private static boolean isAuthenticated = true;

    public ModernHttpRequesterMock(BitCacheFactory.CacheDirSetup cacheDirSetup, URL url, HashMap<String, String> params, HashMap<String, String> headers, @Nullable RequestBody requestBody, @Nullable List<MultipartBody.Part> parts, CommCareNetworkService commCareNetworkService, HTTPMethod method) {
        super(cacheDirSetup, url, params, headers, requestBody, parts, commCareNetworkService, method);
    }

    /**
     * Set the response code for the next N requests
     */
    public static void setResponseCodes(Integer[] responseCodes) {
        responseCodeStack.clear();
        Collections.addAll(responseCodeStack, responseCodes);
    }

    public static void setAuthenticated(boolean authenticated) {
        isAuthenticated = authenticated;
    }

    /**
     * Set expected url for the next N requests
     */
    public static void setExpectedUrls(String[] urls) {
        expectedUrlStack.clear();
        Collections.addAll(expectedUrlStack, urls);
    }

    public static void setRequestPayloads(String[] payloadReferences) {
        requestPayloadStack.clear();
        Collections.addAll(requestPayloadStack, payloadReferences);
    }

    @Override
    protected Response<ResponseBody> makeRequest() throws IOException {
        if(isAuthenticated && !url.getProtocol().contentEquals("https")){
            throw new AuthenticationInterceptor.PlainTextPasswordException();
        }

        if (!expectedUrlStack.isEmpty()) {
            assertUrlsEqual(expectedUrlStack.remove(0), buildUrlWithParams().toString());
        }

        if (requestPayloadStack.isEmpty()) {
            return OkHTTPResponseMock.createResponse(responseCodeStack.remove(0));
        } else {
            String payloadReference = requestPayloadStack.remove(0);
            if (payloadReference == null) {
                throw new IOException(ioErrorMessage);
            } else {
                InputStream payloadStream;
                try {
                    payloadStream =
                            ReferenceManager.instance().DeriveReference(payloadReference).getStream();
                } catch (InvalidReferenceException ire) {
                    throw new IOException("No payload available at " + payloadReference);
                }
                return OkHTTPResponseMock.createResponse(responseCodeStack.remove(0),payloadStream);
            }
        }
    }

    private URL buildUrlWithParams() throws MalformedURLException {
        Uri.Builder b = Uri.parse(url.toString()).buildUpon();
        for (Map.Entry<String, String> param : params.entrySet()) {
            b.appendQueryParameter(param.getKey(), param.getValue());
        }
        return new URL(b.build().toString());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void assertUrlsEqual(String expected, String request) {
        Uri requestUrl = Uri.parse(request);
        Uri expectedUrl = Uri.parse(expected);
        assertEquals(expectedUrl.getPath(), requestUrl.getPath());
        for (String queryParam : expectedUrl.getQueryParameterNames()) {
            assertEquals(requestUrl.getQueryParameter(queryParam), expectedUrl.getQueryParameter(queryParam));
        }
    }
}
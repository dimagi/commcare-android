package org.commcare.interfaces;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Types of http requests made by CommCare mobile to server
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface CommcareRequestEndpoints {
    Response<ResponseBody> makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws IOException;

    Response<ResponseBody> makeKeyFetchRequest(String baseUri, @Nullable Date lastRequest) throws IOException;

    Response<ResponseBody> postMultipart(String url, List<MultipartBody.Part> parts) throws IOException;

    Response<ResponseBody> simpleGet(String uri) throws IOException;

    /**
     * Synchronous GET request
     *
     * @param httpParams non URL-Encoded parameters to include in the HTTP request with the URL
     */
    Response<ResponseBody> simpleGet(String uri, Map<String, String> httpParams) throws IOException;

    void abortCurrentRequest();
}

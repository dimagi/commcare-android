package org.commcare.interfaces;

import com.google.common.collect.Multimap;

import java.io.IOException;
import java.util.Date;
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
    Response<ResponseBody> makeCaseFetchRequest(String baseUri, boolean includeStateFlags, boolean skipFixtures) throws IOException;

    Response<ResponseBody> makeKeyFetchRequest(String baseUri, @Nullable Date lastRequest) throws IOException;

    Response<ResponseBody> postMultipart(String url, List<MultipartBody.Part> parts, Multimap<String, String> queryParams) throws IOException;

    Response<ResponseBody> simpleGet(String uri) throws IOException;

    /**
     * Synchronous GET request
     *
     * @param httpParams non URL-Encoded parameters to include in the HTTP request with the URL
     * @param httpHeaders http headers to use in the HTTP request
     */
    Response<ResponseBody> simpleGet(String uri, Multimap<String, String> httpParams, Map<String, String> httpHeaders) throws IOException;

    void abortCurrentRequest();

    Response<ResponseBody> postLogs(String submissionUrl, List<MultipartBody.Part> parts, boolean forceLogs) throws IOException;

}

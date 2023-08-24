package org.commcare.tasks;

import android.content.Context;

import com.google.common.collect.Multimap;

import org.commcare.CommCareApplication;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.interfaces.ResponseStreamAccessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.tasks.templates.CommCareTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.annotation.Nullable;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Makes get/post request and delegates http response to receiver on completion
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpTask
        extends CommCareTask<Void, Void, Void, HttpResponseProcessor>
        implements ResponseStreamAccessor {

    public static final int SIMPLE_HTTP_TASK_ID = 11;

    private final Context context;
    private final String url;
    private final Multimap<String, String> params;
    private final HashMap<String, String> headers;
    private final RequestBody requestBody;
    private final HTTPMethod method;
    private final AuthInfo authInfo;
    private InputStream responseDataStream;
    private IOException mException;
    private Response<ResponseBody> mResponse;

    // Use for GET request
    public ModernHttpTask(Context context, String url, Multimap<String, String> params,
                          HashMap<String, String> headers,
                          AuthInfo authInfo) {
        this(context, url, params, headers, null, HTTPMethod.GET, authInfo);
    }

    public ModernHttpTask(Context context, String url, Multimap<String, String> params,
            HashMap<String, String> headers,
            @Nullable RequestBody requestBody,
            HTTPMethod method,
            AuthInfo authInfo) {
        taskId = SIMPLE_HTTP_TASK_ID;

        this.context = context;
        this.url = url;
        this.params = params;
        this.headers = headers;
        this.requestBody = requestBody;
        this.method = method;
        this.authInfo = authInfo;
    }

    @Override
    protected Void doTaskBackground(Void... params) {
        try {
            ModernHttpRequester requester = CommCareApplication.instance().buildHttpRequester(
                    context,
                    url,
                    this.params,
                    headers,
                    requestBody,
                    null,
                    method,
                    authInfo,
                    null,
                    method.equals(HTTPMethod.GET));

            mResponse = requester.makeRequest();
            if (mResponse.isSuccessful()) {
                responseDataStream = requester.getResponseStream(mResponse);
            }
        } catch (IOException e) {
            mException = e;
        }
        return null;
    }

    @Override
    protected void deliverResult(HttpResponseProcessor httpResponseProcessor,
                                 Void result) {

        if (mException != null) {
            httpResponseProcessor.handleIOException(mException);
        } else {
            // route to appropriate callback based on http response code
            ModernHttpRequester.processResponse(
                    httpResponseProcessor,
                    mResponse.code(),
                    this);
        }
    }

    @Override
    protected void deliverUpdate(HttpResponseProcessor httpResponseProcessor,
                                 Void... update) {
    }

    @Override
    protected void deliverError(HttpResponseProcessor httpResponseProcessor,
                                Exception e) {
    }

    @Override
    public InputStream getResponseStream() {
        return responseDataStream;
    }
}

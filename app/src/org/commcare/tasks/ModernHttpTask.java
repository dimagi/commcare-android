package org.commcare.tasks;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.interfaces.ResponseStreamAccessor;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.modern.util.Pair;
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

    private final ModernHttpRequester requester;
    private InputStream responseDataStream;
    private IOException mException;
    private Response<ResponseBody> mResponse;

    // Use for GET request
    public ModernHttpTask(Context context, String url, HashMap<String, String> params,
                          HashMap<String, String> headers,
                          @Nullable Pair<String, String> usernameAndPasswordToAuthWith) {
        this(context, url, params, headers, null, HTTPMethod.GET, usernameAndPasswordToAuthWith);
    }

    public ModernHttpTask(Context context, String url, HashMap<String, String> params,
                          HashMap<String, String> headers,
                          @Nullable RequestBody requestBody,
                          HTTPMethod method,
                          @Nullable Pair<String, String> usernameAndPasswordToAuthWith) {
        taskId = SIMPLE_HTTP_TASK_ID;
        requester = CommCareApplication.instance().buildHttpRequester(
                context,
                url,
                params,
                headers,
                requestBody,
                null,
                method,
                usernameAndPasswordToAuthWith,
                null);
    }

    @Override
    protected Void doTaskBackground(Void... params) {
        try {
            mResponse = requester.makeRequest();
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
    public InputStream getResponseStream() throws IOException{
        return requester.getResponseStream(mResponse);
    }
}

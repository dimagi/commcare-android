package org.commcare.tasks;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.interfaces.ResponseStreamAccessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.modern.util.Pair;
import org.commcare.tasks.templates.CommCareTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.annotation.Nullable;

import okhttp3.RequestBody;

/**
 * Makes get/post request and delegates http response to receiver on completion
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpTask
        extends CommCareTask<Void, Void, Void, HttpResponseProcessor>
        implements HttpResponseProcessor, ResponseStreamAccessor {

    public static final int SIMPLE_HTTP_TASK_ID = 11;

    private final ModernHttpRequester requester;
    private int responseCode;
    private InputStream responseDataStream;
    private IOException exception;

    // Use for GET request
    public ModernHttpTask(Context context, String url, HashMap<String, String> params,
                          HashMap<String, String> headers,
                          AuthInfo authInfo) {
        this(context, url, params, headers, null, HTTPMethod.GET, authInfo);
    }

    public ModernHttpTask(Context context, String url, HashMap<String, String> params,
                          HashMap<String, String> headers,
                          @Nullable RequestBody requestBody,
                          HTTPMethod method,
                          AuthInfo authInfo) {
        taskId = SIMPLE_HTTP_TASK_ID;
        requester = CommCareApplication.instance().buildHttpRequester(
                context,
                url,
                params,
                headers,
                requestBody,
                null,
                method,
                authInfo,
                this);
    }

    @Override
    protected Void doTaskBackground(Void... params) {
        requester.makeRequestAndProcess();
        return null;
    }

    @Override
    protected void deliverResult(HttpResponseProcessor httpResponseProcessor,
                                 Void result) {
        if (exception != null) {
            httpResponseProcessor.handleIOException(exception);
        } else {
            // route to appropriate callback based on http response code
            ModernHttpRequester.processResponse(
                    httpResponseProcessor,
                    responseCode,
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
    public void processSuccess(int responseCode, InputStream responseData) {
        this.responseCode = responseCode;
        responseDataStream = responseData;
    }

    @Override
    public void processClientError(int responseCode) {
        this.responseCode = responseCode;
    }

    @Override
    public void processServerError(int responseCode) {
        this.responseCode = responseCode;
    }

    @Override
    public void processOther(int responseCode) {
        this.responseCode = responseCode;
    }

    @Override
    public void handleIOException(IOException exception) {
        this.exception = exception;
    }

    @Override
    public InputStream getResponseStream() throws IOException {
        return responseDataStream;
    }
}

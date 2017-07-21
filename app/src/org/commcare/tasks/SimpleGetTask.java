package org.commcare.tasks;

import org.apache.http.HttpResponse;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.interfaces.ResponseStreamAccessor;
import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.tasks.templates.CommCareTask;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * A bare-bones task for making a GET request using the old HTTP libs
 *
 * Created by amstone326 on 6/4/17.
 */
public class SimpleGetTask extends CommCareTask<String, Void, Void, HttpResponseProcessor>
        implements ResponseStreamAccessor {

    private HttpRequestGenerator requestGenerator;
    private HttpResponseProcessor responseProcessor;
    private Response<ResponseBody> lastResponse;

    public SimpleGetTask(String username, String password,
                         HttpResponseProcessor responseProcessor) {
        this.requestGenerator = new HttpRequestGenerator(username, password);
        this.responseProcessor = responseProcessor;
    }

    @Override
    protected Void doTaskBackground(String... params) {
        try {
            this.lastResponse = requestGenerator.simpleGet(params[0]);
            ModernHttpRequester.processResponse(responseProcessor,
                    lastResponse.code(), this);
        } catch (IOException | AuthenticationInterceptor.PlainTextPasswordException e) {
            responseProcessor.handleException(e);
        }
        return null;
    }

    @Override
    protected void deliverResult(HttpResponseProcessor httpResponseProcessor, Void aVoid) {

    }

    @Override
    protected void deliverUpdate(HttpResponseProcessor httpResponseProcessor, Void... update) {

    }

    @Override
    protected void deliverError(HttpResponseProcessor httpResponseProcessor, Exception e) {

    }

    @Override
    public InputStream getResponseStream() throws IOException {
        return lastResponse.body().byteStream();
    }
}

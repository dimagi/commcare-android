package org.commcare.tasks;

import org.apache.http.HttpResponse;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.interfaces.ResponseStreamAccessor;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.tasks.templates.CommCareTask;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by amstone326 on 6/4/17.
 */

public class SimpleGetTask extends CommCareTask<String, Void, Void, HttpResponseProcessor>
        implements ResponseStreamAccessor {

    private HttpRequestGenerator requestGenerator;
    private HttpResponseProcessor responseProcessor;
    private HttpResponse lastResponse;

    public SimpleGetTask(String username, String password,
                         HttpResponseProcessor responseProcessor) {
        this.requestGenerator = new HttpRequestGenerator(username, password);
        this.responseProcessor = responseProcessor;
    }

    @Override
    protected Void doTaskBackground(String... params) {
        try {
            this.lastResponse = requestGenerator.get(params[0]);
            ModernHttpRequester.processResponse(this.responseProcessor,
                    lastResponse.getStatusLine().getStatusCode(), this);
        } catch (IOException e) {
            responseProcessor.handleIOException(e);
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
        return lastResponse.getEntity().getContent();
    }
}

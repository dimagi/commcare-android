package org.commcare.activities.connect;

import android.content.Context;
import android.widget.Toast;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;

import org.commcare.CommCareApplication;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ConnectIDNetworkHelper {
    public interface INetworkResultHandler {
        void processSuccess(int responseCode, InputStream responseData);
        void processFailure(int responseCode, IOException e);
    }

    public static class PostResult {
        public int responseCode;
        public InputStream responseStream;
        public IOException e;

        public PostResult(int responseCode, InputStream responseStream, IOException e) {
            this.responseCode = responseCode;
            this.responseStream = responseStream;
            this.e = e;
        }
    }

    public static PostResult postSync(Context context, String url, AuthInfo authInfo, HashMap<String, String> params, boolean useFormEncoding) {
        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody;

        if(useFormEncoding) {
            Multimap<String, String> multimap = ArrayListMultimap.create();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                multimap.put(entry.getKey(), entry.getValue());
            }

            requestBody = ModernHttpRequester.getPostBody(multimap);
            headers = getContentHeadersForXFormPost(requestBody);
        }
        else {
            Gson gson = new Gson();
            String json = gson.toJson(params);
            requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        }

        ModernHttpRequester requester = CommCareApplication.instance().buildHttpRequester(
                context,
                url,
                ImmutableMultimap.of(),
                headers,
                requestBody,
                null,
                HTTPMethod.POST,
                authInfo,
                null,
                false);

        int responseCode = -1;
        InputStream stream = null;
        IOException exception = null;
        try {
            Response<ResponseBody> response = requester.makeRequest();
            responseCode = response.code();
            if (response.isSuccessful()) {
                stream = requester.getResponseStream(response);
            }
        } catch (IOException e) {
            exception = e;
        }

        return new PostResult(responseCode, stream, exception);
    }

    public static void post(Context context, String url, AuthInfo authInfo, HashMap<String, String> params, boolean useFormEncoding, INetworkResultHandler handler) {
        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody;

        if(useFormEncoding) {
            Multimap<String, String> multimap = ArrayListMultimap.create();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                multimap.put(entry.getKey(), entry.getValue());
            }

            requestBody = ModernHttpRequester.getPostBody(multimap);
            headers = getContentHeadersForXFormPost(requestBody);
        }
        else {
            Gson gson = new Gson();
            String json = gson.toJson(params);
            requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        }

        ModernHttpTask postTask =
                new ModernHttpTask(context, url,
                        ImmutableMultimap.of(),
                        headers,
                        requestBody,
                        HTTPMethod.POST,
                        authInfo);
        postTask.connect(new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                handler.processSuccess(responseCode, responseData);
            }

            @Override
            public void processClientError(int responseCode) {
                //400 error
                handler.processFailure(responseCode, null);
            }

            @Override
            public void processServerError(int responseCode) {
                //500 error for internal server error
                handler.processFailure(responseCode, null);
            }

            @Override
            public void processOther(int responseCode) {
                handler.processFailure(responseCode, null);
            }

            @Override
            public void handleIOException(IOException exception) {
                //UnknownHostException if host not found
                handler.processFailure(-1, exception);
            }

            @Override
            public <A, B, C> void connectTask(CommCareTask<A, B, C, HttpResponseProcessor> task) {}

            @Override
            public void startBlockingForTask(int id) {}

            @Override
            public void stopBlockingForTask(int id) {}

            @Override
            public void taskCancelled() {}

            @Override
            public HttpResponseProcessor getReceiver() { return this; }

            @Override
            public void startTaskTransition() {}

            @Override
            public void stopTaskTransition(int taskId) {}

            @Override
            public void hideTaskCancelButton() {}
        });
        postTask.executeParallel();
    }

    private static HashMap<String, String> getContentHeadersForXFormPost(RequestBody postBody) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        try {
            headers.put("Content-Length", String.valueOf(postBody.contentLength()));
        }
        catch(IOException e) {

        }
        return headers;
    }

    public static void get(Context context, String url, AuthInfo authInfo, Multimap<String, String> params, INetworkResultHandler handler) {
        //TODO: Figure out how to send GET request the right way
        String getUrl = url;
        if(params.size() > 0) {
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entries()) {
                String delim = "&";
                if(first) {
                    delim = "?";
                    first = false;
                }
                getUrl += delim + entry.getKey() + "=" + entry.getValue();
            }
        }

        ModernHttpTask getTask =
                new ModernHttpTask(context, getUrl,
                        ArrayListMultimap.create(),
                        new HashMap<>(),
                        authInfo);
        getTask.connect(new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                handler.processSuccess(responseCode, responseData);
            }

            @Override
            public void processClientError(int responseCode) {
                //400 error
                handler.processFailure(responseCode, null);
            }

            @Override
            public void processServerError(int responseCode) {
                //500 error for internal server error
                handler.processFailure(responseCode, null);
            }

            @Override
            public void processOther(int responseCode) {
                handler.processFailure(responseCode, null);
            }

            @Override
            public void handleIOException(IOException exception) {
                //UnknownHostException if host not found
                handler.processFailure(-1, exception);
            }

            @Override
            public <A, B, C> void connectTask(CommCareTask<A, B, C, HttpResponseProcessor> task) {
            }

            @Override
            public void startBlockingForTask(int id) {
            }

            @Override
            public void stopBlockingForTask(int id) {
            }

            @Override
            public void taskCancelled() {
            }

            @Override
            public HttpResponseProcessor getReceiver() {
                return this;
            }

            @Override
            public void startTaskTransition() {
            }

            @Override
            public void stopTaskTransition(int taskId) {
            }

            @Override
            public void hideTaskCancelButton() {
            }
        });
        getTask.executeParallel();
    }
}

package org.commcare.activities.connect;

import android.content.Context;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Helper class for making network calls related to Connect
 * Calls may go to ConnectID server or HQ server (for SSO)
 *
 * @author dviggiano
 */
public class ConnectIdNetworkHelper {
    /**
     * Interface for callbacks when network request completes
     */
    public interface INetworkResultHandler {
        void processSuccess(int responseCode, InputStream responseData);

        void processFailure(int responseCode, IOException e);

        void processNetworkFailure();
    }

    /**
     * Helper class to hold the results of a network request
     */
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

    private boolean isBusy = false;

    private ConnectIdNetworkHelper() {
        //Private constructor for singleton
    }

    private static class Loader {
        static final ConnectIdNetworkHelper INSTANCE = new ConnectIdNetworkHelper();
    }

    private static ConnectIdNetworkHelper getInstance() {
        return Loader.INSTANCE;
    }

    public static PostResult postSync(Context context, String url, AuthInfo authInfo,
                                      HashMap<String, String> params, boolean useFormEncoding) {
        return getInstance().postSyncInternal(context, url, authInfo, params, useFormEncoding);
    }

    public static boolean post(Context context, String url, AuthInfo authInfo,
                               HashMap<String, String> params, boolean useFormEncoding,
                               INetworkResultHandler handler) {
        return getInstance().postInternal(context, url, authInfo, params, useFormEncoding, handler);
    }

    public static boolean get(Context context, String url, AuthInfo authInfo,
                              Multimap<String, String> params, INetworkResultHandler handler) {
        return getInstance().getInternal(context, url, authInfo, params, handler);
    }

    private PostResult postSyncInternal(Context context, String url, AuthInfo authInfo,
                                        HashMap<String, String> params, boolean useFormEncoding) {
        isBusy = true;
        showProgressDialog(context);
        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody;

        if (useFormEncoding) {
            Multimap<String, String> multimap = ArrayListMultimap.create();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                multimap.put(entry.getKey(), entry.getValue());
            }

            requestBody = ModernHttpRequester.getPostBody(multimap);
            headers = getContentHeadersForXFormPost(requestBody);
        } else {
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

        onFinishProcessing(context);

        return new PostResult(responseCode, stream, exception);
    }

    private boolean postInternal(Context context, String url, AuthInfo authInfo,
                                 HashMap<String, String> params, boolean useFormEncoding,
                                 INetworkResultHandler handler) {
        if (isBusy) {
            return false;
        }
        isBusy = true;

        showProgressDialog(context);

        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody;

        if (useFormEncoding) {
            Multimap<String, String> multimap = ArrayListMultimap.create();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                multimap.put(entry.getKey(), entry.getValue());
            }

            requestBody = ModernHttpRequester.getPostBody(multimap);
            headers = getContentHeadersForXFormPost(requestBody);
        } else {
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
        postTask.connect(getResponseProcessor(context, handler));

        postTask.executeParallel();

        return true;
    }

    private HashMap<String, String> getContentHeadersForXFormPost(RequestBody postBody) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        try {
            headers.put("Content-Length", String.valueOf(postBody.contentLength()));
        } catch (IOException e) {
            //Empty headers if something goes wrong
        }
        return headers;
    }

    private boolean getInternal(Context context, String url, AuthInfo authInfo,
                                Multimap<String, String> params, INetworkResultHandler handler) {
        if (isBusy) {
            return false;
        }
        isBusy = true;

        showProgressDialog(context);

        //TODO: Figure out how to send GET request the right way
        StringBuilder getUrl = new StringBuilder(url);
        if (params.size() > 0) {
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entries()) {
                String delim = "&";
                if (first) {
                    delim = "?";
                    first = false;
                }
                getUrl.append(delim).append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        ModernHttpTask getTask =
                new ModernHttpTask(context, getUrl.toString(),
                        ArrayListMultimap.create(),
                        new HashMap<>(),
                        authInfo);
        getTask.connect(getResponseProcessor(context, handler));
        getTask.executeParallel();

        return true;
    }

    private ConnectorWithHttpResponseProcessor<HttpResponseProcessor> getResponseProcessor(
            Context context, INetworkResultHandler handler) {
        return new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                onFinishProcessing(context);
                handler.processSuccess(responseCode, responseData);
            }

            @Override
            public void processClientError(int responseCode) {
                onFinishProcessing(context);
                //400 error
                handler.processFailure(responseCode, null);
            }

            @Override
            public void processServerError(int responseCode) {
                onFinishProcessing(context);
                //500 error for internal server error
                handler.processFailure(responseCode, null);
            }

            @Override
            public void processOther(int responseCode) {
                onFinishProcessing(context);
                handler.processFailure(responseCode, null);
            }

            @Override
            public void handleIOException(IOException exception) {
                onFinishProcessing(context);
                if (exception instanceof UnknownHostException) {
                    handler.processNetworkFailure();
                } else {
                    handler.processFailure(-1, exception);
                }
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
        };
    }

    private void onFinishProcessing(Context context) {
        isBusy = false;
        dismissProgressDialog(context);
    }

    private static final int NETWORK_ACTIVITY_ID = 7000;

    private void showProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            ((CommCareActivity<?>)context).showProgressDialog(NETWORK_ACTIVITY_ID);
        }
    }

    private void dismissProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            ((CommCareActivity<?>)context).dismissProgressDialogForTask(NETWORK_ACTIVITY_ID);
        }
    }
}

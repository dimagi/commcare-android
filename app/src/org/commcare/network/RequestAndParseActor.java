package org.commcare.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.util.LogTypes;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Created by amstone326 on 5/8/18.
 */

public abstract class RequestAndParseActor {

    private final String requestName;
    private final String logTag;

    public RequestAndParseActor(String requestName, String logTag) {
        this.requestName = requestName;
        this.logTag = logTag;
    }

    public abstract void parseResponse(JSONObject responseAsJson);
    public abstract String getUrl();
    public abstract HashMap<String, String> getRequestParams();
    public abstract AuthInfo getAuth();

    public void makeRequest() {
        Log.i(logTag, String.format("Requesting %s from %s", requestName, getUrl()));
        ModernHttpRequester requester = CommCareApplication.instance().createGetRequester(
                CommCareApplication.instance(),
                getUrl(),
                getRequestParams(),
                new HashMap(),
                getAuth(),
                responseProcessor);
        requester.makeRequestAndProcess();
    }

    protected final HttpResponseProcessor responseProcessor = new HttpResponseProcessor() {

        @Override
        public void processSuccess(int responseCode, InputStream responseData) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                JSONObject jsonResponse = new JSONObject(responseAsString);
                parseResponseOnUiThread(jsonResponse);
            } catch (JSONException e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        String.format("%s response was not properly-formed JSON: " + e.getMessage(), requestName));
            } catch (IOException e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        String.format("IO error while processing %s response: " + e.getMessage(), requestName));
            }
        }

        @Override
        public void processClientError(int responseCode) {
            processErrorResponse(responseCode);
        }

        @Override
        public void processServerError(int responseCode) {
            processErrorResponse(responseCode);
        }

        @Override
        public void processOther(int responseCode) {
            processErrorResponse(responseCode);
        }

        @Override
        public void handleIOException(IOException exception) {
            if (exception instanceof AuthenticationInterceptor.PlainTextPasswordException) {
                Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE,
                        String.format("Encountered PlainTextPasswordException while sending %s request: " +
                                "Sending password over HTTP", requestName));
            } else {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        String.format("Encountered IOException while getting response stream for %s response: %s",
                                requestName, exception.getMessage()));
            }
        }

        private void processErrorResponse(int responseCode) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    String.format("Received error response from %s request: %s", requestName, responseCode));
        }
    };

    private void parseResponseOnUiThread(final JSONObject responseAsJson) {
        new Handler(Looper.getMainLooper()).post(() -> parseResponse(responseAsJson));
    }

}

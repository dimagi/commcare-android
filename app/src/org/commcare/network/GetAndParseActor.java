package org.commcare.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.commcare.CommCareApp;
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

public abstract class GetAndParseActor {

    protected static final String APP_ID = "app_id";
    protected static final String DEVICE_ID = "device_id";
    protected static final String APP_VERSION = "app_version";
    protected static final String CC_VERSION = "cc_version";
    protected static final String QUARANTINED_FORMS_PARAM = "num_quarantined_forms";
    protected static final String UNSENT_FORMS_PARAM = "num_unsent_forms";
    protected static final String LAST_SYNC_TIME_PARAM = "last_sync_time";

    private final String requestName;
    private final String logTag;
    private final String url;

    public GetAndParseActor(String requestName, String logTag, String urlPrefKey) {
        this.requestName = requestName;
        this.logTag = logTag;
        this.url = CommCareApplication.instance().getCurrentApp().getAppPreferences().getString(urlPrefKey, null);
    }

    public void makeRequest() {
        if (url == null) {
            return;
        }
        Log.i(logTag, String.format("Requesting %s from %s", requestName, url));
        ModernHttpRequester requester = CommCareApplication.instance().createGetRequester(
                CommCareApplication.instance(),
                url,
                getRequestParams(),
                new HashMap(),
                getAuth(),
                responseProcessor);
        requester.makeRequestAndProcess();
    }

    public abstract HashMap<String, String> getRequestParams();

    public abstract AuthInfo getAuth();

    public abstract void parseResponse(JSONObject responseAsJson);

    protected final HttpResponseProcessor responseProcessor = new HttpResponseProcessor() {

        @Override
        public void processSuccess(int responseCode, InputStream responseStream) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseStream));
                JSONObject jsonResponse = new JSONObject(responseAsString);
                parseResponse(jsonResponse);
            } catch (JSONException e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        String.format("%s response was not properly-formed JSON: %s", requestName, e.getMessage()));
            } catch (IOException e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        String.format("IO error while processing %s response: %s", requestName, e.getMessage()));
            } finally {
                StreamsUtil.closeStream(responseStream);
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

    protected boolean checkForAppIdMatch(JSONObject responseAsJson) {
        try {
            if (responseAsJson.has("app_id")) {
                CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
                if (currentApp != null) {
                    String appIdOfResponse = responseAsJson.getString("app_id");
                    String currentAppId = currentApp.getAppRecord().getUniqueId();
                    return appIdOfResponse.equals(currentAppId);
                }
            }
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    String.format("%s response did not have required app_id param", this.requestName));
        } catch (JSONException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    String.format("App id in %s response was not properly formatted: %s", this.requestName, e.getMessage()));
        }
        return false;
    }


}

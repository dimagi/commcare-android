package org.commcare.heartbeat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.logging.AndroidLogger;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;

/**
 * Responsible for making a heartbeat request to the server when signaled to do so by the current
 * session's HeartbeatLifecycleManager, and then parsing and handling the response. Currently,
 * the primary content of the server's response to the heartbeat request will be information
 * about potential binary or app updates that the app should prompt users to conduct.
 * <p>
 * Created by amstone326 on 5/5/17.
 */
public class HeartbeatRequester {

    private static final String TAG = HeartbeatRequester.class.getSimpleName();

    private static final String APP_ID = "app_id";
    private static final String QUARANTINED_FORMS_PARAM = "num_quarantined_forms";
    private static final String UNSENT_FORMS_PARAM = "num_unsent_forms";
    private static final String LAST_SYNC_TIME_PARAM = "last_sync_time";

    private final HttpResponseProcessor responseProcessor = new HttpResponseProcessor() {

        @Override
        public void processSuccess(int responseCode, InputStream responseData) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                JSONObject jsonResponse = new JSONObject(responseAsString);
                passResponseToUiThread(jsonResponse);
            } catch (JSONException e) {
                Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                        "Heartbeat response was not properly-formed JSON: " + e.getMessage());
            } catch (IOException e) {
                Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                        "IO error while processing heartbeat response: " + e.getMessage());
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
        public void handleException(Exception exception) {
            if (exception instanceof IOException) {
                Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                        "Encountered IOException while getting response stream for heartbeat response: "
                                + exception.getMessage());
            } else if (exception instanceof AuthenticationInterceptor.PlainTextPasswordException) {
                Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "PlainTextPasswordException: Sending password over HTTP");
            }
        }

        private void processErrorResponse(int responseCode) {
            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                    "Received error response from heartbeat request: " + responseCode);
        }
    };

    protected void requestHeartbeat() {
        String urlString = CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getString(CommCareServerPreferences.PREFS_HEARTBEAT_URL_KEY, null);
        Log.i(TAG, "Requesting heartbeat from " + urlString);
        ModernHttpRequester requester = CommCareApplication.instance().createGetRequestor(
                CommCareApplication.instance(),
                urlString,
                getParamsForHeartbeatRequest(),
                new HashMap(),
                null,
                responseProcessor);
        requester.processRequest();
    }

    private static HashMap<String, String> getParamsForHeartbeatRequest() {
        HashMap<String, String> params = new HashMap<>();
        params.put(APP_ID, CommCareApplication.instance().getCurrentApp().getUniqueId());
        params.put(QUARANTINED_FORMS_PARAM, "" + StorageUtils.getNumQuarantinedForms());
        params.put(UNSENT_FORMS_PARAM, "" + StorageUtils.getNumUnsentForms());
        params.put(LAST_SYNC_TIME_PARAM, new Date(SyncDetailCalculations.getLastSyncTime()).toString());
        return params;
    }

    protected static void passResponseToUiThread(final JSONObject responseAsJson) {
        // will run on UI thread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                parseHeartbeatResponse(responseAsJson);
            }
        });
    }

    protected static void parseHeartbeatResponse(JSONObject responseAsJson) {
        if (checkForAppIdMatch(responseAsJson)) {
            // We only want to register this response if the current app is still the
            // same as the one that sent the request originally
            try {
                CommCareApplication.instance().getSession().setHeartbeatSuccess();
            } catch (SessionUnavailableException e) {
                // Do nothing -- the session expired, so we just don't register the response
                return;
            }
            Log.i(TAG, "Parsing heartbeat response");
            attemptApkUpdateParse(responseAsJson);
            attemptCczUpdateParse(responseAsJson);
        }
    }

    private static boolean checkForAppIdMatch(JSONObject responseAsJson) {
        try {
            if (responseAsJson.has("app_id")) {
                CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
                if (currentApp != null) {
                    String appIdOfResponse = responseAsJson.getString("app_id");
                    String currentAppId = currentApp.getAppRecord().getUniqueId();
                    return appIdOfResponse.equals(currentAppId);
                }
            }
            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                    "Heartbeat response did not have required app_id param");
        } catch (JSONException e) {
            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                    "App id in heartbeat response was not formatted properly: " + e.getMessage());
        }
        return false;
    }

    private static void attemptApkUpdateParse(JSONObject responseAsJson) {
        try {
            if (responseAsJson.has("latest_apk_version")) {
                JSONObject latestApkVersionInfo =
                        responseAsJson.getJSONObject("latest_apk_version");
                parseUpdateToPrompt(latestApkVersionInfo, true);
            }
        } catch (JSONException e) {
            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                    "Latest apk version object in heartbeat response was not " +
                            "formatted properly: " + e.getMessage());
        }
    }

    private static void attemptCczUpdateParse(JSONObject responseAsJson) {
        try {
            if (responseAsJson.has("latest_ccz_version")) {
                JSONObject latestCczVersionInfo = responseAsJson.getJSONObject("latest_ccz_version");
                parseUpdateToPrompt(latestCczVersionInfo, false);
            }
        } catch (JSONException e) {
            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                    "Latest ccz version object in heartbeat response was not " +
                            "formatted properly: " + e.getMessage());
        }
    }

    private static void parseUpdateToPrompt(JSONObject latestVersionInfo, boolean isForApk) {
        try {
            if (latestVersionInfo.has("value")) {
                String versionValue = latestVersionInfo.getString("value");
                String forceByDate = null;
                if (latestVersionInfo.has("force_by_date")) {
                    forceByDate = latestVersionInfo.getString("force_by_date");
                }
                UpdateToPrompt updateToPrompt = new UpdateToPrompt(versionValue, forceByDate, isForApk);
                updateToPrompt.registerWithSystem();
            }
        } catch (JSONException e) {
            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                    "Encountered malformed json while trying to parse server response into an " +
                            "UpdateToPrompt object : " + e.getMessage());
        }
    }
}

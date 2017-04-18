package org.commcare;

import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.logging.AndroidLogger;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by amstone326 on 4/13/17.
 */

public class CommCareHeartbeatManager {

    private static final long ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;
    private static final long TEN_SECONDS = 10*1000;

    private static final String TEST_RESPONSE =
            "{\"latest_apk_version\":{\"value\":\"2.36.1\"},\"latest_ccz_version\":{\"value\":\"85\", \"force_by_date\":\"2017-05-01\"}}";

    private static final String QUARANTINED_FORMS_PARAM = "num_quarantined_forms";

    private Timer heartbeatTimer;

    private static CommCareHeartbeatManager INSTANCE;

    private final HttpResponseProcessor responseProcessor = new HttpResponseProcessor() {

        @Override
        public void processSuccess(int responseCode, InputStream responseData) {
            try {
                String responseAsString = StreamsUtil.inputStreamToByteArray(responseData).toString();
                JSONObject jsonResponse = new JSONObject(responseAsString);
                parseHeartbeatResponse(jsonResponse);
            }
            catch (JSONException e) {
                System.out.println("Heartbeat response was not properly-formed JSON");
            }
            catch (IOException e) {
                System.out.println("IO error while processing heartbeat response");
            }
        }

        @Override
        public void processRedirection(int responseCode) {
            processErrorResponse(responseCode);
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
            System.out.println("Encountered IOExeption while getting response stream");
        }

        private void processErrorResponse(int responseCode) {
            System.out.println("Received error response from heartbeat request: " + responseCode);
        }
    };

    public static CommCareHeartbeatManager instance() {
        if (INSTANCE == null) {
            INSTANCE = new CommCareHeartbeatManager();
        }
        return INSTANCE;
    }

    public void startHeartbeatCommunications() {
        TimerTask heartbeatTimerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    //requestHeartbeat(CommCareApplication.instance().getSession().getLoggedInUser());
                    parseTestHeartbeatResponse();
                } catch (SessionUnavailableException e) {
                    Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW,
                            "Tried to send heartbeat request when there was no active session");
                }
            }
        };

        heartbeatTimer = new Timer();
        heartbeatTimer.schedule(heartbeatTimerTask, new Date(), TEN_SECONDS);
    }

    public void stopHeartbeatCommunications() {
        heartbeatTimer.cancel();
    }

    public static void parseTestHeartbeatResponse() {
        System.out.println("NOTE: Testing heartbeat response processing");
        try {
            parseHeartbeatResponse(new JSONObject(TEST_RESPONSE));
        } catch (JSONException e) {
            System.out.println("Test response was not properly formed JSON");
        }
    }

    private void requestHeartbeat(User currentUser) {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        String urlString = currentApp.getAppPreferences().getString(
                CommCareServerPreferences.PREFS_HEARTBEAT_URL_KEY, null);
        if (urlString == null) {
            // This app was generated before the heartbeat URL started being included, so we
            // can't make the request
            return;
        }

        try {
            ModernHttpRequester requester =
                    CommCareApplication.instance().buildHttpRequesterForLoggedInUser(
                            CommCareApplication.instance(), new URL(urlString),
                            getParamsForHeartbeatRequest(), true, false);
            requester.setResponseProcessor(responseProcessor);
            requester.request();
        } catch (MalformedURLException e) {
            System.out.println("Heartbeat URL was malformed");
        }
    }

    private static HashMap<String, String> getParamsForHeartbeatRequest() {
        HashMap<String, String> params = new HashMap<>();
        // TODO: get the actual value for this
        params.put(QUARANTINED_FORMS_PARAM, "0");
        return params;
    }

    private static void parseHeartbeatResponse(JSONObject responseAsJson) {
        try {
            if (responseAsJson.has("latest_apk_version")) {
                JSONObject latestApkVersionInfo = responseAsJson.getJSONObject("latest_apk_version");
                parseUpdateToPrompt(latestApkVersionInfo, true);
            }
        } catch (JSONException e) {
            System.out.println("Latest apk version object not formatted properly");
        }

        try {
            if (responseAsJson.has("latest_ccz_version")) {
                JSONObject latestCczVersionInfo = responseAsJson.getJSONObject("latest_ccz_version");
                parseUpdateToPrompt(latestCczVersionInfo, false);
            }
        } catch (JSONException e) {
            System.out.println("Latest ccz version object not formatted properly");
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
            System.out.println("Encountered malformed json while parsing an UpdateToPrompt");
        }
    }

}

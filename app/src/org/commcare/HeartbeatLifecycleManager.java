package org.commcare;

import android.os.Handler;
import android.os.Looper;

import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.logging.AndroidLogger;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * While active, this class is responsible for using a TimerTask to periodically ping the server
 * with a "heartbeat" request, and then handle the response. The lifecycle of the TimerTask is
 * tied to that of the CommCareSessionService; it should be started whenever a session service is
 * started, and ended whenever a session service is ended for any reason.
 *
 * Currently, the primary content of the server's response to the heartbeat request will be
 * information about potential binary or app updates that the app should prompt users to conduct.
 *
 * Created by amstone326 on 4/13/17.
 */
public class HeartbeatLifecycleManager {

    private static final long ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;

    private static final String TEST_RESPONSE =
            "{\"latest_apk_version\":{\"value\":\"2.36.1\"},\"latest_ccz_version\":{\"value\":\"197\", \"force_by_date\":\"2017-04-24\"}}";

    private static final String QUARANTINED_FORMS_PARAM = "num_quarantined_forms";
    private static final String UNSENT_FORMS_PARAM = "num_unsent_forms";
    private static final String LAST_SYNC_TIME_PARAM = "last_sync_time";

    private Timer heartbeatTimer;

    private final HttpResponseProcessor responseProcessor = new HttpResponseProcessor() {

        @Override
        public void processSuccess(int responseCode, InputStream responseData) {
            try {
                String responseAsString = StreamsUtil.inputStreamToByteArray(responseData).toString();
                JSONObject jsonResponse = new JSONObject(responseAsString);
                parseHeartbeatResponse(jsonResponse);
            }
            catch (JSONException e) {
                Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                        "Heartbeat response was not properly-formed JSON: " + e.getMessage());
            }
            catch (IOException e) {
                Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                        "IO error while processing heartbeat response: " + e.getMessage());
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
            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                    "Encountered IOException while getting response stream for heartbeat response: "
                            + exception.getMessage());
        }

        private void processErrorResponse(int responseCode) {
            Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                    "Received error response from heartbeat request: " + responseCode);
        }
    };

    private static HeartbeatLifecycleManager INSTANCE;
    public static HeartbeatLifecycleManager instance() {
        if (INSTANCE == null) {
            INSTANCE = new HeartbeatLifecycleManager();
        }
        return INSTANCE;
    }

    public void startHeartbeatCommunications() {
        if (heartbeatTimer != null) {
            // Make sure we end anything still in progress
            heartbeatTimer.cancel();
        }
        heartbeatTimer = new Timer();
        heartbeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    User currentUser = CommCareApplication.instance().getSession().getLoggedInUser();
                    //simulateRequestGettingStuck();
                    //requestHeartbeat(currentUser);
                    parseTestHeartbeatResponse();
                } catch (SessionUnavailableException e) {
                    // Means the session has ended, so we should stop these requests
                    stopHeartbeatCommunications();
                } catch (Exception e) {
                    // Encountered a different, unexpected issue
                    stopHeartbeatCommunications();
                    Logger.log(AndroidLogger.TYPE_ERROR_SERVER_COMMS,
                            "Encountered unexpected exception during heartbeat communications: "
                                    + e.getMessage() + ". Stopping the heartbeat thread.");
                }
            }
        }, new Date(), ONE_DAY_IN_MS);
    }

    public void stopHeartbeatCommunications() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
        }
    }

    public static void parseTestHeartbeatResponse() {
        System.out.println("NOTE: Testing heartbeat response processing");
        try {
            parseHeartbeatResponse(new JSONObject(TEST_RESPONSE));
        } catch (JSONException e) {
            System.out.println("Test response was not properly formed JSON");
        }
    }

    private static void simulateRequestGettingStuck() {
        System.out.println("Before sleeping");
        try {
            Thread.sleep(5*1000);
        } catch (InterruptedException e) {
            System.out.println("TEST ERROR: sleep was interrupted");
        }
        System.out.println("After sleeping");
    }

    private void requestHeartbeat(User currentUser) {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        String urlString = currentApp.getAppPreferences().getString(
                CommCareServerPreferences.PREFS_HEARTBEAT_URL_KEY, null);
        if (urlString == null) {
            // This app was generated before the heartbeat URL started being included, so we
            // can't make the request
            stopHeartbeatCommunications();
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
            Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE,
                    "Heartbeat URL was malformed: " + e.getMessage());
        }
    }

    private static HashMap<String, String> getParamsForHeartbeatRequest() {
        HashMap<String, String> params = new HashMap<>();
        params.put(QUARANTINED_FORMS_PARAM, "" + StorageUtils.getNumQuarantinedForms());
        params.put(UNSENT_FORMS_PARAM, "" + StorageUtils.getNumUnsentForms());
        params.put(LAST_SYNC_TIME_PARAM, new Date(SyncDetailCalculations.getLastSyncTime()).toString());
        return params;
    }

    private static void parseHeartbeatResponse(final JSONObject responseAsJson) {
        try {
            // Make sure we still have an active session before parsing the response
            CommCareApplication.instance().getSession();

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    // will run on UI thread
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
            });
        } catch (SessionUnavailableException e) {
            // Don't do anything, since we don't want to parse the response if the session service
            // has ended.
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

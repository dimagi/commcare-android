package org.commcare.heartbeat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.preferences.ServerUrls;
import org.commcare.recovery.measures.RecoveryMeasure;
import org.commcare.recovery.measures.RecoveryMeasuresManager;
import org.commcare.util.LogTypes;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.TimeZone;

/**
 * Responsible for making a heartbeat request to the server when signaled to do so by the current
 * session's HeartbeatLifecycleManager, and then parsing and handling the response. Currently,
 * the primary content of the server's response to the heartbeat request will be information
 * about potential binary or app updates that the app should prompt users to conduct.
 *
 * Created by amstone326 on 5/5/17.
 */
public class HeartbeatRequester {

    private static final String TAG = HeartbeatRequester.class.getSimpleName();

    private static final String TARGET = "target";
    private static final String TARGET_HEARTBEAT = "standard_heartbeat";
    private static final String TARGET_RECOVERY_MEASURES = "recovery_measures";

    private static final String APP_ID = "app_id";
    private static final String DEVICE_ID = "device_id";
    private static final String APP_VERSION = "app_version";
    private static final String CC_VERSION = "cc_version";
    private static final String QUARANTINED_FORMS_PARAM = "num_quarantined_forms";
    private static final String UNSENT_FORMS_PARAM = "num_unsent_forms";
    private static final String LAST_SYNC_TIME_PARAM = "last_sync_time";

    private boolean forRecoveryMeasures;

    public HeartbeatRequester(boolean forRecoveryMeasures) {
        this.forRecoveryMeasures = forRecoveryMeasures;
    }

    private final HttpResponseProcessor responseProcessor = new HttpResponseProcessor() {

        @Override
        public void processSuccess(int responseCode, InputStream responseData) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                JSONObject jsonResponse = new JSONObject(responseAsString);
                parseResponseOnUiThread(jsonResponse, forRecoveryMeasures);
            } catch (JSONException e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        "Heartbeat response was not properly-formed JSON: " + e.getMessage());
            } catch (IOException e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
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
        public void handleIOException(IOException exception) {
            if (exception instanceof AuthenticationInterceptor.PlainTextPasswordException) {
                Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE, "Encountered PlainTextPasswordException while sending heartbeat request: Sending password over HTTP");
            } else {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        "Encountered IOException while getting response stream for heartbeat response: "
                                + exception.getMessage());
            }
        }

        private void processErrorResponse(int responseCode) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Received error response from heartbeat request: " + responseCode);
        }
    };

    public void requestHeartbeat() {
        String urlString = CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getString(ServerUrls.PREFS_HEARTBEAT_URL_KEY, null);
        Log.i(TAG, String.format("Requesting %s from %s",
                forRecoveryMeasures ? "recovery measures" : "standard heartbeat", urlString));
        ModernHttpRequester requester = CommCareApplication.instance().buildNoAuthHttpRequester(
                CommCareApplication.instance(),
                urlString,
                getParamsForHeartbeatRequest(forRecoveryMeasures),
                new HashMap(),
                null,
                null,
                HTTPMethod.GET,
                responseProcessor);
        requester.makeRequestAndProcess();
    }

    private static HashMap<String, String> getParamsForHeartbeatRequest(boolean forRecoveryMeasures) {
        HashMap<String, String> params = new HashMap<>();
        params.put(TARGET, forRecoveryMeasures ? TARGET_RECOVERY_MEASURES : TARGET_HEARTBEAT);
        params.put(APP_ID, CommCareApplication.instance().getCurrentApp().getUniqueId());
        params.put(DEVICE_ID, CommCareApplication.instance().getPhoneId());
        params.put(APP_VERSION, "" + ReportingUtils.getAppBuildNumber());
        params.put(CC_VERSION, ReportingUtils.getCommCareVersionString());
        params.put(QUARANTINED_FORMS_PARAM, "" + StorageUtils.getNumQuarantinedForms());
        params.put(UNSENT_FORMS_PARAM, "" + StorageUtils.getNumUnsentForms());
        params.put(LAST_SYNC_TIME_PARAM, getISO8601FormattedLastSyncTime());
        return params;
    }

    private static String getISO8601FormattedLastSyncTime() {
        long lastSyncTime = SyncDetailCalculations.getLastSyncTime();
        if (lastSyncTime == 0) {
            return "";
        } else {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            return df.format(lastSyncTime);
        }
    }

    private static void parseResponseOnUiThread(final JSONObject responseAsJson, final boolean forRecoveryMeasures) {
        // will run on UI thread
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (forRecoveryMeasures) {
                    parseRecoveryMeasures(responseAsJson);
                } else {
                    parseStandardHeartbeatResponse(responseAsJson);
                }
            }
        });
    }

    private static void parseRecoveryMeasures(JSONObject responseAsJson) {
        if (checkForAppIdMatch(responseAsJson)) {
            try {
                if (responseAsJson.has("recovery_measures")) {
                    boolean recoveryMeasuresStored = false;
                    JSONArray recoveryMeasures = responseAsJson.getJSONArray("recovery_measures");
                    for (int i = 0; i < recoveryMeasures.length(); i++) {
                        JSONObject recoveryMeasure = recoveryMeasures.getJSONObject(i);
                        recoveryMeasuresStored = parseRecoveryMeasure(recoveryMeasure) || recoveryMeasuresStored;
                    }
                    if (recoveryMeasuresStored) {
                        RecoveryMeasuresManager.sendBroadcast();
                    }
                }
            } catch (JSONException e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        "recovery_measures array in heartbeat response not properly formatted: " + e.getMessage());
            }
        }
    }

    private static boolean parseRecoveryMeasure(JSONObject recoveryMeasureObject) {
        try {
            int sequenceNumber = Integer.parseInt(recoveryMeasureObject.getString("sequence_number"));
            String type = recoveryMeasureObject.getString("type");
            String ccVersionMin = recoveryMeasureObject.getString("cc_version_min");
            String ccVersionMax = recoveryMeasureObject.getString("cc_version_max");
            int appVersionMin = Integer.parseInt(recoveryMeasureObject.getString("app_version_min"));
            int appVersionMax = Integer.parseInt(recoveryMeasureObject.getString("app_version_max"));
            RecoveryMeasure measure = new RecoveryMeasure(type, sequenceNumber, ccVersionMin,
                    ccVersionMax, appVersionMin, appVersionMax);
            if (measure.applicableToCurrentInstallation()) {
                measure.registerWithSystem();
                return true;
            }
        } catch (JSONException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Recovery measure object in heartbeat response not properly formatted: " +
                            e.getMessage());
        } catch (NumberFormatException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Sequence number or app version in recovery measure response was " +
                            "not a valid number: " + e.getMessage());
        }
        return false;
    }

    static void parseStandardHeartbeatResponse(JSONObject responseAsJson) {
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
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Heartbeat response did not have required app_id param");
        } catch (JSONException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "App id in heartbeat response was not properly formatted: " + e.getMessage());
        }
        return false;
    }

    private static void attemptApkUpdateParse(JSONObject responseAsJson) {
        try {
            if (responseAsJson.has("latest_apk_version")) {
                JSONObject latestApkVersionInfo =
                        responseAsJson.getJSONObject("latest_apk_version");
                parseUpdateToPrompt(latestApkVersionInfo, UpdateToPrompt.Type.APK_UPDATE);
            }
        } catch (JSONException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Latest apk version object in heartbeat response was not " +
                            "properly formatted: " + e.getMessage());
        }
    }

    private static void attemptCczUpdateParse(JSONObject responseAsJson) {
        try {
            if (responseAsJson.has("latest_ccz_version")) {
                JSONObject latestCczVersionInfo = responseAsJson.getJSONObject("latest_ccz_version");
                parseUpdateToPrompt(latestCczVersionInfo, UpdateToPrompt.Type.CCZ_UPDATE);
            }
        } catch (JSONException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Latest ccz version object in heartbeat response was not " +
                            "formatted properly: " + e.getMessage());
        }
    }

    private static void parseUpdateToPrompt(JSONObject latestVersionInfo, UpdateToPrompt.Type updateType) {
        try {
            if (latestVersionInfo.has("value")) {
                String versionValue = latestVersionInfo.getString("value");
                if (!"".equals(versionValue)) {
                    String forceString = null;
                    if (latestVersionInfo.has("force")) {
                        forceString = latestVersionInfo.getString("force");
                    }
                    UpdateToPrompt updateToPrompt = new UpdateToPrompt(versionValue, forceString, updateType);
                    updateToPrompt.registerWithSystem();
                }
            }
        } catch (JSONException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    "Encountered malformed json while trying to parse server response into an " +
                            "UpdateToPrompt object : " + e.getMessage());
        }
    }
}

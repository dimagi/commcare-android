package org.commcare.heartbeat;

import android.util.Log;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.core.network.AuthInfo;
import org.commcare.network.GetAndParseActor;
import org.commcare.preferences.ServerUrls;
import org.commcare.util.LogTypes;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

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
public class HeartbeatRequester extends GetAndParseActor {

    private static final String NAME = "heartbeat";
    private static final String TAG = HeartbeatRequester.class.getSimpleName();

    private static final String APP_ID = "app_id";
    private static final String DEVICE_ID = "device_id";
    private static final String APP_VERSION = "app_version";
    private static final String CC_VERSION = "cc_version";
    private static final String QUARANTINED_FORMS_PARAM = "num_quarantined_forms";
    private static final String UNSENT_FORMS_PARAM = "num_unsent_forms";
    private static final String LAST_SYNC_TIME_PARAM = "last_sync_time";

    public HeartbeatRequester() {
        super(NAME, TAG);
    }

    @Override
    public String getUrl() {
        return CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .getString(ServerUrls.PREFS_HEARTBEAT_URL_KEY, null);
    }

    @Override
    public HashMap<String, String> getRequestParams() {
        HashMap<String, String> params = new HashMap<>();
        params.put(APP_ID, CommCareApplication.instance().getCurrentApp().getUniqueId());
        params.put(DEVICE_ID, CommCareApplication.instance().getPhoneId());
        params.put(APP_VERSION, "" + ReportingUtils.getAppBuildNumber());
        params.put(CC_VERSION, ReportingUtils.getCommCareVersionString());
        params.put(QUARANTINED_FORMS_PARAM, "" + StorageUtils.getNumQuarantinedForms());
        params.put(UNSENT_FORMS_PARAM, "" + StorageUtils.getNumUnsentForms());
        params.put(LAST_SYNC_TIME_PARAM, getISO8601FormattedLastSyncTime());
        return params;
    }

    @Override
    public AuthInfo getAuth() {
        return new AuthInfo.CurrentAuth();
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

    @Override
    public void parseResponse(JSONObject responseAsJson) {
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

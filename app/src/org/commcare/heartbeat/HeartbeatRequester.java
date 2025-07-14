package org.commcare.heartbeat;


import static org.commcare.AppUtils.getCurrentAppId;
import static org.commcare.utils.FirebaseMessagingUtil.FCM_TOKEN;
import static org.commcare.utils.FirebaseMessagingUtil.FCM_TOKEN_TIME;

import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.activities.DriftHelper;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.core.network.AuthInfo;
import org.commcare.network.GetAndParseActor;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.util.LogTypes;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.FirebaseMessagingUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.work.WorkManager;
import org.commcare.android.integrity.IntegrityReporter;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

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
    private static final String CURRENT_DRIFT = "current_drift";
    private static final String MAX_DRIFT_SINCE_LAST_HEARTBEAT = "max_drift_since_last_heartbeat";

    // Request Params
    private static final String DEVICE_ID = "device_id";
    private static final String APP_VERSION = "app_version";
    private static final String CC_VERSION = "cc_version";
    private static final String QUARANTINED_FORMS_PARAM = "num_quarantined_forms";
    private static final String UNSENT_FORMS_PARAM = "num_unsent_forms";
    private static final String LAST_SYNC_TIME_PARAM = "last_sync_time";
    private static final String REPORT_INTEGRITY_KEY = "report_integrity";

    public HeartbeatRequester() {
        super(NAME, TAG, ServerUrls.PREFS_HEARTBEAT_URL_KEY);
    }

    @Override
    public Multimap<String, String> getRequestParams() {
        Multimap<String, String> params = ArrayListMultimap.create();
        params.put(APP_ID, CommCareApplication.instance().getCurrentApp().getUniqueId());
        params.put(DEVICE_ID, CommCareApplication.instance().getPhoneId());
        params.put(APP_VERSION, String.valueOf(ReportingUtils.getAppBuildNumber()));
        params.put(CC_VERSION, ReportingUtils.getCommCareVersionString());
        params.put(QUARANTINED_FORMS_PARAM, String.valueOf(StorageUtils.getNumQuarantinedForms()));
        params.put(UNSENT_FORMS_PARAM, String.valueOf(StorageUtils.getNumUnsentForms()));
        params.put(LAST_SYNC_TIME_PARAM, DateUtils.convertTimeInMsToISO8601(SyncDetailCalculations.getLastSyncTime()));
        params.put(CURRENT_DRIFT, String.valueOf(DriftHelper.getCurrentDrift()));
        params.put(MAX_DRIFT_SINCE_LAST_HEARTBEAT, String.valueOf(DriftHelper.getMaxDriftSinceLastHeartbeat()));
        //TODO: Encode the FCM registration token
        params.put(FCM_TOKEN, FirebaseMessagingUtil.getFCMToken());
        params.put(FCM_TOKEN_TIME, DateUtils.convertTimeInMsToISO8601(FirebaseMessagingUtil.getFCMTokenTime()));
        return params;
    }

    @Override
    public AuthInfo getAuth() {
        return new AuthInfo.CurrentAuth();
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
            checkForForceLogs(responseAsJson);
            checkForDisableBackgroundWork(responseAsJson);
            checkForIntegrityRequest(responseAsJson);
        }
        DriftHelper.clearMaxDriftSinceLastHeartbeat();
    }

    private void checkForDisableBackgroundWork(JSONObject responseAsJson) {
        boolean disableBackgroundWork = responseAsJson.optBoolean("disable_background_work", false);
        HiddenPreferences.setDisableBackgroundWorkTime(disableBackgroundWork);
        if (disableBackgroundWork) {
            WorkManager.getInstance(CommCareApplication.instance()).cancelAllWorkByTag(getCurrentAppId());
        }
    }

    private void checkForForceLogs(JSONObject responseAsJson) {
        String userId = CommCareApplication.instance().getSession().getLoggedInUser().getUniqueId();
        HiddenPreferences.setForceLogs(userId, responseAsJson.optBoolean("force_logs", false));
        if (HiddenPreferences.shouldForceLogs(userId)) {
            CommCareUtil.triggerLogSubmission(CommCareApplication.instance(), true);
        }
    }

    private void checkForIntegrityRequest(JSONObject responseAsJson) {
        String integrityRequest = responseAsJson.optString(REPORT_INTEGRITY_KEY, "");
        if(!Strings.isNullOrEmpty(integrityRequest)) {
            IntegrityReporter.launch(CommCareApplication.instance(), responseAsJson.optString(REPORT_INTEGRITY_KEY, ""));
        }
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
                    if (updateType == UpdateToPrompt.Type.APK_UPDATE) {
                        HiddenPreferences.setLatestCommcareVersion(versionValue);
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

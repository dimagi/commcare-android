package org.commcare.recovery.measures;

import org.commcare.CommCareApplication;
import org.commcare.core.network.AuthInfo;
import org.commcare.network.GetAndParseActor;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * Created by amstone326 on 5/8/18.
 */

public class RecoveryMeasuresRequester extends GetAndParseActor {

    private static final String NAME = "recovery measures";
    private static final String TAG = RecoveryMeasuresRequester.class.getSimpleName();

    RecoveryMeasuresRequester() {
        super(NAME, TAG, ServerUrls.PREFS_RECOVERY_MEASURES_URL_KEY);
    }

    @Override
    public HashMap<String, String> getRequestParams() {
        HashMap<String, String> params = new HashMap<>();
        params.put(APP_ID, CommCareApplication.instance().getCurrentApp().getUniqueId());
        return params;
    }

    @Override
    public AuthInfo getAuth() {
        return new AuthInfo.NoAuth();
    }

    @Override
    public void parseResponse(JSONObject responseAsJson) {
        if (checkForAppIdMatch(responseAsJson)) {
            try {
                // Do this as the first thing
                updateLatestVersionPrefs(responseAsJson);
                if (responseAsJson.has("recovery_measures")) {
                    JSONArray recoveryMeasures = responseAsJson.getJSONArray("recovery_measures");
                    for (int i = 0; i < recoveryMeasures.length(); i++) {
                        JSONObject recoveryMeasure = recoveryMeasures.getJSONObject(i);
                        parseAndStoreRecoveryMeasure(recoveryMeasure);
                    }
                }
            } catch (JSONException e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        "recovery_measures array not properly formatted: " + e.getMessage());
            }
        }
    }

    private static void parseAndStoreRecoveryMeasure(JSONObject recoveryMeasureObject) {
        try {

            String ccVersionMin = null, ccVersionMax = null;
            int appVersionMin = -1, appVersionMax = -1;
            if (recoveryMeasureObject.has("cc_version_min")) {
                ccVersionMin = recoveryMeasureObject.getString("cc_version_min");
                ccVersionMax = recoveryMeasureObject.getString("cc_version_max");
            }
            if (recoveryMeasureObject.has("app_version_min")) {
                appVersionMin = recoveryMeasureObject.getInt("app_version_min");
                appVersionMax = recoveryMeasureObject.getInt("app_version_max");
            }

            if (ccVersionMin == null && appVersionMin == -1) {
                // neither was included, which is invalid
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                        "Recovery measure object is invalid. " +
                                "No app version range or CommCare version range was included");
                return;
            }

            int sequenceNumber = recoveryMeasureObject.getInt("sequence_number");
            String type = recoveryMeasureObject.getString("type");

            RecoveryMeasure measure = new RecoveryMeasure(type, sequenceNumber, ccVersionMin,
                    ccVersionMax, appVersionMin, appVersionMax);
            if (measure.newToCurrentInstallation() && measure.applicableToCurrentInstallation()) {
                measure.registerWithSystem();
            }
        } catch (JSONException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    String.format("Recovery measure object not properly formatted: %s", e.getMessage()));
        }
    }

    private static void updateLatestVersionPrefs(JSONObject recoveryMeasureObject) throws JSONException {
        HiddenPreferences.setLatestCommcareVersion(recoveryMeasureObject.getString("latest_apk_version"));
        HiddenPreferences.setLatestAppVersion(recoveryMeasureObject.getInt("latest_ccz_version"));
    }

}

package org.commcare.recovery.measures;

import org.commcare.core.network.AuthInfo;
import org.commcare.network.RequestAndParseActor;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * Created by amstone326 on 5/8/18.
 */

public class RecoveryMeasuresRequester extends RequestAndParseActor {

    private static final String NAME = "recovery measures";
    private static final String TAG = RecoveryMeasuresRequester.class.getSimpleName();


    public RecoveryMeasuresRequester() {
        super(NAME, TAG);
    }

    @Override
    public String getUrl() {
        return null;
    }

    @Override
    public HashMap<String, String> getRequestParams() {
        return new HashMap<>();
    }

    @Override
    public AuthInfo getAuth() {
        return new AuthInfo.NoAuth();
    }

    @Override
    public void parseResponse(JSONObject responseAsJson) {
        //if (checkForAppIdMatch(responseAsJson)) {
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
        //}
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

}

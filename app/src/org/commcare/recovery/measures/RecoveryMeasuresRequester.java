package org.commcare.recovery.measures;

import org.commcare.core.network.AuthInfo;
import org.commcare.network.GetAndParseActor;
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

    private static final String MOCK_RESPONSE = "{\"app_id\":\"\",\"recovery_measures\": " +
            "[{\"sequence_number\":\"1\", \"type\":\"clear_data\", \"cc_version_min\":\"2.36.2\", " +
            "\"cc_version_max\":\"2.45.0\", \"app_version_min\":\"200\", \"app_version_max\":\"1000\"}," +
            "{\"sequence_number\":\"2\", \"type\":\"app_reinstall\", \"cc_version_min\":\"2.36.2\", " +
            "\"cc_version_max\":\"2.45.0\", \"app_version_min\":\"200\", \"app_version_max\":\"1000\"} ]}";

    public RecoveryMeasuresRequester() {
        super(NAME, TAG, ServerUrls.PREFS_RECOVERY_MEASURES_URL_KEY);
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
    public void makeRequest() {
        // mock waiting for request response
        try {
            Thread.sleep(5000);
            parseResponse(new JSONObject(MOCK_RESPONSE));
        } catch (InterruptedException e) {
            // nothing to do
        } catch (JSONException e) {
            System.out.println("JSONException while parsing mock response: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void parseResponse(JSONObject responseAsJson) {
        //if (checkForAppIdMatch(responseAsJson)) {
            try {
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
        //}
    }

    private static void parseAndStoreRecoveryMeasure(JSONObject recoveryMeasureObject) {
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
            }
        } catch (JSONException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    String.format("Recovery measure object not properly formatted: %s", e.getMessage()));
        } catch (NumberFormatException e) {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS,
                    String.format("Sequence number or app version in recovery measure response was " +
                            "not a valid number: %s", e.getMessage()));
        }
    }

}

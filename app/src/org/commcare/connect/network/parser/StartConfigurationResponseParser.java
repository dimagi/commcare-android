package org.commcare.connect.network.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class StartConfigurationResponseParser {

    /**
     * Parses the JSON response from the start configuration API call and populates
     * the provided PersonalIdSessionData instance.
     *
     * @param json        The JSON object returned from the API
     * @param sessionData The instance to populate with parsed values
     * @throws JSONException if expected keys are missing or malformed
     */
    public static void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        if (json.has("required_lock")) {
            sessionData.setRequiredLock(json.getString("required_lock"));
        }

        if (json.has("demo_user")) {
            sessionData.setDemoUser(json.getBoolean("demo_user"));
        }

        if (json.has("token")) {
            sessionData.setToken(json.getString("token"));
        }

        if (json.has("failure_code")) {
            String failureCode = json.getString("failure_code");
            Logger.log(LogTypes.TYPE_USER, failureCode);
            sessionData.setSessionFailureCode(json.getString("failure_code"));
        }

        if (json.has("failure_subcode")) {
            sessionData.setSessionFailureSubcode(json.getString("failure_subcode"));
        }
    }
}

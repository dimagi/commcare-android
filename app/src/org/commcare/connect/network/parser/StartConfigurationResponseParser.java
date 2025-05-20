package org.commcare.connect.network.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class StartConfigurationResponseParser {

    /**
     * Parses the JSON response from the start configuration API call and populates
     * the PersonalIdSessionData singleton with relevant data.
     *
     * @param json The JSON object returned from the API
     * @throws JSONException if expected keys are missing or malformed
     */
    public static void parse(JSONObject json) throws JSONException {
        PersonalIdSessionData sessionData = PersonalIdSessionData.getInstance();

        if (json.has("required_lock")) {
            sessionData.requiredLock = json.getString("required_lock");
        }

        if (json.has("demo_user")) {
            sessionData.demoUser = json.getBoolean("demo_user");
        }

        if (json.has("token")) {
            sessionData.token = json.getString("token");
        }

        if (json.has("failure_code")) {
            String failureCode = json.getString("failure_code");
            Logger.log(LogTypes.TYPE_USER, failureCode);
            sessionData.sessionFailureCode = failureCode;
        }

        if (json.has("failure_subcode")) {
            sessionData.sessionFailureSubcode = json.getString("failure_subcode");
        }
    }
}

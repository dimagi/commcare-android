package org.commcare.connect.network.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses a JSON response from the start configuration API call
 * and populates a PersonalIdSessionData instance.
 */
public class StartConfigurationResponseParser {

    private final JSONObject json;

    public StartConfigurationResponseParser(JSONObject json) {
        this.json = json;
    }

    /**
     * Parses and sets values on the given PersonalIdSessionData instance.
     *
     * @param sessionData the instance to populate
     * @throws JSONException if a parsing error occurs
     */
    public void parse(PersonalIdSessionData sessionData) throws JSONException {
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
            sessionData.setSessionFailureCode(failureCode);
        }

        if (json.has("failure_subcode")) {
            sessionData.setSessionFailureSubcode(json.getString("failure_subcode"));
        }
    }
}
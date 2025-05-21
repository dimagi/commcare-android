package org.commcare.connect.network.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.utils.JsonExtensions;
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
        sessionData.setRequiredLock(JsonExtensions.optStringSafe(json, "required_lock", null));
        sessionData.setDemoUser(json.optBoolean("demo_user", false));
        sessionData.setToken(JsonExtensions.optStringSafe(json, "token", null));
        sessionData.setSessionFailureCode(JsonExtensions.optStringSafe(json, "failure_code", null));
        sessionData.setSessionFailureSubcode(JsonExtensions.optStringSafe(json, "failure_subcode", null));
    }
}
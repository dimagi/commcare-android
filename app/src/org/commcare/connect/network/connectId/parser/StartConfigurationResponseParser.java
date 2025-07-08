package org.commcare.connect.network.connectId.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.utils.JsonExtensions;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses a JSON response from the start configuration API call
 * and populates a PersonalIdSessionData instance.
 */
public class StartConfigurationResponseParser implements PersonalIdApiResponseParser {

    /**
     * Parses and sets values on the given PersonalIdSessionData instance.
     *
     * @param sessionData the instance to populate
     * @throws JSONException if a parsing error occurs
     */
    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        sessionData.setRequiredLock(JsonExtensions.optStringSafe(json, "required_lock", null));
        sessionData.setDemoUser(json.optBoolean("demo_user", false));
        sessionData.setToken(JsonExtensions.optStringSafe(json, "token", null));
        sessionData.setSmsMethod(JsonExtensions.optStringSafe(json, "sms_method", null));
        sessionData.setSessionFailureCode(JsonExtensions.optStringSafe(json, "failure_code", null));
        sessionData.setSessionFailureSubcode(JsonExtensions.optStringSafe(json, "failure_subcode", null));
    }
}

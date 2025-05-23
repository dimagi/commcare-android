package org.commcare.connect.network.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.utils.JsonExtensions;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses a JSON response from the check_name API call
 * and populates a PersonalIdSessionData instance.
 */
public class AddOrVerifyNameParser implements PersonalIdApiResponseParser {
    /**
     * Parses and sets values on the given PersonalIdSessionData instance.
     *
     * @param sessionData the instance to populate
     * @throws JSONException if a parsing error occurs
     */

    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        sessionData.setAccountExists(json.optBoolean("account_exists", false));
        sessionData.setToken(JsonExtensions.optStringSafe(json, "photo", null));
    }
}

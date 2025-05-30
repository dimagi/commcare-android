package org.commcare.connect.network.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.utils.JsonExtensions;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses a JSON response from the complete profile API call
 * and populates a PersonalIdSessionData instance.
 */
public class CompleteProfileResponseParser implements PersonalIdApiResponseParser{
    /**
     * Parses and sets values on the given PersonalIdSessionData instance.
     *
     * @param sessionData the instance to populate
     * @throws JSONException if a parsing error occurs
     */
    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        sessionData.setPersonalId(json.getString("username"));
        sessionData.setDbKey(json.getString("db_key"));
        sessionData.setOauthPassword(json.getString("password"));
    }
}

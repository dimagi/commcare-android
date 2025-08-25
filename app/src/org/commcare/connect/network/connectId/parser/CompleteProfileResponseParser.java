package org.commcare.connect.network.connectId.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.utils.JsonExtensions;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * Parses a JSON response from the complete profile API call
 * and populates a PersonalIdSessionData instance.
 */
public class CompleteProfileResponseParser implements PersonalIdApiResponseParser {
    /**
     * Parses and sets values on the given PersonalIdSessionData instance.
     *
     * @param sessionData the instance to populate
     * @throws JSONException if a parsing error occurs
     */
    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        sessionData.setPersonalId(Objects.requireNonNull(JsonExtensions.optStringSafe(json, "username", null)));
        sessionData.setDbKey(Objects.requireNonNull(JsonExtensions.optStringSafe(json, "db_key", null)));
        sessionData.setOauthPassword(Objects.requireNonNull(JsonExtensions.optStringSafe(json, "password", null)));
        sessionData.setInvitedUser(json.optBoolean("invited_user", false));
    }
}

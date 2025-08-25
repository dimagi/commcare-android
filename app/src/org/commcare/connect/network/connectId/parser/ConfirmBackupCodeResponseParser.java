package org.commcare.connect.network.connectId.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.utils.JsonExtensions;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses a JSON response from the confirm backup code API call
 * and populates a PersonalIdSessionData instance.
 */
public class ConfirmBackupCodeResponseParser implements PersonalIdApiResponseParser{
    /**
     * Parses and sets values on the given PersonalIdSessionData instance.
     *
     * @param sessionData the instance to populate
     * @throws JSONException if a parsing error occurs
     */
    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        sessionData.setPersonalId(JsonExtensions.optStringSafe(json, "username", null));
        sessionData.setDbKey(JsonExtensions.optStringSafe(json, "db_key", null));
        if (json.has("attempts_left")) {
            sessionData.setAttemptsLeft(json.getInt("attempts_left"));
        }
        if (json.has("error_code")) {
            sessionData.setSessionFailureCode(json.getString("error_code"));
        }
        sessionData.setOauthPassword(JsonExtensions.optStringSafe(json, "password", null));
        sessionData.setInvitedUser(json.optBoolean("invited_user", false));
    }
}

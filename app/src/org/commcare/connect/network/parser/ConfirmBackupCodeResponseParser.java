package org.commcare.connect.network.parser;

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
        sessionData.setUsername(JsonExtensions.optStringSafe(json, "username", null));
        sessionData.setDbKey(JsonExtensions.optStringSafe(json, "db_key", null));
        sessionData.setAccountOrphaned(json.optBoolean("account_orphaned", false));
        sessionData.setOauthPassword(JsonExtensions.optStringSafe(json, "password", null));
    }
}

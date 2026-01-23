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
        String username = JsonExtensions.optStringSafe(json, "username", null);
        String dbKey = JsonExtensions.optStringSafe(json, "db_key", null);
        String password = JsonExtensions.optStringSafe(json, "password", null);

        Objects.requireNonNull(username);
        Objects.requireNonNull(dbKey);
        Objects.requireNonNull(password);
        if (username.isEmpty() || dbKey.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException(
                    "Any of the fields amongst username, db_key or password cannot be empty");
        }

        sessionData.setPersonalId(username);
        sessionData.setDbKey(dbKey);
        sessionData.setOauthPassword(password);
        sessionData.setInvitedUser(json.optBoolean("invited_user", false));
    }
}

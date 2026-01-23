package org.commcare.connect.network.connectId.parser;

import com.google.common.base.Strings;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.utils.JsonExtensions;
import org.javarosa.core.model.utils.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * Parses a JSON response from the confirm backup code API call
 * and populates a PersonalIdSessionData instance.
 */
public class ConfirmBackupCodeResponseParser implements PersonalIdApiResponseParser {
    /**
     * Parses and sets values on the given PersonalIdSessionData instance.
     *
     * @param sessionData the instance to populate
     * @throws JSONException if a parsing error occurs
     */
    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {

        if (json.has("attempts_left")) {    // whenever wrong code is entered, server responed with attempts_left
            sessionData.setAttemptsLeft(json.getInt("attempts_left"));
        } else if (json.has("error_code")) {    // whenever account is locked, server responed with error_code
            sessionData.setSessionFailureCode(json.getString("error_code"));
        } else { // whenever backup code is correct
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

            sessionData.setPreviousDevice(JsonExtensions.optStringSafe(json, "previous_device", null));
            String lastAccessedString = JsonExtensions.optStringSafe(json, "last_accessed", null);
            if(!Strings.isNullOrEmpty(lastAccessedString)) {
                sessionData.setLastAccessed(DateUtils.parseDate(lastAccessedString));
            }
        }
    }
}

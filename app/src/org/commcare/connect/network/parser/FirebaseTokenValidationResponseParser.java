package org.commcare.connect.network.parser;

import android.util.Log;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.json.JSONException;
import org.json.JSONObject;

public class FirebaseTokenValidationResponseParser implements PersonalIdApiResponseParser {

    /**
     * Parses and sets values on the given PersonalIdSessionData instance.
     *
     * @param sessionData the instance to populate
     * @throws JSONException if a parsing error occurs
     */
    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        if (json.has("error")) {
            sessionData.setSessionFailureCode(json.getString("error"));
        } else {
            // If JSON has no "error", assume it's valid (success)
            Log.e("TAG", "parse: FirebaseTokenValidationParser");
        }
    }
}

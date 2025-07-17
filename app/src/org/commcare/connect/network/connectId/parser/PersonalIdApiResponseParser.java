package org.commcare.connect.network.connectId.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.json.JSONException;
import org.json.JSONObject;

public interface PersonalIdApiResponseParser {
    void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException;
}
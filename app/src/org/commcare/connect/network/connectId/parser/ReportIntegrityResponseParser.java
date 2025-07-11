package org.commcare.connect.network.connectId.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.json.JSONException;
import org.json.JSONObject;

public class ReportIntegrityResponseParser implements PersonalIdApiResponseParser {
    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        if (json.has("result_code")) {
            sessionData.setResultCode(json.optInt("result_code", -1));
        }
    }
} 
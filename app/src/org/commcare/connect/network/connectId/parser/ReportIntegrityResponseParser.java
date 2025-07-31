package org.commcare.connect.network.connectId.parser;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.json.JSONException;
import org.json.JSONObject;

public class ReportIntegrityResponseParser implements PersonalIdApiResponseParser {
    private final String requestId;

    public ReportIntegrityResponseParser(String requestId) {
        this.requestId = requestId;
    }

    @Override
    public void parse(JSONObject json, PersonalIdSessionData sessionData) throws JSONException {
        FirebaseAnalyticsUtil.reportPersonalIdIntegritySubmission(requestId,
                json.optString("result_code", "NoCodeFromServer"));
    }
} 
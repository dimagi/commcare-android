package org.commcare.connect.network.connectId.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.base.BaseApiHandler;
import org.commcare.connect.network.base.BaseApiResponseParser;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public class ReportIntegrityResponseParser<T> implements BaseApiResponseParser<T> {

    @Override
    public T parse(int responseCode, @NonNull InputStream responseData, @Nullable Object anyInputObject) throws IOException,JSONException {
        JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(responseData)));
        FirebaseAnalyticsUtil.reportPersonalIdIntegritySubmission(((String)anyInputObject),
                json.optString("result_code", "NoCodeFromServer"));
        return (T)Boolean.TRUE;
    }
}
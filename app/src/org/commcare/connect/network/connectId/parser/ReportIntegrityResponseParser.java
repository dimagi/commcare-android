package org.commcare.connect.network.connectId.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.base.BaseApiHandler;
import org.commcare.connect.network.base.BaseApiResponseParser;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.utils.JsonExtensions;
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
        String resultCode = JsonExtensions.optStringSafe(json, "result_code", "NoCodeFromServer");
        FirebaseAnalyticsUtil.reportPersonalIdHeartbeatIntegritySubmission(((String)anyInputObject), resultCode);
        return (T)Boolean.TRUE;
    }
}

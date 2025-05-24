package org.commcare.connect.network;

import android.app.Activity;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.parser.AddOrVerifyNameParser;
import org.commcare.connect.network.parser.PersonalIdApiResponseParser;
import org.commcare.connect.network.parser.StartConfigurationResponseParser;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public abstract class PersonalIdApiHandler {

    public enum PersonalIdApiErrorCodes {
        NETWORK_ERROR,
        OLD_API_ERROR,
        TOKEN_UNAVAILABLE_ERROR,
        TOKEN_DENIED_ERROR,
        INVALID_RESPONSE_ERROR,
        JSON_PARSING_ERROR;
    }

    private IApiCallback createCallback(PersonalIdSessionData sessionData,
                                        PersonalIdApiResponseParser parser,
                                        PersonalIdApiErrorCodes defaultFailureCode) {
        return new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try (InputStream in = responseData) {
                    JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(in)));
                    parser.parse(json, sessionData);
                    onSuccess(sessionData);
                } catch (IOException | JSONException e) {
                    Logger.exception("Error parsing API response", e);
                    onFailure(PersonalIdApiErrorCodes.JSON_PARSING_ERROR);
                }
            }

            @Override
            public void processFailure(int responseCode) {
                onFailure(defaultFailureCode);
            }

            @Override
            public void processNetworkFailure() {
                onFailure(PersonalIdApiErrorCodes.NETWORK_ERROR);
            }

            @Override
            public void processTokenUnavailableError() {
                onFailure(PersonalIdApiErrorCodes.TOKEN_UNAVAILABLE_ERROR);
            }

            @Override
            public void processTokenRequestDeniedError() {
                onFailure(PersonalIdApiErrorCodes.TOKEN_DENIED_ERROR);
            }

            @Override
            public void processOldApiError() {
                onFailure(PersonalIdApiErrorCodes.OLD_API_ERROR);
            }
        };
    }

    public void makeStartConfigurationCall(Activity activity,
                    Map<String, String> body,
                    String integrityToken,
                    String requestHash) {
        PersonalIdSessionData sessionData = new PersonalIdSessionData();
        ApiPersonalId.startConfiguration(activity, body, integrityToken, requestHash,
                createCallback(sessionData,
                        new StartConfigurationResponseParser(),
                        PersonalIdApiErrorCodes.INVALID_RESPONSE_ERROR));
    }

    public void addOrVerifyNameCall(Activity activity, String name, PersonalIdSessionData sessionData) {
        ApiPersonalId.addOrVerifyName(activity, name,
                createCallback(sessionData,
                        new AddOrVerifyNameParser(),
                        PersonalIdApiErrorCodes.INVALID_RESPONSE_ERROR));
    }

    protected abstract void onSuccess(PersonalIdSessionData sessionData);

    protected abstract void onFailure(PersonalIdApiErrorCodes errorCode);
}

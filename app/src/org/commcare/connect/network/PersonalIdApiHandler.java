package org.commcare.connect.network;

import android.app.Activity;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.parser.AddOrVerifyNameParser;
import org.commcare.connect.network.parser.StartConfigurationResponseParser;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public abstract class PersonalIdApiHandler {

    public enum PersonalIdApiErrorCodes {
        NETWORK_ERROR,
        OLD_API_ERROR,
        TOKEN_UNAVAILABLE_ERROR,
        TOKEN_DENIED_ERROR,
        INVALID_RESPONSE_ERROR,
        JSON_PARSING_ERROR;
    }

    public void makeStartConfigurationCall(Activity activity, String phone) {
        ApiPersonalId.startConfiguration(activity, phone, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try (InputStream in = responseData) {
                    JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(responseData)));
                    PersonalIdSessionData sessionData = new PersonalIdSessionData();
                    StartConfigurationResponseParser parser = new StartConfigurationResponseParser(json);
                    parser.parse(sessionData);
                    onSuccess(sessionData);
                } catch (IOException | JSONException e) {
                    Logger.exception("Error parsing recovery response", e);
                    onFailure(PersonalIdApiErrorCodes.JSON_PARSING_ERROR);
                }
            }

            @Override
            public void processFailure(int responseCode) {
                onFailure(PersonalIdApiErrorCodes.INVALID_RESPONSE_ERROR);
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
        });
    }

    public void addOrVerifyNameCall(Activity activity, String name, PersonalIdSessionData sessionData) {
        ApiPersonalId.addOrVerifyName(activity, name, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try (InputStream in = responseData) {
                    JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(responseData)));
                    AddOrVerifyNameParser parser = new AddOrVerifyNameParser(json);
                    parser.parse(sessionData);
                    onSuccess(sessionData);
                } catch (IOException | JSONException e) {
                    Logger.exception("Error parsing recovery response", e);
                    onFailure(PersonalIdApiErrorCodes.JSON_PARSING_ERROR);
                }
            }

            @Override
            public void processFailure(int responseCode) {
                onFailure(PersonalIdApiErrorCodes.INVALID_RESPONSE_ERROR);
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
        });
    }

    protected abstract void onSuccess(PersonalIdSessionData sessionData);

    protected abstract void onFailure(PersonalIdApiErrorCodes errorCode);
}

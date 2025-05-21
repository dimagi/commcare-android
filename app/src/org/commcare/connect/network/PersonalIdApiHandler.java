package org.commcare.connect.network;

import android.app.Activity;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.network.parser.StartConfigurationResponseParser;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public abstract class PersonalIdApiHandler {

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
                    onFailure(ConnectConstants.JSON_PARSING_ERROR);
                }
            }

            @Override
            public void processFailure(int responseCode) {
                onFailure(ConnectConstants.API_ERROR);
            }

            @Override
            public void processNetworkFailure() {
                onFailure(ConnectConstants.NETWORK_ERROR);
            }

            @Override
            public void processTokenUnavailableError() {
                onFailure(ConnectConstants.TOKEN_UNAVAILABLE_ERROR);
            }

            @Override
            public void processTokenRequestDeniedError() {
                onFailure(ConnectConstants.TOKEN_DENIED_ERROR);
            }

            @Override
            public void processOldApiError() {
                onFailure(ConnectConstants.OLD_API_ERROR);
            }
        });
    }

    protected abstract void onSuccess(PersonalIdSessionData sessionData);

    protected abstract void onFailure(int errorCode);
}

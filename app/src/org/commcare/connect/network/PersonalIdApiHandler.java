package org.commcare.connect.network;

import android.app.Activity;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.parser.StartConfigurationResponseParser;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

public abstract class PersonalIdApiHandler {

    public void makeConfigurationCall(Activity activity, String phone) {
        ApiPersonalId.startConfiguration(activity, phone, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(responseData)));
                    PersonalIdSessionData sessionData = new PersonalIdSessionData();
                    StartConfigurationResponseParser parser = new StartConfigurationResponseParser(json);
                    parser.parse(sessionData);
                    onSuccess();
                } catch (IOException | JSONException e) {
                    Logger.exception("Error parsing recovery response", e);
                    onFailure();
                }
            }

            @Override
            public void processFailure(int responseCode) {
                onFailure();
            }

            @Override
            public void processNetworkFailure() {
                ConnectNetworkHelper.showNetworkError(activity);
            }

            @Override
            public void processTokenUnavailableError() {
                ConnectNetworkHelper.handleTokenUnavailableException(activity);
            }

            @Override
            public void processTokenRequestDeniedError() {
                ConnectNetworkHelper.handleTokenDeniedException(activity);
            }

            @Override
            public void processOldApiError() {
                ConnectNetworkHelper.showOutdatedApiError(activity);
            }
        });
    }

    protected abstract void onSuccess();

    protected abstract void onFailure();
}

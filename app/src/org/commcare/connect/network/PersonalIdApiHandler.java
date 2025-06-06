package org.commcare.connect.network;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.parser.AddOrVerifyNameParser;
import org.commcare.connect.network.parser.CompleteProfileResponseParser;
import org.commcare.connect.network.parser.ConfirmBackupCodeResponseParser;
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
        FORBIDDEN_ERROR,
        TOKEN_UNAVAILABLE_ERROR,
        TOKEN_DENIED_ERROR,
        INVALID_RESPONSE_ERROR,
        JSON_PARSING_ERROR;

        public boolean shouldAllowRetry(){
            return this == NETWORK_ERROR || this == TOKEN_UNAVAILABLE_ERROR;
        }
    }

    private IApiCallback createCallback(PersonalIdSessionData sessionData,
                                        PersonalIdApiResponseParser parser) {
        return new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                if (parser != null) {
                    try (InputStream in = responseData) {
                        JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(in)));
                        parser.parse(json, sessionData);
                    } catch (JSONException e) {
                        Logger.exception("JSON error parsing API response", e);
                        onFailure(PersonalIdApiErrorCodes.JSON_PARSING_ERROR, e);
                    } catch (IOException e) {
                        Logger.exception("Error parsing API response", e);
                        onFailure(PersonalIdApiErrorCodes.NETWORK_ERROR, e);
                    }
                }
                onSuccess(sessionData);
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse) {
                if(responseCode == 403) {
                    onFailure(PersonalIdApiErrorCodes.FORBIDDEN_ERROR, null);
                    return;
                }

                StringBuilder info = new StringBuilder("Response " + responseCode);
                if (errorResponse != null) {
                    try (InputStream in = errorResponse) {
                        JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(in)));
                        if (json.has("error")) {
                            info.append(": ").append(json.optString("error"));
                            Toast.makeText(CommCareApplication.instance(), json.optString("error"),
                                    Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        Logger.exception("JSON error parsing API error response", e);
                        onFailure(PersonalIdApiErrorCodes.JSON_PARSING_ERROR, e);
                        return;
                    } catch (IOException e) {
                        Logger.exception("Error parsing API error response", e);
                        onFailure(PersonalIdApiErrorCodes.NETWORK_ERROR, e);
                        return;
                    }
                }
                onFailure(PersonalIdApiErrorCodes.INVALID_RESPONSE_ERROR, new Exception(info.toString()));
            }

            @Override
            public void processNetworkFailure() {
                onFailure(PersonalIdApiErrorCodes.NETWORK_ERROR, null);
            }

            @Override
            public void processTokenUnavailableError() {
                onFailure(PersonalIdApiErrorCodes.TOKEN_UNAVAILABLE_ERROR, null);
            }

            @Override
            public void processTokenRequestDeniedError() {
                onFailure(PersonalIdApiErrorCodes.TOKEN_DENIED_ERROR, null);
            }

            @Override
            public void processOldApiError() {
                onFailure(PersonalIdApiErrorCodes.OLD_API_ERROR, null);
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
                        new StartConfigurationResponseParser()));
    }
    public void validateFirebaseIdToken(Activity activity, String firebaseIdToken,PersonalIdSessionData sessionData) {
        ApiPersonalId.validateFirebaseIdToken(sessionData.getToken(),activity,firebaseIdToken,
                createCallback(sessionData,
                        null));
    }

    public void addOrVerifyNameCall(Activity activity, String name, PersonalIdSessionData sessionData) {
        ApiPersonalId.addOrVerifyName(activity, name, sessionData.getToken(),
                createCallback(sessionData,
                        new AddOrVerifyNameParser()));
    }

    public void confirmBackupCode(Activity activity, String backupCode, PersonalIdSessionData sessionData) {
        ApiPersonalId.confirmBackupCode(activity, backupCode, sessionData.getToken(),
                createCallback(sessionData,
                        new ConfirmBackupCodeResponseParser()));
    }

    public void completeProfile(Context context, String userName,
                                String photoAsBase64, String backupCode, String token,PersonalIdSessionData sessionData) {
        ApiPersonalId.setPhotoAndCompleteProfile(context, userName, photoAsBase64, backupCode, token,
                createCallback(sessionData,
                        new CompleteProfileResponseParser()));
    }


    protected abstract void onSuccess(PersonalIdSessionData sessionData);

    protected abstract void onFailure(PersonalIdApiErrorCodes errorCode, Throwable t);
}

package org.commcare.connect.network.connectId;

import android.app.Activity;
import android.content.Context;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.ApiPersonalId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.network.base.BaseApiCallback;
import org.commcare.connect.network.base.BaseApiHandler;
import org.commcare.connect.network.connectId.parser.RetrieveCredentialsResponseParser;
import org.commcare.connect.network.connectId.parser.AddOrVerifyNameParser;
import org.commcare.connect.network.connectId.parser.CompleteProfileResponseParser;
import org.commcare.connect.network.connectId.parser.ConfirmBackupCodeResponseParser;
import org.commcare.connect.network.connectId.parser.PersonalIdApiResponseParser;
import org.commcare.connect.network.connectId.parser.StartConfigurationResponseParser;
import org.commcare.util.LogTypes;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public abstract class PersonalIdApiHandler<T> extends BaseApiHandler<T> {


    private IApiCallback createCallback(PersonalIdSessionData sessionData,
                                        PersonalIdApiResponseParser parser) {
        return new BaseApiCallback<T>(this) {

            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                if (parser != null) {
                    try (InputStream in = responseData) {
                        JSONObject json = new JSONObject(new String(StreamsUtil.inputStreamToByteArray(in)));
                        parser.parse(json, sessionData);
                    } catch (JSONException e) {
                        Logger.exception("JSON error parsing API response", e);
                        onFailure(PersonalIdOrConnectApiErrorCodes.JSON_PARSING_ERROR, e);
                    } catch (IOException e) {
                        Logger.exception("Error parsing API response", e);
                        onFailure(PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR, e);
                    }
                }
                onSuccess((T)sessionData);
            }

            @Override
            public void processFailure(int responseCode, InputStream errorResponse, String url) {
                if (!handleErrorCodeIfPresent(errorResponse, sessionData)) {
                    super.processFailure(responseCode, null, url);
                }
            }
        };
    }

    private boolean handleErrorCodeIfPresent(InputStream errorResponse, PersonalIdSessionData sessionData) {
        try {
            if (errorResponse != null) {
                byte[] errorBytes = StreamsUtil.inputStreamToByteArray(errorResponse);
                String jsonStr = new String(errorBytes, java.nio.charset.StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(jsonStr);

                String errorCode = json.optString("error_code", "");
                sessionData.setSessionFailureCode(errorCode);
                if ("LOCKED_ACCOUNT".equalsIgnoreCase(errorCode)) {
                    onFailure(PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR, null);
                    return true;
                } else if ("INTEGRITY_ERROR".equalsIgnoreCase(errorCode)) {
                    if (json.has("sub_code")) {
                        String subErrorCode = json.optString("sub_code");
                        Logger.log(LogTypes.TYPE_MAINTENANCE, "Integrity error with subcode " + subErrorCode);
                        sessionData.setSessionFailureSubcode(subErrorCode);
                        onFailure(PersonalIdOrConnectApiErrorCodes.INTEGRITY_ERROR, null);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            Logger.exception("Error parsing error_code", e);
        }
        return false;
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

    public void validateFirebaseIdToken(Activity activity, String firebaseIdToken, PersonalIdSessionData sessionData) {
        ApiPersonalId.validateFirebaseIdToken(sessionData.getToken(), activity, firebaseIdToken,
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
                                String photoAsBase64, String backupCode, String token, PersonalIdSessionData sessionData) {
        ApiPersonalId.setPhotoAndCompleteProfile(context, userName, photoAsBase64, backupCode, token,
                createCallback(sessionData,
                        new CompleteProfileResponseParser()));
    }

    public void retrieveCredentials(Context context, String userName, String password) {
        ApiPersonalId.retrieveCredentials(context, userName, password,
                createCallback(
                        new RetrieveCredentialsResponseParser<T>()));
    }

    public void sendOtp(Activity activity, PersonalIdSessionData sessionData) {
        ApiPersonalId.sendOtp(activity, sessionData.getToken(),
                createCallback(sessionData, null));
    }

    public void validateOtp(Activity activity, String otp, PersonalIdSessionData sessionData) {
        ApiPersonalId.validateOtp(activity, sessionData.getToken(), otp,
                createCallback(sessionData, null));
    }

}

package org.commcare.connect.network.connectId;

import android.app.Activity;
import android.content.Context;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.ApiPersonalId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.connect.network.NoParsingResponseParser;
import org.commcare.connect.network.base.BaseApiCallback;
import org.commcare.connect.network.base.BaseApiHandler;
import org.commcare.connect.network.connectId.parser.AddOrVerifyNameParser;
import org.commcare.connect.network.connectId.parser.CompleteProfileResponseParser;
import org.commcare.connect.network.connectId.parser.ConfirmBackupCodeResponseParser;
import org.commcare.connect.network.connectId.parser.ConnectTokenResponseParser;
import org.commcare.connect.network.connectId.parser.PersonalIdApiResponseParser;
import org.commcare.connect.network.connectId.parser.ReportIntegrityResponseParser;
import org.commcare.connect.network.connectId.parser.RetrieveNotificationsResponseParser;
import org.commcare.connect.network.connectId.parser.RetrieveWorkHistoryResponseParser;
import org.commcare.connect.network.connectId.parser.NotificationParseResult;
import org.commcare.connect.services.NotificationService;
import org.commcare.connect.services.ProcessedNotificationResult;
import org.commcare.connect.network.connectId.parser.StartConfigurationResponseParser;
import org.commcare.interfaces.base.BaseConnectView;
import org.commcare.util.LogTypes;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import kotlin.Pair;

import static org.commcare.connect.network.NetworkUtils.getErrorCodes;

public abstract class PersonalIdApiHandler<T> extends BaseApiHandler<T> {


    public PersonalIdApiHandler() {
        super();
    }

    public PersonalIdApiHandler(Boolean loading, BaseConnectView baseView) {
        super(loading, baseView);
    }

    private IApiCallback createCallback(
            PersonalIdSessionData sessionData,
            PersonalIdApiResponseParser parser
    ) {
        return new BaseApiCallback<T>(this) {

            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                if (parser != null) {
                    try (InputStream in = responseData) {
                        JSONObject json = new JSONObject(
                                new String(StreamsUtil.inputStreamToByteArray(in))
                        );
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
            public void processFailure(int responseCode, String url, String errorBody) {
                Pair<String, String> errorCodes = getErrorCodes(errorBody);
                if (!handleErrorCodeIfPresent(errorCodes.getFirst(), errorCodes.getSecond(), sessionData)) {
                    super.processFailure(responseCode, url, errorBody);
                }
            }
        };
    }

    private boolean handleErrorCodeIfPresent(
            String errorCode,
            String errorSubCode,
            PersonalIdSessionData sessionData
    ) {
        sessionData.setSessionFailureCode(errorCode);
        switch (errorCode) {
            case "LOCKED_ACCOUNT":
                onFailure(PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR, null);
                return true;
            case "INTEGRITY_ERROR":
                Logger.log(
                        LogTypes.TYPE_MAINTENANCE,
                        "Integrity error with subcode " + errorSubCode
                );
                sessionData.setSessionFailureSubcode(errorSubCode);
                onFailure(PersonalIdOrConnectApiErrorCodes.INTEGRITY_ERROR, null);
                return true;
            case "INVALID_TOKEN":
                onFailure(PersonalIdOrConnectApiErrorCodes.TOKEN_INVALID_ERROR, null);
                return true;
            case "INCORRECT_OTP":
                onFailure(PersonalIdOrConnectApiErrorCodes.INCORRECT_OTP_ERROR, null);
                return true;
            case "NO_RECOVERY_PIN_SET":
                onFailure(
                        PersonalIdOrConnectApiErrorCodes.NO_RECOVERY_PIN_SET_ERROR,
                        new Throwable("This user does not have a backup code setup yet.")
                );
                return true;
            case "FAILED_TO_UPLOAD":
                // This error code relates to uploading a user profile photo.
                onFailure(PersonalIdOrConnectApiErrorCodes.SERVER_ERROR, null);
                return true;
            case "FILE_TOO_LARGE":
                // This error code relates to uploading a user profile photo.
                onFailure(
                        PersonalIdOrConnectApiErrorCodes.FILE_TOO_LARGE_ERROR,
                        new Throwable("The user's photo is too large for server to handle.")
                );
                return true;
            case "MISSING_DATA":
                onFailure(
                        PersonalIdOrConnectApiErrorCodes.MISSING_DATA_ERROR,
                        new Throwable("API call failed due to missing data with error subcode: " + errorSubCode)
                );
                return true;
            case "PHONE_MISMATCH":
                onFailure(
                        PersonalIdOrConnectApiErrorCodes.PHONE_MISMATCH_ERROR,
                        new Throwable("There was a phone number mismatch when validating the firebase ID token.")
                );
                return true;
            case "MISSING_TOKEN":
                onFailure(
                        PersonalIdOrConnectApiErrorCodes.MISSING_TOKEN_ERROR,
                        new Throwable("Can't validate the firebase ID token because it is missing.")
                );
                return true;
            case "FAILED_VALIDATING_TOKEN":
                onFailure(
                        PersonalIdOrConnectApiErrorCodes.FAILED_VALIDATING_TOKEN_ERROR,
                        new Throwable("There was an issue verifying the firebase ID token.")
                );
                return true;
            case "NAME_REQUIRED":
                onFailure(
                        PersonalIdOrConnectApiErrorCodes.NAME_REQUIRED_ERROR,
                        new Throwable("The user's name is missing.")
                );
                return true;
            case "PHONE_NOT_VALIDATED", "UNSUPPORTED_COUNTRY", "NOT_ALLOWED":
                // The "NOT_ALLOWED" error code relates to an uninvited user receiving an OTP.
                onFailure(PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR, null);
                return true;
            case "ACTIVE_USER_EXISTS":
                onFailure(
                        PersonalIdOrConnectApiErrorCodes.ACTIVE_USER_EXISTS_ERROR,
                        new Throwable("The user attempted to create a new profile with a phone number that is already tied to an existing account.")
                );
                return true;
            default:
                return false;
        }
    }


    public void makeIntegrityReportCall(
            Context context,
            String requestId,
            Map<String, String> body,
            String integrityToken,
            String requestHash
    ) {
        ApiPersonalId.reportIntegrity(
                context,
                body,
                integrityToken,
                requestHash,
                createCallback(new ReportIntegrityResponseParser<>(), requestId)
        );
    }

    public void makeStartConfigurationCall(
            Activity activity,
            Map<String, String> body,
            String integrityToken,
            String requestHash
    ) {
        PersonalIdSessionData sessionData = new PersonalIdSessionData();
        ApiPersonalId.startConfiguration(
                activity,
                body,
                integrityToken,
                requestHash,
                createCallback(sessionData, new StartConfigurationResponseParser())
        );
    }

    public void validateFirebaseIdToken(
            Activity activity,
            String firebaseIdToken,
            PersonalIdSessionData sessionData
    ) {
        ApiPersonalId.validateFirebaseIdToken(
                sessionData.getToken(),
                activity,
                firebaseIdToken,
                createCallback(sessionData, null)
        );
    }

    public void addOrVerifyNameCall(
            Activity activity,
            String name,
            PersonalIdSessionData sessionData
    ) {
        ApiPersonalId.addOrVerifyName(
                activity,
                name,
                sessionData.getToken(),
                createCallback(sessionData, new AddOrVerifyNameParser())
        );
    }

    public void confirmBackupCode(
            Activity activity,
            String backupCode,
            PersonalIdSessionData sessionData
    ) {
        ApiPersonalId.confirmBackupCode(
                activity,
                backupCode,
                sessionData.getToken(),
                createCallback(sessionData, new ConfirmBackupCodeResponseParser())
        );
    }

    public void completeProfile(
            Context context,
            String userName,
            String photoAsBase64,
            String backupCode,
            String token,
            PersonalIdSessionData sessionData
    ) {
        ApiPersonalId.setPhotoAndCompleteProfile(
                context,
                userName,
                photoAsBase64,
                backupCode,
                token,
                createCallback(sessionData, new CompleteProfileResponseParser())
        );
    }

    public void retrieveWorkHistory(Context context, String userId, String password) {
        ApiPersonalId.retrieveWorkHistory(
                context,
                userId,
                password,
                createCallback(new RetrieveWorkHistoryResponseParser<>(context), null)
        );
    }

    public void sendOtp(Activity activity, PersonalIdSessionData sessionData) {
        ApiPersonalId.sendOtp(
                activity,
                sessionData.getToken(),
                createCallback(sessionData, null)
        );
    }

    public void validateOtp(Activity activity, String otp, PersonalIdSessionData sessionData) {
        ApiPersonalId.validateOtp(
                activity,
                sessionData.getToken(),
                otp,
                createCallback(sessionData, null)
        );
    }

    public void connectToken(Context context, ConnectUserRecord user) {
        ApiPersonalId.retrievePersonalIdToken(
                context,
                user,
                createCallback(new ConnectTokenResponseParser<>(), user)
        );
    }

    public void heartbeatRequest(Context context, ConnectUserRecord user) {
        ApiPersonalId.makeHeartbeatRequest(
                context,
                user,
                createCallback(new NoParsingResponseParser<>(), null)
        );
    }

    public void retrieveNotifications(Context context, ConnectUserRecord user) {
        ApiPersonalId.retrieveNotifications(
                context,
                user.getUserId(),
                user.getPassword(),
                createNotificationCallback(context));
    }
    
    private IApiCallback createNotificationCallback(Context context) {
        onStart();
        return new BaseApiCallback<T>(this) {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    RetrieveNotificationsResponseParser parser = new RetrieveNotificationsResponseParser(context);
                    NotificationParseResult parseResult = parser.parse(responseCode, responseData, null);
                    ProcessedNotificationResult processedResult = 
                        NotificationService.INSTANCE.processNotificationData(context, parseResult);
                    onSuccess((T) processedResult);
                    onStop();
                } catch (Exception e) {
                    Logger.exception("Error processing notification data", e);
                    stopLoadingAndInformError(PersonalIdOrConnectApiErrorCodes.JSON_PARSING_ERROR, e);
                }
            }
        };
    }

    public void updateNotifications(
            Context context,
            String userId,
            String password,
            List<String> notificationId
    ) {
        ApiPersonalId.updateNotifications(
                context,
                userId,
                password,
                createCallback(new NoParsingResponseParser<>(), null),
                notificationId
        );
    }

}

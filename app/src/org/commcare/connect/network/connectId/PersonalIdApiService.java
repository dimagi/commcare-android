package org.commcare.connect.network.connectId;

import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface PersonalIdApiService {

    @POST(PersonalIdApiEndpoints.REPORT_INTEGRITY)
    Call<ResponseBody> reportIntegrity(@Header("CC-Integrity-Token") String integrityToken,
                                          @Header("CC-Request-Hash") String requestHash,
                                          @Body Map<String, String> reportRequest);

    @POST(PersonalIdApiEndpoints.START_CONFIGURATION)
    Call<ResponseBody> startConfiguration(@Header("CC-Integrity-Token") String integrityToken,
                                          @Header("CC-Request-Hash") String requestHash,
                                          @Body Map<String, String> registrationRequest);

    @POST(PersonalIdApiEndpoints.VALIDATE_FIREBASE_ID_TOKEN)
    Call<ResponseBody> validateFirebaseIdToken(@Header("Authorization") String token,
                                               @Body Map<String, String> firebaseIdToken);

    @POST(PersonalIdApiEndpoints.CHECK_NAME)
    Call<ResponseBody> checkName(@Header("Authorization") String token,
                                 @Body Map<String, String> nameRequest);

    @POST(PersonalIdApiEndpoints.UPDATE_PROFILE)
    Call<ResponseBody> updateProfile(@Header("Authorization") String token,
                                     @Body Map<String, String> updateProfile);

    @POST(PersonalIdApiEndpoints.COMPLETE_PROFILE)
    Call<ResponseBody> completeProfile(@Header("Authorization") String token,
                                       @Body Map<String, String> body);

    @POST(PersonalIdApiEndpoints.CONFIRM_BACKUP_CODE)
    Call<ResponseBody> confirmBackupCode(@Header("Authorization") String token,
                                         @Body Map<String, String> confirmBackupCodeRequest);

    @GET(PersonalIdApiEndpoints.CREDENTIALS)
    Call<ResponseBody> retrieveCredentials(@Header("Authorization") String token);

    @POST(PersonalIdApiEndpoints.SEND_SESSION_OTP)
    Call<ResponseBody> sendSessionOtp(@Header("Authorization") String token);

    @POST(PersonalIdApiEndpoints.VALIDATE_SESSION_OTP)
    Call<ResponseBody> validateSessionOtp(@Header("Authorization") String token,
            @Body Map<String, String> body);

    @POST(PersonalIdApiEndpoints.SEND_EMAIL_OTP)
    Call<ResponseBody> sendEmailOtp(@Header("Authorization") String token,
                                    @Body Map<String, String> emailRequest);

    @POST(PersonalIdApiEndpoints.VERIFY_EMAIL_OTP)
    Call<ResponseBody> verifyEmailOtp(@Header("Authorization") String token,
                                      @Body Map<String, String> otpRequest);

    @POST(PersonalIdApiEndpoints.TOKEN_URL)
    Call<ResponseBody> connectToken(@HeaderMap Map<String, String> headers, @Body RequestBody connectTokenRequest);

    @POST(PersonalIdApiEndpoints.HEARTBEAT_URL)
    Call<ResponseBody> connectHeartbeat(@Header("Authorization") String token,
                                        @HeaderMap Map<String, String> headers,
                                        @Body RequestBody connectTokenRequest);

    @GET(PersonalIdApiEndpoints.RETRIEVE_NOTIFICATIONS)
    Call<ResponseBody> getAllNotifications(@Header("Authorization") String token);

    @POST(PersonalIdApiEndpoints.UPDATE_NOTIFICATIONS)
    Call<ResponseBody> updateNotification(@Header("Authorization") String token,
                                        @HeaderMap Map<String, String> headers,
                                        @Body RequestBody updateNotificationRequest);

    @POST(PersonalIdApiEndpoints.MESSAGE_CHANNEL_CONSENT_URL)
    Call<ResponseBody> updateChannelConsent(@Header("Authorization") String token,
                                          @HeaderMap Map<String, String> headers,
                                          @Body RequestBody updateChannelConsentRequest);

    @POST(PersonalIdApiEndpoints.MESSAGE_SEND_URL)
    Call<ResponseBody> sendMessagingMessage(@Header("Authorization") String token,
                                            @HeaderMap Map<String, String> headers,
                                            @Body RequestBody sendMessagingMessageRequest);

    @POST
    Call<ResponseBody> makePostRequest(
            @Url String url,
            @Header("Authorization") String token,
            @HeaderMap Map<String, String> headers,
            @Body RequestBody requestBody);

    @GET(PersonalIdApiEndpoints.RELEASE_TOGGLES)
    Call<ResponseBody> getReleaseToggles(@Header("Authorization") String token);
}

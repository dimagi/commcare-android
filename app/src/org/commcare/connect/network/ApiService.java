package org.commcare.connect.network;

import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    @POST(ApiEndPoints.reportIntegrity)
    Call<ResponseBody> reportIntegrity(@Header("CC-Integrity-Token") String integrityToken,
                                          @Header("CC-Request-Hash") String requestHash,
                                          @Body Map<String, String> reportRequest);

    @POST(ApiEndPoints.startConfiguration)
    Call<ResponseBody> startConfiguration(@Header("CC-Integrity-Token") String integrityToken,
                                          @Header("CC-Request-Hash") String requestHash,
                                          @Body Map<String, String> registrationRequest);

    @POST(ApiEndPoints.validateFirebaseIdToken)
    Call<ResponseBody> validateFirebaseIdToken(@Header("Authorization") String token,
                                               @Body Map<String, String> firebaseIdToken);

    @POST(ApiEndPoints.checkName)
    Call<ResponseBody> checkName(@Header("Authorization") String token,
                                 @Body Map<String, String> nameRequest);

    @POST(ApiEndPoints.updateProfile)
    Call<ResponseBody> updateProfile(@Header("Authorization") String token,
                                     @Body Map<String, String> updateProfile);

    @POST(ApiEndPoints.completeProfile)
    Call<ResponseBody> completeProfile(@Header("Authorization") String token,
                                       @Body Map<String, String> body);

    @POST(ApiEndPoints.confirmBackupCode)
    Call<ResponseBody> confirmBackupCode(@Header("Authorization") String token,
                                         @Body Map<String, String> confirmBackupCodeRequest);

    @GET(ApiEndPoints.CREDENTIALS)
    Call<ResponseBody> retrieveCredentials(@Header("Authorization") String token);


    @GET(ApiEndPoints.connectOpportunitiesURL)
    Call<ResponseBody> getConnectOpportunities(@Header("Authorization") String token, @HeaderMap Map<String, String> headers);


    @POST(ApiEndPoints.connectStartLearningURL)
    Call<ResponseBody> connectStartLearningApp(@Header("Authorization") String token,
                                               @HeaderMap Map<String, String> headers,
                                               @Body RequestBody connectStartLearningRequest);

    @GET(ApiEndPoints.connectLearnProgressURL)
    Call<ResponseBody> getConnectLearningAppProgress(@Header("Authorization") String token,
                                                     @Path("id") int id,
                                                     @HeaderMap Map<String, String> headers);


    @POST(ApiEndPoints.connectClaimJobURL)
    Call<ResponseBody> connectClaimJob(@Header("Authorization") String token,
                                       @Path("id") int id,
                                       @HeaderMap Map<String, String> headers,
                                       @Body RequestBody connectClaimJobRequest);

    @GET(ApiEndPoints.connectDeliveriesURL)
    Call<ResponseBody> getConnectDeliveries(@Header("Authorization") String token,
                                                     @Path("id") int id,
                                                     @HeaderMap Map<String, String> headers);

    @POST(ApiEndPoints.connectPaymentConfirmationURL)
    Call<ResponseBody> connectPaymentConfirmation(@Header("Authorization") String token,
                                       @Path("id") String id,
                                       @HeaderMap Map<String, String> headers,
                                       @Body RequestBody connectPaymentConfirmationRequest);


    @POST(ApiEndPoints.sendSessionOtp)
    Call<ResponseBody> sendSessionOtp(@Header("Authorization") String token);

    @POST(ApiEndPoints.validateSessionOtp)
    Call<ResponseBody> validateSessionOtp(@Header("Authorization") String token,
            @Body Map<String, String> body);

    @POST(ApiEndPoints.connectTokenURL)
    Call<ResponseBody> connectToken(@HeaderMap Map<String, String> headers, @Body RequestBody connectTokenRequest);

    @POST(ApiEndPoints.connectHeartbeatURL)
    Call<ResponseBody> connectHeartbeat(@Header("Authorization") String token,
                                        @HeaderMap Map<String, String> headers,
                                        @Body RequestBody connectTokenRequest);

}

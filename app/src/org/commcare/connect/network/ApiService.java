package org.commcare.connect.network;

import java.util.Map;

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

    @POST(ApiEndPoints.sendSessionOtp)
    Call<ResponseBody> sendSessionOtp(@Header("Authorization") String token);

    @POST(ApiEndPoints.validateSessionOtp)
    Call<ResponseBody> validateSessionOtp(@Header("Authorization") String token,
            @Body Map<String, String> body);
}

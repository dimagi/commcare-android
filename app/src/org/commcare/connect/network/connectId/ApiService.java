package org.commcare.connect.network.connectId;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {


    @POST(ApiEndPoints.startConfiguration)
    Call<ResponseBody> startConfiguration(@Header("CC-Integrity-Token") String integrityToken,
            @Header("CC-Request-Hash") String requestHash,
            @Body Map<String, String> registrationRequest);

    @POST(ApiEndPoints.checkName)
    Call<ResponseBody> checkName(@Body Map<String, String> nameRequest);

    @POST(ApiEndPoints.changePhoneNo)
    Call<ResponseBody> changePhoneNo(@Header("Authorization") String token,
                                     @Body Map<String, String> changeRequest);

    @POST(ApiEndPoints.updateProfile)
    Call<ResponseBody> updateProfile(@Header("Authorization") String token,
                                     @Body Map<String, String> updateProfile);

    @POST(ApiEndPoints.completeProfile)
    Call<ResponseBody> completeProfile(@Header("Authorization") String token,
                                       @Body Map<String, String> body);

    @POST(ApiEndPoints.validatePhone)
    Call<ResponseBody> validatePhone(@Header("Authorization") String token, @Body Map<String, String> requestOTP);

    @POST(ApiEndPoints.recoverOTPPrimary)
    Call<ResponseBody> requestOTPPrimary(@Body Map<String, String> requestOTP);

    @POST(ApiEndPoints.accountDeactivation)
    Call<ResponseBody> accountDeactivation(@Body Map<String, String> accountDeactivationRequest);

    @POST(ApiEndPoints.confirmDeactivation)
    Call<ResponseBody> confirmDeactivation(@Body Map<String, String> confirmDeactivationRequest);

    @POST(ApiEndPoints.recoverConfirmOTP)
    Call<ResponseBody> recoverConfirmOTP(@Body Map<String, String> confirmOTPRequest);

    @POST(ApiEndPoints.confirmOTP)
    Call<ResponseBody> confirmOTP(@Header("Authorization") String token,
                                  @Body Map<String, String> confirmOTPRequest);

    @POST(ApiEndPoints.confirmPIN)
    Call<ResponseBody> confirmPin(@Header("Authorization") String token,
                                  @Body Map<String, String> confirmBackupCodeRequest);

    @POST(ApiEndPoints.setBackupCode)
    Call<ResponseBody> setBackupCode(@Header("Authorization") String token,
                                     @Body Map<String, String> changeBackupCodeRequest);

    @POST(ApiEndPoints.resetPassword)
    Call<ResponseBody> resetPassword(@Body Map<String, String> resetPasswordRequest);

}

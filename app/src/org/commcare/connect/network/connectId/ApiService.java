package org.commcare.connect.network.connectId;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {


    @POST(ApiEndPoints.registerUser)
    Call<ResponseBody> registerUser(@Body Map<String, String> registrationRequest);

    @POST(ApiEndPoints.changePhoneNo)
    Call<ResponseBody> changePhoneNo(@Header("Authorization") String token,
                                     @Body Map<String, String> changeRequest);

    @POST(ApiEndPoints.updateProfile)
    Call<ResponseBody> updateProfile(@Header("Authorization") String token,
                                     @Body Map<String, String> updateProfile);

    @POST(ApiEndPoints.setProfile)
    Call<ResponseBody> setProfile(@Header("Authorization") String token,
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
    Call<ResponseBody> confirmPIN(@Body Map<String, String> confirmPINRequest);

    @POST(ApiEndPoints.setPIN)
    Call<ResponseBody> changePIN(@Header("Authorization") String token,
                                 @Body Map<String, String> changePINRequest);

    @POST(ApiEndPoints.resetPassword)
    Call<ResponseBody> resetPassword(@Body Map<String, String> resetPasswordRequest);

}

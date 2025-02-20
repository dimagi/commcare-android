package org.commcare.connect.network.connectId;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    @GET(ApiEndPoints.phoneAvailable)
    Call<ResponseBody> checkPhoneNumber(@Query("phone_number") String phoneNumber);

    @POST(ApiEndPoints.registerUser)
    Call<ResponseBody> registerUser(@Body Map<String, Object> registrationRequest);

    @POST(ApiEndPoints.changePhoneNo)
    Call<ResponseBody> changePhoneNo(@Header("Authorization") String token, @Body Map<String, Object> changeRequest);

    @POST(ApiEndPoints.updateProfile)
    Call<ResponseBody> updateProfile(@Header("Authorization") String token, @Body Map<String, Object> updateProfile);

    @POST(ApiEndPoints.validatePhone)
    Call<ResponseBody> validatePhone(@Header("Authorization") String token, @Body Map<String, Object> requestOTP);

    @POST(ApiEndPoints.recoverOTPPrimary)
    Call<ResponseBody> requestOTPPrimary(@Body Map<String, Object> requestOTP);

    @POST(ApiEndPoints.recoverOTPSecondary)
    Call<ResponseBody> validateSecondaryPhone(@Header("Authorization") String token, @Body Map<String, Object> validateSecondaryPhoneRequest);

    @POST(ApiEndPoints.recoverConfirmOTPSecondary)
    Call<ResponseBody> recoverConfirmOTPSecondary(@Body Map<String, Object> recoverConfirmOTPSecondaryRequest);

    @POST(ApiEndPoints.confirmOTPSecondary)
    Call<ResponseBody> confirmOTPSecondary(@Header("Authorization") String token, @Body Map<String, Object> confirmOTPSecondaryRequest);

    @POST(ApiEndPoints.accountDeactivation)
    Call<ResponseBody> accountDeactivation(@Body Map<String, Object> accountDeactivationRequest);

    @POST(ApiEndPoints.confirmDeactivation)
    Call<ResponseBody> confirmDeactivation(@Body Map<String, Object> confirmDeactivationRequest);

    @POST(ApiEndPoints.recoverConfirmOTP)
    Call<ResponseBody> recoverConfirmOTP(@Body Map<String, Object> confirmOTPRequest);

    @POST(ApiEndPoints.confirmOTP)
    Call<ResponseBody> confirmOTP(@Header("Authorization") String token, @Body Map<String, Object> confirmOTPRequest);

    @POST(ApiEndPoints.recoverSecondary)
    Call<ResponseBody> recoverSecondary(@Body Map<String, Object> recoverSecondaryRequest);

    @POST(ApiEndPoints.confirmPIN)
    Call<ResponseBody> confirmPIN(@Body Map<String, Object> confirmPINRequest);

    @POST(ApiEndPoints.setPIN)
    Call<ResponseBody> changePIN(@Header("Authorization") String token, @Body Map<String, Object> changePINRequest);

    @POST(ApiEndPoints.resetPassword)
    Call<ResponseBody> resetPassword(@Body Map<String, Object> resetPasswordRequest);

    @POST(ApiEndPoints.changePassword)
    Call<ResponseBody> changePassword(@Header("Authorization") String token, @Body Map<String, Object> changePasswordRequest);

    @POST(ApiEndPoints.confirmPassword)
    Call<ResponseBody> checkPassword(@Body Map<String, Object> confirmPasswordRequest);
}
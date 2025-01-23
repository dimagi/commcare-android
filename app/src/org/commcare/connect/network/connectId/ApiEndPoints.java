package org.commcare.connect.network.connectId;

public class ApiEndPoints {
    public static final String connectTokenURL = "o/token/";
    public static final String connectHeartbeatURL = "/users/heartbeat";
    public static final String connectFetchDbKeyURL = "/users/fetch_db_key";
    public static final String connectChangePasswordURL = "/users/change_password";
    public static final String registerUser = "/users/register";
    public static final String phoneAvailable = "/users/phone_available";
    public static final String changePhoneNo = "/users/change_phone";
    public static final String updateProfile = "/users/update_profile";
    public static final String validatePhone = "/users/validate_phone";
    public static final String recoverOTPPrimary = "/users/recover";
    public static final String recoverOTPSecondary = "/users/validate_secondary_phone";
    public static final String recoverConfirmOTPSecondary = "/users/recover/confirm_secondary_otp";
    public static final String confirmOTPSecondary = "/users/confirm_secondary_otp";
    public static final String accountDeactivation = "/users/recover/initiate_deactivation";
    public static final String confirmDeactivation = "/users/recover/confirm_deactivation";
    public static final String recoverConfirmOTP = "/users/recover/confirm_otp";
    public static final String recoverSecondary = "/users/recover/secondary";
    public static final String confirmOTP = "/users/confirm_otp";
    public static final String setPIN = "/users/set_recovery_pin";
    public static final String confirmPIN = "/users/recover/confirm_pin";
    public static final String resetPassword = "/users/recover/reset_password";
    public static final String changePassword = "/users/change_password";
    public static final String confirmPassword = "/users/recover/confirm_password";

}
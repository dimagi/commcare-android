package org.commcare.connect.network.connectId;

public class ApiEndPoints {
    public static final String connectTokenURL = "/o/token/";
    public static final String connectHeartbeatURL = "/users/heartbeat";
    public static final String connectFetchDbKeyURL = "/users/fetch_db_key";
    public static final String validateFirebaseIdToken = "/users/validate_firebase_id_token";
    public static final String checkName = "/users/check_name";
    public static final String startConfiguration = "/users/start_configuration";
    public static final String phoneAvailable = "/users/phone_available";
    public static final String changePhoneNo = "/users/change_phone";
    public static final String updateProfile = "/users/update_profile";
    public static final String completeProfile = "/users/complete_profile";
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
    public static final String setBackupCode = "/users/set_recovery_pin";
    public static final String confirmBackupCode = "/users/recover/confirm_backup_code";
    public static final String resetPassword = "/users/recover/reset_password";
    public static final String changePassword = "/users/change_password";
    public static final String confirmPassword = "/users/recover/confirm_password";
    public static final String connectOpportunitiesURL = "https://%s/api/opportunity/";
    public static final String connectStartLearningURL = "https://%s/users/start_learn_app/";
    public static final String connectLearnProgressURL = "https://%s/api/opportunity/%d/learn_progress";
    public static final String connectClaimJobURL = "https://%s/api/opportunity/%d/claim";
    public static final String connectDeliveriesURL = "https://%s/api/opportunity/%d/delivery_progress";
    public static final String connectPaymentConfirmationURL = "https://%s/api/payment/%s/confirm";
    public static final String connectInitiateUserAccountDeactivationURL = "https://connectid.dimagi.com/users/recover/initiate_deactivation";
    public static final String connectConfirmUserAccountDeactivationURL = "https://connectid.dimagi.com/users/recover/confirm_deactivation";
}

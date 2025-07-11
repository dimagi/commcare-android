package org.commcare.connect.network;

public class ApiEndPoints {
    public static final String connectTokenURL = "/o/token/";
    public static final String connectHeartbeatURL = "/users/heartbeat";
    public static final String connectFetchDbKeyURL = "/users/fetch_db_key";
    public static final String validateFirebaseIdToken = "/users/validate_firebase_id_token";
    public static final String checkName = "/users/check_name";
    public static final String startConfiguration = "/users/start_configuration";
    public static final String sendSessionOtp = "/users/send_session_otp";
    public static final String validateSessionOtp = "/users/confirm_session_otp";
    public static final String updateProfile = "/users/update_profile";
    public static final String completeProfile = "/users/complete_profile";
    public static final String confirmBackupCode = "/users/recover/confirm_backup_code";
    public static final String connectOpportunitiesURL = "/api/opportunity/";
    public static final String connectStartLearningURL = "/users/start_learn_app/";
    public static final String connectLearnProgressURL = "/api/opportunity/{id}/learn_progress";
    public static final String connectClaimJobURL = "/api/opportunity/{id}/claim";
    public static final String connectDeliveriesURL = "/api/opportunity/{id}/delivery_progress";
    public static final String connectPaymentConfirmationURL = "/api/payment/{id}/confirm";
    public static final String CREDENTIALS = "/users/credentials";
}

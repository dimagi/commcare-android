package org.commcare.connect.network.connectId;

public class ApiEndPoints {
    public static final String connectTokenURL = "/o/token/";
    public static final String connectHeartbeatURL = "/users/heartbeat";
    public static final String connectFetchDbKeyURL = "/users/fetch_db_key";
    public static final String validateFirebaseIdToken = "/users/validate_firebase_id_token";
    public static final String checkName = "/users/check_name";
    public static final String startConfiguration = "/users/start_configuration";
    public static final String updateProfile = "/users/update_profile";
    public static final String completeProfile = "/users/complete_profile";
    public static final String confirmBackupCode = "/users/recover/confirm_backup_code";
    public static final String connectOpportunitiesURL = "https://%s/api/opportunity/";
    public static final String connectStartLearningURL = "https://%s/users/start_learn_app/";
    public static final String connectLearnProgressURL = "https://%s/api/opportunity/%d/learn_progress";
    public static final String connectClaimJobURL = "https://%s/api/opportunity/%d/claim";
    public static final String connectDeliveriesURL = "https://%s/api/opportunity/%d/delivery_progress";
    public static final String connectPaymentConfirmationURL = "https://%s/api/payment/%s/confirm";
}

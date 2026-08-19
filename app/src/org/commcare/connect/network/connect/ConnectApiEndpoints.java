package org.commcare.connect.network.connect;

public class ConnectApiEndpoints {
    public static final String connectOpportunitiesURL = "/api/opportunity/";
    public static final String connectStartLearningURL = "/users/start_learn_app/";
    public static final String connectLearnProgressURL = "/api/opportunity/{id}/learn_progress";
    public static final String connectClaimJobURL = "/api/opportunity/{id}/claim";
    public static final String connectDeliveriesURL = "/api/opportunity/{id}/delivery_progress";
    public static final String PAYMENT_CONFIRMAITONS = "/api/payment/confirm";
}

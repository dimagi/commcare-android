package org.commcare.activities.connect;

public enum ConnectIDTask {
    CONNECT_NO_ACTIVITY(ConnectIDConstants.ConnectIDTaskIDOffset, null),
    CONNECT_REGISTER_OR_RECOVER_DECISION(ConnectIDConstants.ConnectIDTaskIDOffset + 1, ConnectIDRecoveryDecisionActivity.class),
    CONNECT_REGISTRATION_PRIMARY_PHONE(ConnectIDConstants.ConnectIDTaskIDOffset + 2, ConnectIDPhoneActivity.class),
    CONNECT_REGISTRATION_CONSENT(ConnectIDConstants.ConnectIDTaskIDOffset + 3, ConnectIDConsentActivity.class),
    CONNECT_REGISTRATION_MAIN(ConnectIDConstants.ConnectIDTaskIDOffset + 4, ConnectIDRegistrationActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS(ConnectIDConstants.ConnectIDTaskIDOffset + 5, ConnectIDVerificationActivity.class),
    CONNECT_REGISTRATION_UNLOCK_BIOMETRIC(ConnectIDConstants.ConnectIDTaskIDOffset + 6, ConnectIDLoginActivity.class),
    CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE(ConnectIDConstants.ConnectIDTaskIDOffset + 7, ConnectIDPhoneVerificationActivity.class),
    CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE(ConnectIDConstants.ConnectIDTaskIDOffset + 8, ConnectIDPhoneActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_PASSWORD(ConnectIDConstants.ConnectIDTaskIDOffset + 9, ConnectIDPasswordActivity.class),
    CONNECT_REGISTRATION_ALTERNATE_PHONE(ConnectIDConstants.ConnectIDTaskIDOffset + 10, ConnectIDPhoneActivity.class),
    CONNECT_REGISTRATION_SUCCESS(ConnectIDConstants.ConnectIDTaskIDOffset + 11, ConnectIDMessageActivity.class),
    CONNECT_RECOVERY_PRIMARY_PHONE(ConnectIDConstants.ConnectIDTaskIDOffset + 12, ConnectIDPhoneActivity.class),
    CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE(ConnectIDConstants.ConnectIDTaskIDOffset + 13, ConnectIDPhoneVerificationActivity.class),
    CONNECT_RECOVERY_VERIFY_PASSWORD(ConnectIDConstants.ConnectIDTaskIDOffset + 14, ConnectIDPasswordVerificationActivity.class),
    CONNECT_RECOVERY_ALT_PHONE_MESSAGE(ConnectIDConstants.ConnectIDTaskIDOffset + 15, ConnectIDMessageActivity.class),
    CONNECT_RECOVERY_VERIFY_ALT_PHONE(ConnectIDConstants.ConnectIDTaskIDOffset + 16, ConnectIDPhoneVerificationActivity.class),
    CONNECT_RECOVERY_CHANGE_PASSWORD(ConnectIDConstants.ConnectIDTaskIDOffset + 17, ConnectIDPasswordActivity.class),
    CONNECT_RECOVERY_SUCCESS(ConnectIDConstants.ConnectIDTaskIDOffset + 18, ConnectIDMessageActivity.class),
    CONNECT_UNLOCK_BIOMETRIC(ConnectIDConstants.ConnectIDTaskIDOffset + 19, ConnectIDLoginActivity.class),
    CONNECT_UNLOCK_PASSWORD(ConnectIDConstants.ConnectIDTaskIDOffset + 20, ConnectIDPasswordVerificationActivity.class),
    CONNECT_UNLOCK_PIN(ConnectIDConstants.ConnectIDTaskIDOffset + 21, null);

    private final int requestCode;
    private final Class<?> nextActivity;
    ConnectIDTask(int requestCode, Class<?> nextActivity) {
        this.requestCode = requestCode;
        this.nextActivity = nextActivity;
    }

    public int getRequestCode() { return requestCode; }

    public Class<?> getNextActivity() { return nextActivity; }

    public static ConnectIDTask fromRequestCode(int code) {
        for(ConnectIDTask task : ConnectIDTask.values()) {
            if(task.requestCode == code) {
                return task;
            }
        }

        return ConnectIDTask.CONNECT_NO_ACTIVITY;
    }
}

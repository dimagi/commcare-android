package org.commcare.activities.connect;

/**
 * Enum representing the tasks (and associated activities) involved in various ConnectID workflows
 *
 * @author dviggiano
 */
public enum ConnectIdTask {
    CONNECT_NO_ACTIVITY(ConnectIdConstants.ConnectIDTaskIDOffset,
            null),
    CONNECT_REGISTER_OR_RECOVER_DECISION(ConnectIdConstants.ConnectIDTaskIDOffset + 1,
            ConnectIdRecoveryDecisionActivity.class),
    CONNECT_REGISTRATION_PRIMARY_PHONE(ConnectIdConstants.ConnectIDTaskIDOffset + 2,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_CONSENT(ConnectIdConstants.ConnectIDTaskIDOffset + 3,
            ConnectIdConsentActivity.class),
    CONNECT_REGISTRATION_MAIN(ConnectIdConstants.ConnectIDTaskIDOffset + 4,
            ConnectIdRegistrationActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS(ConnectIdConstants.ConnectIDTaskIDOffset + 5,
            ConnectIdVerificationActivity.class),
    CONNECT_REGISTRATION_UNLOCK_BIOMETRIC(ConnectIdConstants.ConnectIDTaskIDOffset + 6,
            ConnectIdLoginActivity.class),
    CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE(ConnectIdConstants.ConnectIDTaskIDOffset + 7,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE(ConnectIdConstants.ConnectIDTaskIDOffset + 8,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_PASSWORD(ConnectIdConstants.ConnectIDTaskIDOffset + 9,
            ConnectIdPasswordActivity.class),
    CONNECT_REGISTRATION_ALTERNATE_PHONE(ConnectIdConstants.ConnectIDTaskIDOffset + 10,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_SUCCESS(ConnectIdConstants.ConnectIDTaskIDOffset + 11,
            ConnectIdMessageActivity.class),
    CONNECT_RECOVERY_PRIMARY_PHONE(ConnectIdConstants.ConnectIDTaskIDOffset + 12,
            ConnectIdPhoneActivity.class),
    CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE(ConnectIdConstants.ConnectIDTaskIDOffset + 13,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_RECOVERY_VERIFY_PASSWORD(ConnectIdConstants.ConnectIDTaskIDOffset + 14,
            ConnectIdPasswordVerificationActivity.class),
    CONNECT_RECOVERY_ALT_PHONE_MESSAGE(ConnectIdConstants.ConnectIDTaskIDOffset + 15,
            ConnectIdMessageActivity.class),
    CONNECT_RECOVERY_VERIFY_ALT_PHONE(ConnectIdConstants.ConnectIDTaskIDOffset + 16,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_RECOVERY_CHANGE_PASSWORD(ConnectIdConstants.ConnectIDTaskIDOffset + 17,
            ConnectIdPasswordActivity.class),
    CONNECT_RECOVERY_SUCCESS(ConnectIdConstants.ConnectIDTaskIDOffset + 18,
            ConnectIdMessageActivity.class),
    CONNECT_UNLOCK_BIOMETRIC(ConnectIdConstants.ConnectIDTaskIDOffset + 19,
            ConnectIdLoginActivity.class),
    CONNECT_UNLOCK_PASSWORD(ConnectIdConstants.ConnectIDTaskIDOffset + 20,
            ConnectIdPasswordVerificationActivity.class),
    CONNECT_UNLOCK_PIN(ConnectIdConstants.ConnectIDTaskIDOffset + 21,
            null);

    private final int requestCode;
    private final Class<?> nextActivity;

    ConnectIdTask(int requestCode, Class<?> nextActivity) {
        this.requestCode = requestCode;
        this.nextActivity = nextActivity;
    }

    public int getRequestCode() {
        return requestCode;
    }

    public Class<?> getNextActivity() {
        return nextActivity;
    }

    public static ConnectIdTask fromRequestCode(int code) {
        for (ConnectIdTask task : ConnectIdTask.values()) {
            if (task.requestCode == code) {
                return task;
            }
        }

        return ConnectIdTask.CONNECT_NO_ACTIVITY;
    }
}

package org.commcare.activities.connect;

/**
 * Enum representing the tasks (and associated activities) involved in various ConnectID workflows
 *
 * @author dviggiano
 */
public enum ConnectIdTask {
    CONNECT_NO_ACTIVITY(ConnectIdConstants.ConnectIdTaskIdOffset,
            null),
    CONNECT_REGISTER_OR_RECOVER_DECISION(ConnectIdConstants.ConnectIdTaskIdOffset + 1,
            ConnectIdRecoveryDecisionActivity.class),
    CONNECT_REGISTRATION_PRIMARY_PHONE(ConnectIdConstants.ConnectIdTaskIdOffset + 2,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_CONSENT(ConnectIdConstants.ConnectIdTaskIdOffset + 3,
            ConnectIdConsentActivity.class),
    CONNECT_REGISTRATION_MAIN(ConnectIdConstants.ConnectIdTaskIdOffset + 4,
            ConnectIdRegistrationActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS(ConnectIdConstants.ConnectIdTaskIdOffset + 5,
            ConnectIdVerificationActivity.class),
    CONNECT_REGISTRATION_UNLOCK_BIOMETRIC(ConnectIdConstants.ConnectIdTaskIdOffset + 6,
            ConnectIdLoginActivity.class),
    CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE(ConnectIdConstants.ConnectIdTaskIdOffset + 7,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE(ConnectIdConstants.ConnectIdTaskIdOffset + 8,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_PASSWORD(ConnectIdConstants.ConnectIdTaskIdOffset + 9,
            ConnectIdPasswordActivity.class),
    CONNECT_REGISTRATION_ALTERNATE_PHONE(ConnectIdConstants.ConnectIdTaskIdOffset + 10,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_SUCCESS(ConnectIdConstants.ConnectIdTaskIdOffset + 11,
            ConnectIdMessageActivity.class),
    CONNECT_RECOVERY_PRIMARY_PHONE(ConnectIdConstants.ConnectIdTaskIdOffset + 12,
            ConnectIdPhoneActivity.class),
    CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE(ConnectIdConstants.ConnectIdTaskIdOffset + 13,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_RECOVERY_VERIFY_PASSWORD(ConnectIdConstants.ConnectIdTaskIdOffset + 14,
            ConnectIdPasswordVerificationActivity.class),
    CONNECT_RECOVERY_ALT_PHONE_MESSAGE(ConnectIdConstants.ConnectIdTaskIdOffset + 15,
            ConnectIdMessageActivity.class),
    CONNECT_RECOVERY_VERIFY_ALT_PHONE(ConnectIdConstants.ConnectIdTaskIdOffset + 16,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_RECOVERY_CHANGE_PASSWORD(ConnectIdConstants.ConnectIdTaskIdOffset + 17,
            ConnectIdPasswordActivity.class),
    CONNECT_RECOVERY_SUCCESS(ConnectIdConstants.ConnectIdTaskIdOffset + 18,
            ConnectIdMessageActivity.class),
    CONNECT_UNLOCK_BIOMETRIC(ConnectIdConstants.ConnectIdTaskIdOffset + 19,
            ConnectIdLoginActivity.class),
    CONNECT_UNLOCK_PASSWORD(ConnectIdConstants.ConnectIdTaskIdOffset + 20,
            ConnectIdPasswordVerificationActivity.class),
    CONNECT_UNLOCK_PIN(ConnectIdConstants.ConnectIdTaskIdOffset + 21,
            null),
    CONNECT_BIOMETRIC_ENROLL_FAIL(ConnectIdConstants.ConnectIdTaskIdOffset + 22,
                             ConnectIdMessageActivity.class);

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

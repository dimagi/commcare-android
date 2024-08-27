package org.commcare.connect;

import org.commcare.activities.connect.ConnectIdConsentActivity;
import org.commcare.activities.connect.ConnectIdBiometricUnlockActivity;
import org.commcare.activities.connect.ConnectIdMessageActivity;
import org.commcare.activities.connect.ConnectIdPasswordVerificationActivity;
import org.commcare.activities.connect.ConnectIdPhoneActivity;
import org.commcare.activities.connect.ConnectIdPhoneVerificationActivity;
import org.commcare.activities.connect.ConnectIdPinActivity;
import org.commcare.activities.connect.ConnectIdRecoveryDecisionActivity;
import org.commcare.activities.connect.ConnectIdRegistrationActivity;
import org.commcare.activities.connect.ConnectIdBiometricConfigActivity;

/**
 * Enum representing the tasks (and associated activities) involved in various ConnectId workflows
 *
 * @author dviggiano
 */
public enum ConnectTask {
    CONNECT_NO_ACTIVITY(ConnectConstants.ConnectIdTaskIdOffset,
            null),
    CONNECT_REGISTER_OR_RECOVER_DECISION(ConnectConstants.ConnectIdTaskIdOffset + 1,
            ConnectIdRecoveryDecisionActivity.class),
    CONNECT_REGISTRATION_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 2,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_CONSENT(ConnectConstants.ConnectIdTaskIdOffset + 3,
            ConnectIdConsentActivity.class),
    CONNECT_REGISTRATION_MAIN(ConnectConstants.ConnectIdTaskIdOffset + 4,
            ConnectIdRegistrationActivity.class),
    CONNECT_REGISTRATION_CONFIGURE_BIOMETRICS(ConnectConstants.ConnectIdTaskIdOffset + 5,
            ConnectIdBiometricConfigActivity.class),
    CONNECT_REGISTRATION_UNLOCK_BIOMETRIC(ConnectConstants.ConnectIdTaskIdOffset + 6,
            ConnectIdBiometricUnlockActivity.class),
    CONNECT_REGISTRATION_VERIFY_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 7,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_REGISTRATION_CHANGE_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 8,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_ALTERNATE_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 10,
            ConnectIdPhoneActivity.class),
    CONNECT_REGISTRATION_SUCCESS(ConnectConstants.ConnectIdTaskIdOffset + 11,
            ConnectIdMessageActivity.class),
    CONNECT_RECOVERY_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 12,
            ConnectIdPhoneActivity.class),
    CONNECT_RECOVERY_VERIFY_PRIMARY_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 13,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_RECOVERY_VERIFY_PASSWORD(ConnectConstants.ConnectIdTaskIdOffset + 14,
            ConnectIdPasswordVerificationActivity.class),
    CONNECT_RECOVERY_ALT_PHONE_MESSAGE(ConnectConstants.ConnectIdTaskIdOffset + 15,
            ConnectIdMessageActivity.class),
    CONNECT_RECOVERY_VERIFY_ALT_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 16,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_RECOVERY_SUCCESS(ConnectConstants.ConnectIdTaskIdOffset + 18,
            ConnectIdMessageActivity.class),
    CONNECT_UNLOCK_BIOMETRIC(ConnectConstants.ConnectIdTaskIdOffset + 19,
            ConnectIdBiometricUnlockActivity.class),
    CONNECT_UNLOCK_PASSWORD(ConnectConstants.ConnectIdTaskIdOffset + 20,
            ConnectIdPasswordVerificationActivity.class),
    CONNECT_UNLOCK_PIN(ConnectConstants.ConnectIdTaskIdOffset + 21,
            ConnectIdPinActivity.class),
    CONNECT_BIOMETRIC_ENROLL_FAIL(ConnectConstants.ConnectIdTaskIdOffset + 22,
            ConnectIdMessageActivity.class),
    CONNECT_MAIN(ConnectConstants.ConnectIdTaskIdOffset + 23,
            null), //NOTE: Will be ConnectActivity.class when ready
    CONNECT_REGISTRATION_CONFIGURE_PIN(ConnectConstants.ConnectIdTaskIdOffset + 24,
            ConnectIdPinActivity.class),
    CONNECT_REGISTRATION_CONFIRM_PIN(ConnectConstants.ConnectIdTaskIdOffset + 25,
            ConnectIdPinActivity.class),
    CONNECT_RECOVERY_VERIFY_PIN(ConnectConstants.ConnectIdTaskIdOffset + 26,
            ConnectIdPinActivity.class),
    CONNECT_RECOVERY_CHANGE_PIN(ConnectConstants.ConnectIdTaskIdOffset + 27,
            ConnectIdPinActivity.class),
    CONNECT_RECOVERY_CONFIGURE_BIOMETRICS(ConnectConstants.ConnectIdTaskIdOffset + 28,
            ConnectIdBiometricConfigActivity.class),
    CONNECT_RECOVERY_UNLOCK_BIOMETRIC(ConnectConstants.ConnectIdTaskIdOffset + 29,
            ConnectIdBiometricUnlockActivity.class),
    CONNECT_VERIFY_ALT_PHONE_MESSAGE(ConnectConstants.ConnectIdTaskIdOffset + 30,
            ConnectIdMessageActivity.class),
    CONNECT_VERIFY_ALT_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 31,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_UNLOCK_ALT_PHONE_MESSAGE(ConnectConstants.ConnectIdTaskIdOffset + 32,
            ConnectIdMessageActivity.class),
    CONNECT_UNLOCK_VERIFY_ALT_PHONE(ConnectConstants.ConnectIdTaskIdOffset + 33,
            ConnectIdPhoneVerificationActivity.class),
    CONNECT_JOB_INFO(ConnectConstants.ConnectIdTaskIdOffset + 34,
            null), //NOTE: Will be ConnectActivity.class when ready
    CONNECT_REGISTRATION_CHANGE_PIN(ConnectConstants.ConnectIdTaskIdOffset + 35,
            ConnectIdPinActivity.class),
    CONNECT_UNLOCK_ALT_PHONE_CHANGE(ConnectConstants.ConnectIdTaskIdOffset + 36,
            ConnectIdPhoneActivity.class),
    CONNECT_VERIFY_ALT_PHONE_CHANGE(ConnectConstants.ConnectIdTaskIdOffset + 37,
            ConnectIdPhoneActivity.class),

    CONNECT_RECOVERY_WRONG_PIN(ConnectConstants.ConnectIdTaskIdOffset + 38,
            ConnectIdMessageActivity.class),
    CONNECT_REGISTRATION_WRONG_PIN(ConnectConstants.ConnectIdTaskIdOffset + 39,
            ConnectIdMessageActivity.class),
    ;

    private final int requestCode;
    private final Class<?> nextActivity;

    ConnectTask(int requestCode, Class<?> nextActivity) {
        this.requestCode = requestCode;
        this.nextActivity = nextActivity;
    }

    public int getRequestCode() {
        return requestCode;
    }

    public Class<?> getNextActivity() {
        return nextActivity;
    }

    public static ConnectTask fromRequestCode(int code) {
        for (ConnectTask task : ConnectTask.values()) {
            if (task.requestCode == code) {
                return task;
            }
        }

        return ConnectTask.CONNECT_NO_ACTIVITY;
    }

    public static boolean isConnectTaskCode(int code) {
        for (ConnectTask task : ConnectTask.values()) {
            if (task.requestCode == code) {
                return true;
            }
        }

        return false;
    }
}
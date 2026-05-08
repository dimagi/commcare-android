package org.commcare.utils;

public enum OtpErrorType {
    INVALID_CREDENTIAL,
    TOO_MANY_REQUESTS,
    MISSING_ACTIVITY,
    GENERIC_ERROR,
    VERIFICATION_FAILED;

    public boolean isNonRecoverable() {
        return this == OtpErrorType.VERIFICATION_FAILED;
    }
}

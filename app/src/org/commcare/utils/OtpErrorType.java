package org.commcare.utils;

public enum OtpErrorType {
    INVALID_CREDENTIAL,
    TOO_MANY_REQUESTS,
    MISSING_ACTIVITY,
    GENERIC_ERROR,
    VERIFICATION_FAILED;

    /**
     * INVALID_CREDENTIAL is the only error the user can act on themselves; it means the phone
     * number or the code they supplied was malformed, missing or expired. Everything else
     * indicates Firebase cannot deliver an OTP at all, so we treat unrecognised errors as
     * non-recoverable and let the caller fall back to PersonalID SMS.
     */
    public boolean isNonRecoverable() {
        return this != OtpErrorType.INVALID_CREDENTIAL;
    }
}

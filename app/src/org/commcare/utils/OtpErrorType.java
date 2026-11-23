package org.commcare.utils;

public enum OtpErrorType {
    INVALID_CREDENTIAL,
    SESSION_EXPIRED,
    TOO_MANY_REQUESTS,
    MISSING_ACTIVITY,
    GENERIC_ERROR,
    VERIFICATION_FAILED,
    REGION_BLOCKED;

    /**
     * INVALID_CREDENTIAL and SESSION_EXPIRED are the only errors the user can act on themselves:
     * the phone number or code they supplied was malformed or missing, or the code is no longer
     * valid and a new one must be requested. Everything else indicates Firebase cannot deliver an
     * OTP at all, so we treat unrecognized errors as non-recoverable and let the caller fall back
     * to PersonalID SMS. REGION_BLOCKED is excluded too: the number's country is blocked for SMS,
     * so PersonalID SMS must not be sent there either.
     */
    public boolean isNonRecoverable() {
        return this != OtpErrorType.INVALID_CREDENTIAL && this != OtpErrorType.SESSION_EXPIRED
                && this != OtpErrorType.REGION_BLOCKED;
    }
}

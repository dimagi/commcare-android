package org.commcare.fragments.personalId

/**
 * Counters for a email-OTP collection session
 *
 *  - [requestCount]: total email OTP send/request calls this session.
 *  - [failedAttempts]: absolute number of failed email OTP verifications this session.
 */
class EmailOtpAttemptTracker(
    initialRequestCount: Int = 0,
    initialFailedAttempts: Int = 0,
) {
    var requestCount: Int = initialRequestCount
        private set
    var failedAttempts: Int = initialFailedAttempts
        private set

    /** Records one email OTP send/request, regardless of outcome. */
    fun recordRequest() {
        requestCount++
    }

    /** Records one failed email OTP verification. */
    fun recordFailedVerification() {
        failedAttempts++
    }

    fun reset() {
        requestCount = 0
        failedAttempts = 0
    }
}

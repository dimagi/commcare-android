package org.commcare.fragments.personalId

/**
 * Counters for an OTP collection session
 *
 *  - [requestCount]: total OTP send/request calls this session.
 *  - [failedAttempts]: absolute number of failed OTP verifications this session.
 */
class AttemptTracker(
    initialRequestCount: Int = 0,
    initialFailedAttempts: Int = 0,
) {
    var requestCount: Int = initialRequestCount
        private set
    var failedAttempts: Int = initialFailedAttempts
        private set

    /** Records one OTP send/request, regardless of outcome. */
    fun recordRequest() {
        requestCount++
    }

    /** Records one failed OTP verification. */
    fun recordFailedVerification() {
        failedAttempts++
    }

    fun reset() {
        requestCount = 0
        failedAttempts = 0
    }
}

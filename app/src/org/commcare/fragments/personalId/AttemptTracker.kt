package org.commcare.fragments.personalId

/**
 * Counters for request/failure attempts during a session
 *
 *  - [requestCount]: total request calls made this session.
 *  - [failedAttempts]: number of failed attempts this session.
 */
class AttemptTracker(
    initialRequestCount: Int = 0,
    initialFailedAttempts: Int = 0,
) {
    var requestCount: Int = initialRequestCount
        private set
    var failedAttempts: Int = initialFailedAttempts
        private set

    /** Records one request, regardless of outcome. */
    fun recordRequest() {
        requestCount++
    }

    /** Records one failed attempt. */
    fun recordFailedAttempt() {
        failedAttempts++
    }

    /** Resets both counters to zero. */
    fun reset() {
        requestCount = 0
        failedAttempts = 0
    }
}

package org.commcare.fragments.personalId

/**
 * Request / failed-verification counters an OTP screen reports to analytics and uses to decide
 * when to fall back to PersonalID SMS.
 */
interface AttemptCounter {
    val requestCount: Int
    val failedAttempts: Int

    fun recordRequest()

    fun recordFailedAttempt()
}

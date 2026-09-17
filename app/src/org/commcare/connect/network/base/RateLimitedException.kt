package org.commcare.connect.network.base

/**
 * Carries the server's `retry_after_seconds` alongside a
 * [BaseApiHandler.PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR] so screens can tell
 * the user how long the wait actually is and hold their resend affordance for that long.
 *
 * The wait is not a fixed cooldown: it grows with each resend, and jumps from minutes to hours once
 * the server has thrown a code away after too many wrong guesses.
 *
 * [retryAfterSeconds] is null when the server rate limited us without saying for how long, which
 * leaves callers with nothing better than a generic "try again later".
 */
class RateLimitedException(
    val retryAfterSeconds: Int?,
) : Exception(
        retryAfterSeconds
            ?.let { "Rate limited. Retry after $it seconds." }
            ?: "Rate limited, with no retry time given.",
    )

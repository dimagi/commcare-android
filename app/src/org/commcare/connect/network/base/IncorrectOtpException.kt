package org.commcare.connect.network.base

/**
 * Carries the server's `attempts_left` alongside a
 * [BaseApiHandler.PersonalIdOrConnectApiErrorCodes.INCORRECT_OTP_ERROR] so screens can tell the user
 * how many guesses the current code has left.
 *
 * [attemptsLeft] is null when the endpoint does not report a count.
 */
class IncorrectOtpException(
    val attemptsLeft: Int?,
) : Exception(
        attemptsLeft
            ?.let { "Incorrect OTP. $it attempts left." }
            ?: "Incorrect OTP, with no attempt count given.",
    )

package org.commcare.connect

import org.commcare.login.LoginError

/** A terminal Connect launch failure, for the launching screen to react to. */
sealed class AppLaunchFailure {
    /** The app could not be seated, which usually means its install is corrupted. */
    object SeatFailed : AppLaunchFailure()

    /** [attemptsExhausted] is true once [LaunchOutcomeRouter.MAX_LAUNCH_ATTEMPTS] launches have failed. */
    data class Retryable(
        val error: LoginError,
        val attemptsExhausted: Boolean,
    ) : AppLaunchFailure()
}

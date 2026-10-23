package org.commcare.connect

/** Actions a caller runs for a [LaunchOutcome]; a seam so the mapping is testable without a fragment. */
interface LaunchActions {
    fun dismissProgress()

    fun launchHome(alreadyLoggedIn: Boolean)

    fun handleFailure(failure: AppLaunchFailure)
}

/** Maps each [LaunchOutcome] to its [LaunchActions] calls, always dismissing progress first. */
object LaunchOutcomeRouter {
    /** A retryable failure is marked [AppLaunchFailure.Retryable.attemptsExhausted] once this many attempts have failed. */
    const val MAX_LAUNCH_ATTEMPTS = 3

    fun dispatch(
        outcome: LaunchOutcome,
        failedAttempts: Int,
        actions: LaunchActions,
    ) {
        actions.dismissProgress()
        when (outcome) {
            is LaunchOutcome.Launched -> {
                actions.launchHome(outcome.alreadyLoggedIn)
            }

            LaunchOutcome.AppSeatFailed -> {
                actions.handleFailure(AppLaunchFailure.SeatFailed)
            }

            is LaunchOutcome.Retryable -> {
                actions.handleFailure(
                    AppLaunchFailure.Retryable(outcome.error, failedAttempts >= MAX_LAUNCH_ATTEMPTS),
                )
            }
        }
    }
}

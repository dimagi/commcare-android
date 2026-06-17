package org.commcare.connect

/** Actions a caller runs for a [LaunchOutcome]; a seam so the mapping is testable without a fragment. */
interface LaunchActions {
    fun dismissProgress()

    fun launchHome()

    fun recoverFromSeatFailure()

    fun promptRetry()

    fun showPersistentError()

    fun reportFailure(reason: String)
}

/** Maps each [LaunchOutcome] to its [LaunchActions] calls, always dismissing progress first. */
object LaunchOutcomeRouter {
    /** A retryable failure escalates to [LaunchActions.showPersistentError] once this many attempts have failed. */
    const val MAX_LAUNCH_ATTEMPTS = 3

    fun dispatch(
        outcome: LaunchOutcome,
        failedAttempts: Int,
        actions: LaunchActions,
    ) {
        actions.dismissProgress()
        when (outcome) {
            LaunchOutcome.Launched -> {
                actions.launchHome()
            }

            LaunchOutcome.AppSeatFailed -> {
                actions.reportFailure(outcome::class.java.simpleName)
                actions.recoverFromSeatFailure()
            }

            is LaunchOutcome.Retryable -> {
                actions.reportFailure(outcome.error::class.java.simpleName)
                if (failedAttempts >= MAX_LAUNCH_ATTEMPTS) {
                    actions.showPersistentError()
                } else {
                    actions.promptRetry()
                }
            }
        }
    }
}

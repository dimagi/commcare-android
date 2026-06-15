package org.commcare.connect

/** Actions a caller runs for a [LaunchOutcome]; a seam so the mapping is testable without a fragment. */
interface LaunchActions {
    fun dismissProgress()

    fun launchHome()

    fun handleTokenDenied()

    fun recoverFromSeatFailure()

    fun fallBackToLegacyLaunch()

    fun reportFailure(reason: String)
}

/** Maps each [LaunchOutcome] to its [LaunchActions] calls, always dismissing progress first. */
object LaunchOutcomeRouter {
    fun dispatch(
        outcome: LaunchOutcome,
        actions: LaunchActions,
    ) {
        actions.dismissProgress()
        when (outcome) {
            LaunchOutcome.Launched -> {
                actions.launchHome()
            }

            LaunchOutcome.TokenDenied -> {
                actions.handleTokenDenied()
            }

            LaunchOutcome.AppSeatFailed -> {
                actions.recoverFromSeatFailure()
            }

            is LaunchOutcome.Retryable -> {
                actions.reportFailure(outcome.error::class.java.simpleName)
                actions.fallBackToLegacyLaunch()
            }
        }
    }
}

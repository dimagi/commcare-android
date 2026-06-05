package org.commcare.connect

/** Actions a caller runs for a [SilentLaunchOutcome]; a seam so the mapping is testable without a fragment. */
interface SilentLaunchActions {
    fun dismissProgress()

    fun launchHome()

    fun handleTokenDenied()

    fun recoverFromSeatFailure()

    fun fallBackToLegacyLaunch()

    fun reportFailure(reason: String)

    fun ignoreAlreadyLaunching()
}

/** Maps each [SilentLaunchOutcome] to its [SilentLaunchActions] calls, always dismissing progress first. */
object SilentLaunchOutcomeRouter {
    fun dispatch(
        outcome: SilentLaunchOutcome,
        actions: SilentLaunchActions,
    ) {
        actions.dismissProgress()
        when (outcome) {
            SilentLaunchOutcome.Launched -> actions.launchHome()
            SilentLaunchOutcome.TokenDenied -> actions.handleTokenDenied()
            SilentLaunchOutcome.AppSeatFailed -> actions.recoverFromSeatFailure()
            SilentLaunchOutcome.CredentialResolutionFailed -> {
                actions.reportFailure("CredentialResolutionFailed")
                actions.fallBackToLegacyLaunch()
            }
            is SilentLaunchOutcome.Retryable -> {
                actions.reportFailure(outcome.error::class.java.simpleName)
                actions.fallBackToLegacyLaunch()
            }
            SilentLaunchOutcome.AlreadyLaunching -> actions.ignoreAlreadyLaunching()
        }
    }
}

package org.commcare.connect

/**
 * UI/navigation actions a caller performs in response to a [SilentLaunchOutcome]. Kept as a seam so
 * the outcome-to-action mapping is unit-testable without a hosting fragment.
 */
interface SilentLaunchActions {
    fun dismissProgress()

    fun launchHome()

    fun handleTokenDenied()

    fun recoverFromSeatFailure()

    fun fallBackToLegacyLaunch()

    fun reportFailure(reason: String)

    fun ignoreAlreadyLaunching()
}

/**
 * Maps a [SilentLaunchOutcome] to the right [SilentLaunchActions] calls. The progress UI is always
 * dismissed first, and the exhaustive `when` guarantees every outcome has a defined terminal action.
 */
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

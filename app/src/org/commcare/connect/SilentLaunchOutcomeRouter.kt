package org.commcare.connect

import org.commcare.login.PostLoginDestination

/**
 * UI/navigation actions a caller performs in response to a [SilentLaunchOutcome]. Kept as a seam so
 * the outcome-to-action mapping is unit-testable without a hosting fragment.
 */
interface SilentLaunchActions {
    fun dismissProgress()

    fun goHome(home: PostLoginDestination.Home)

    fun handleTokenDenied()

    fun recoverFromSeatFailure()

    fun showRetry()

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
            is SilentLaunchOutcome.Launched -> actions.goHome(outcome.home)
            SilentLaunchOutcome.TokenDenied -> actions.handleTokenDenied()
            SilentLaunchOutcome.AppSeatFailed -> actions.recoverFromSeatFailure()
            SilentLaunchOutcome.CredentialResolutionFailed -> {
                actions.reportFailure("CredentialResolutionFailed")
                actions.showRetry()
            }
            is SilentLaunchOutcome.Retryable -> {
                actions.reportFailure(outcome.error::class.java.simpleName)
                actions.showRetry()
            }
            SilentLaunchOutcome.AlreadyLaunching -> actions.ignoreAlreadyLaunching()
        }
    }
}

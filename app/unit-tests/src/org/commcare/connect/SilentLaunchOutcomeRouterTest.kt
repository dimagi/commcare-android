package org.commcare.connect

import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.commcare.activities.LoginMode
import org.commcare.login.LoginError
import org.commcare.login.PostLoginDestination
import org.junit.Test

/**
 * Verifies the outcome-to-action dispatch: the progress UI is dismissed on every path and each
 * [SilentLaunchOutcome] maps to exactly the expected [SilentLaunchActions] calls.
 */
class SilentLaunchOutcomeRouterTest {
    private val actions = mockk<SilentLaunchActions>(relaxed = true)

    private val home =
        PostLoginDestination.Home(
            loginMode = LoginMode.PASSWORD,
            startFromLogin = true,
            manualSwitchToPwMode = false,
            personalIdManagedLogin = true,
        )

    @Test
    fun `launched goes home after dismissing progress`() {
        SilentLaunchOutcomeRouter.dispatch(SilentLaunchOutcome.Launched(home), actions)

        verifyOrder {
            actions.dismissProgress()
            actions.goHome(home)
        }
    }

    @Test
    fun `token denied delegates to the token handler`() {
        SilentLaunchOutcomeRouter.dispatch(SilentLaunchOutcome.TokenDenied, actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.handleTokenDenied() }
    }

    @Test
    fun `app seat failure routes to recovery`() {
        SilentLaunchOutcomeRouter.dispatch(SilentLaunchOutcome.AppSeatFailed, actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.recoverFromSeatFailure() }
    }

    @Test
    fun `credential resolution failure reports and falls back to the legacy launch`() {
        SilentLaunchOutcomeRouter.dispatch(SilentLaunchOutcome.CredentialResolutionFailed, actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.reportFailure("CredentialResolutionFailed") }
        verify(exactly = 1) { actions.fallBackToLegacyLaunch() }
    }

    @Test
    fun `retryable reports the error name and falls back to the legacy launch`() {
        SilentLaunchOutcomeRouter.dispatch(SilentLaunchOutcome.Retryable(LoginError.BadCredentials), actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.reportFailure("BadCredentials") }
        verify(exactly = 1) { actions.fallBackToLegacyLaunch() }
    }

    @Test
    fun `already launching is dismissed and ignored`() {
        SilentLaunchOutcomeRouter.dispatch(SilentLaunchOutcome.AlreadyLaunching, actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.ignoreAlreadyLaunching() }
    }
}

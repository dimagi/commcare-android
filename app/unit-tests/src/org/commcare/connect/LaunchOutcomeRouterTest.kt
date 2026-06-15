package org.commcare.connect

import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.commcare.login.LoginError
import org.junit.Test

class LaunchOutcomeRouterTest {
    private val actions = mockk<LaunchActions>(relaxed = true)

    @Test
    fun `launched goes home after dismissing progress`() {
        LaunchOutcomeRouter.dispatch(LaunchOutcome.Launched, actions)

        verifyOrder {
            actions.dismissProgress()
            actions.launchHome()
        }
    }

    @Test
    fun `app seat failure routes to recovery`() {
        LaunchOutcomeRouter.dispatch(LaunchOutcome.AppSeatFailed, actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.recoverFromSeatFailure() }
    }

    @Test
    fun `retryable reports the error name and falls back to the legacy launch`() {
        LaunchOutcomeRouter.dispatch(LaunchOutcome.Retryable(LoginError.BadCredentials), actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.reportFailure("BadCredentials") }
        verify(exactly = 1) { actions.fallBackToLegacyLaunch() }
    }
}

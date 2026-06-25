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
        LaunchOutcomeRouter.dispatch(LaunchOutcome.Launched(alreadyLoggedIn = false), 0, actions)

        verifyOrder {
            actions.dismissProgress()
            actions.launchHome(false)
        }
    }

    @Test
    fun `launched forwards the already-logged-in flag to launchHome`() {
        LaunchOutcomeRouter.dispatch(LaunchOutcome.Launched(alreadyLoggedIn = true), 0, actions)

        verify(exactly = 1) { actions.launchHome(true) }
    }

    @Test
    fun `app seat failure reports and routes to recovery`() {
        LaunchOutcomeRouter.dispatch(LaunchOutcome.AppSeatFailed, 0, actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.reportFailure("AppSeatFailed") }
        verify(exactly = 1) { actions.recoverFromSeatFailure() }
    }

    @Test
    fun `retryable below the attempt limit reports the error name and prompts retry`() {
        LaunchOutcomeRouter.dispatch(
            LaunchOutcome.Retryable(LoginError.BadCredentials),
            LaunchOutcomeRouter.MAX_LAUNCH_ATTEMPTS - 1,
            actions,
        )

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.reportFailure("BadCredentials") }
        verify(exactly = 1) { actions.promptRetry() }
        verify(exactly = 0) { actions.showPersistentError() }
    }

    @Test
    fun `retryable at the attempt limit shows the persistent error instead of retry`() {
        LaunchOutcomeRouter.dispatch(
            LaunchOutcome.Retryable(LoginError.NetworkUnavailable),
            LaunchOutcomeRouter.MAX_LAUNCH_ATTEMPTS,
            actions,
        )

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.reportFailure("NetworkUnavailable") }
        verify(exactly = 1) { actions.showPersistentError() }
        verify(exactly = 0) { actions.promptRetry() }
    }
}

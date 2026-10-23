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
    fun `app seat failure handles a seat failure`() {
        LaunchOutcomeRouter.dispatch(LaunchOutcome.AppSeatFailed, 0, actions)

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) { actions.handleFailure(AppLaunchFailure.SeatFailed) }
    }

    @Test
    fun `retryable below the attempt limit handles a retryable failure`() {
        LaunchOutcomeRouter.dispatch(
            LaunchOutcome.Retryable(LoginError.BadCredentials),
            LaunchOutcomeRouter.MAX_LAUNCH_ATTEMPTS - 1,
            actions,
        )

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) {
            actions.handleFailure(AppLaunchFailure.Retryable(LoginError.BadCredentials, attemptsExhausted = false))
        }
    }

    @Test
    fun `retryable at the attempt limit handles a failure with attempts exhausted`() {
        LaunchOutcomeRouter.dispatch(
            LaunchOutcome.Retryable(LoginError.NetworkUnavailable),
            LaunchOutcomeRouter.MAX_LAUNCH_ATTEMPTS,
            actions,
        )

        verify(exactly = 1) { actions.dismissProgress() }
        verify(exactly = 1) {
            actions.handleFailure(AppLaunchFailure.Retryable(LoginError.NetworkUnavailable, attemptsExhausted = true))
        }
    }
}

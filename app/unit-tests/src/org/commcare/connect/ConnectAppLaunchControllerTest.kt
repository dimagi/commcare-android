package org.commcare.connect

import androidx.fragment.app.Fragment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.login.LoginError
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger
import org.junit.After
import org.junit.Before
import org.junit.Test

class ConnectAppLaunchControllerTest {
    private val fragment =
        mockk<Fragment>(relaxed = true) {
            every { view } returns mockk()
            every { isAdded } returns true
            every { childFragmentManager.isStateSaved } returns true
        }
    private val launcher = mockk<ConnectAppLauncher>(relaxed = true)
    private val listener = mockk<ConnectAppLaunchListener>(relaxed = true)
    private val outcomeCallbacks = mutableListOf<ConnectAppLauncher.OutcomeCallback>()

    private val controller = ConnectAppLaunchController(fragment, listener, launcher)

    @Before
    fun setUp() {
        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportCccAppFailedAutoLogin(any()) } returns Unit
        mockkStatic(Logger::class)
        every { Logger.log(any(), any()) } returns Unit
        every { launcher.start(any(), any(), any(), any(), any(), any()) } answers {
            outcomeCallbacks += arg<ConnectAppLauncher.OutcomeCallback>(5)
            mockk()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a seat failure is logged and reported before the listener is notified`() {
        controller.launchApp(APP_ID, isLearning = true)
        outcomeCallbacks.last().onOutcome(LaunchOutcome.AppSeatFailed)

        verifyOrder {
            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, "Connect launch failed for app $APP_ID: SeatFailed")
            FirebaseAnalyticsUtil.reportCccAppFailedAutoLogin(APP_ID)
            listener.onLaunchFailed(AppLaunchFailure.SeatFailed)
        }
    }

    @Test
    fun `a retryable failure is logged with the login error name`() {
        controller.launchApp(APP_ID, isLearning = true)
        failLatestLaunch()

        verify(exactly = 1) {
            Logger.log(LogTypes.TYPE_ERROR_WORKFLOW, "Connect launch failed for app $APP_ID: NetworkUnavailable")
        }
    }

    @Test
    fun `retries count toward the attempt limit`() {
        controller.launchApp(APP_ID, isLearning = true)
        failLatestLaunch()
        controller.retryLaunch()
        failLatestLaunch()
        controller.retryLaunch()
        failLatestLaunch()

        verifyOrder {
            listener.onLaunchFailed(retryableFailure(attemptsExhausted = false))
            listener.onLaunchFailed(retryableFailure(attemptsExhausted = false))
            listener.onLaunchFailed(retryableFailure(attemptsExhausted = true))
        }
    }

    @Test
    fun `a fresh launch resets the attempt count`() {
        controller.launchApp(APP_ID, isLearning = true)
        repeat(LaunchOutcomeRouter.MAX_LAUNCH_ATTEMPTS - 1) {
            failLatestLaunch()
            controller.retryLaunch()
        }
        failLatestLaunch()
        controller.launchApp(APP_ID, isLearning = true)
        failLatestLaunch()

        verifyOrder {
            listener.onLaunchFailed(retryableFailure(attemptsExhausted = true))
            listener.onLaunchFailed(retryableFailure(attemptsExhausted = false))
        }
    }

    private fun failLatestLaunch() {
        outcomeCallbacks.last().onOutcome(LaunchOutcome.Retryable(LoginError.NetworkUnavailable))
    }

    private fun retryableFailure(attemptsExhausted: Boolean) = AppLaunchFailure.Retryable(LoginError.NetworkUnavailable, attemptsExhausted)

    companion object {
        private const val APP_ID = "app-id"
    }
}

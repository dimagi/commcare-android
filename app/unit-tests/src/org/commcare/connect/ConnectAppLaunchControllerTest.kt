package org.commcare.connect

import android.content.Intent
import androidx.fragment.app.Fragment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.commcare.activities.StandardHomeActivity
import org.commcare.activities.connect.ConnectActivity
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
    private val connectActivity = mockk<ConnectActivity>(relaxed = true)
    private val opportunityHomeRootIntent = mockk<Intent>(relaxed = true)

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

    @Test
    fun `a new sign-in from opportunity home at the bottom of the task opens app home on top of it`() {
        val homeIntent = stubLaunchIntents(opportunityHomeAtTaskBottom = true)

        controller.launchApp(APP_ID, isLearning = true)
        outcomeCallbacks.last().onOutcome(LaunchOutcome.Launched(alreadyLoggedIn = false))

        verify(exactly = 1) { connectActivity.startActivity(homeIntent) }
        verify(exactly = 0) { connectActivity.startActivities(any()) }
    }

    @Test
    fun `a new sign-in from opportunity home above other screens reopens it beneath app home in a new task`() {
        val homeIntent = stubLaunchIntents(opportunityHomeAtTaskBottom = false)

        controller.launchApp(APP_ID, isLearning = true)
        outcomeCallbacks.last().onOutcome(LaunchOutcome.Launched(alreadyLoggedIn = false))

        verify(exactly = 1) { connectActivity.startActivities(arrayOf(opportunityHomeRootIntent, homeIntent)) }
        verify(exactly = 0) { connectActivity.startActivity(any()) }
    }

    @Test
    fun `resuming an app already signed in brings its home forward without resetting the task`() {
        val homeIntent = stubLaunchIntents(opportunityHomeAtTaskBottom = false)

        controller.launchApp(APP_ID, isLearning = true)
        outcomeCallbacks.last().onOutcome(LaunchOutcome.Launched(alreadyLoggedIn = true))

        verify(exactly = 1) { homeIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) }
        verify(exactly = 1) { connectActivity.startActivity(homeIntent) }
        verify(exactly = 0) { connectActivity.startActivities(any()) }
    }

    /** Points the launch at [connectActivity] and returns the App Home intent it will start. */
    private fun stubLaunchIntents(opportunityHomeAtTaskBottom: Boolean): Intent {
        val homeIntent = mockk<Intent>(relaxed = true)
        every { homeIntent.addFlags(any()) } returns homeIntent
        every { fragment.requireActivity() } returns connectActivity
        every { connectActivity.isOpportunityHomeAtTaskBottom } returns opportunityHomeAtTaskBottom
        every { connectActivity.activeJob } returns mockk { every { jobUUID } returns JOB_UUID }
        mockkStatic(StandardHomeActivity::class)
        every { StandardHomeActivity.buildHomeLaunchIntent(any()) } returns homeIntent
        mockkStatic(ConnectNavHelper::class)
        every { ConnectNavHelper.buildOpportunityHomeTaskRootIntent(any(), JOB_UUID) } returns opportunityHomeRootIntent
        return homeIntent
    }

    private fun failLatestLaunch() {
        outcomeCallbacks.last().onOutcome(LaunchOutcome.Retryable(LoginError.NetworkUnavailable))
    }

    private fun retryableFailure(attemptsExhausted: Boolean) = AppLaunchFailure.Retryable(LoginError.NetworkUnavailable, attemptsExhausted)

    companion object {
        private const val APP_ID = "app-id"
        private const val JOB_UUID = "job-uuid"
    }
}

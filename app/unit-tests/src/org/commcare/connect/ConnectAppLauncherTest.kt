package org.commcare.connect

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.commcare.CommCareApplication
import org.commcare.activities.LoginMode
import org.commcare.android.database.app.models.UserKeyRecord
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.login.AuthSource
import org.commcare.login.LoginError
import org.commcare.login.LoginProgressSink
import org.commcare.login.LoginRequest
import org.commcare.login.LoginResult
import org.commcare.login.PostLoginOutcome
import org.commcare.login.SeatFailure
import org.commcare.login.SeatResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the silent-launch orchestration: phase ordering, credential normalization, outcome
 * mapping for each failure mode, and the re-entrancy guard.
 */
class ConnectAppLauncherTest {
    private val context = mockk<Context>(relaxed = true)
    private val app = mockk<CommCareApplication>(relaxed = true)
    private val personalIdManager = mockk<PersonalIdManager>(relaxed = true)
    private val sink = LoginProgressSink { }

    private var seatResult: SeatResult = SeatResult.Success
    private var loginAnswer: suspend () -> LoginResult = { success() }
    private val seatedApps = mutableListOf<String>()
    private val capturedRequests = mutableListOf<LoginRequest>()

    private val launcher =
        ConnectAppLauncher(
            seatApp = { appId, _ ->
                seatedApps += appId
                seatResult
            },
            performLogin = { _, request, _ ->
                capturedRequests += request
                loginAnswer()
            },
        )

    @Before
    fun setUp() {
        mockkStatic(CommCareApplication::class)
        every { CommCareApplication.instance() } returns app
        every { app.getAppStorage(UserKeyRecord::class.java) } returns mockk(relaxed = true)

        mockkStatic(PersonalIdManager::class)
        every { PersonalIdManager.getInstance() } returns personalIdManager
        every { personalIdManager.getConnectUsername(any()) } returns "Alice "

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportCccAppLaunch(any(), any()) } returns Unit
        every { FirebaseAnalyticsUtil.reportCccAppFailedAutoLogin(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun success() =
        LoginResult.Success(
            appId = "app-1",
            username = "alice",
            loginMode = LoginMode.PASSWORD,
            restoreSession = false,
            personalIdManagedLogin = true,
            linkPassword = "pw",
            postLoginOutcome = PostLoginOutcome(redirectToConnectOpportunityInfo = false),
        )

    @Test
    fun `happy path closes session, reports launch, seats, and returns Launched`() =
        runTest {
            val expected = success()
            loginAnswer = { expected }

            val outcome = launcher.launch(context, "app-1", isLearning = false, sink)

            assertEquals(SilentLaunchOutcome.Launched(expected), outcome)
            verify(exactly = 1) { app.closeUserSession() }
            verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccAppLaunch("Deliver", "app-1") }
            assertEquals(listOf("app-1"), seatedApps)
        }

    @Test
    fun `learning launch reports Learn app type`() =
        runTest {
            launcher.launch(context, "app-1", isLearning = true, sink)

            verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccAppLaunch("Learn", "app-1") }
        }

    @Test
    fun `request uses normalized PersonalId credentials`() =
        runTest {
            launcher.launch(context, "app-1", isLearning = false, sink)

            val request = capturedRequests.single()
            assertEquals("alice", request.username)
            assertEquals("app-1", request.appId)
            assertEquals(AuthSource.PersonalId, request.authSource)
            assertEquals("", request.passwordOrPin)
        }

    @Test
    fun `seat failure short-circuits before login`() =
        runTest {
            seatResult = SeatResult.Failed(SeatFailure.CORRUPTED)

            val outcome = launcher.launch(context, "app-1", isLearning = false, sink)

            assertEquals(SilentLaunchOutcome.AppSeatFailed, outcome)
            assertTrue(capturedRequests.isEmpty())
        }

    @Test
    fun `token denied maps to TokenDenied`() =
        runTest {
            loginAnswer = { LoginResult.Failed(LoginError.TokenDenied) }

            val outcome = launcher.launch(context, "app-1", isLearning = false, sink)

            assertEquals(SilentLaunchOutcome.TokenDenied, outcome)
        }

    @Test
    fun `other failures map to Retryable carrying the error`() =
        runTest {
            loginAnswer = { LoginResult.Failed(LoginError.BadCredentials) }

            val outcome = launcher.launch(context, "app-1", isLearning = false, sink)

            assertEquals(SilentLaunchOutcome.Retryable(LoginError.BadCredentials), outcome)
        }

    @Test
    fun `concurrent launch is rejected as AlreadyLaunching`() =
        runTest {
            val gate = CompletableDeferred<LoginResult>()
            loginAnswer = { gate.await() }

            val first =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    launcher.launch(context, "app-1", isLearning = false, sink)
                }

            val second = launcher.launch(context, "app-1", isLearning = false, sink)
            assertEquals(SilentLaunchOutcome.AlreadyLaunching, second)

            gate.complete(success())
            first.join()
            assertTrue(first.isCompleted)
        }
}

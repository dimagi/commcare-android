package org.commcare.connect

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.commcare.CommCareApplication
import org.commcare.activities.LoginMode
import org.commcare.android.database.app.models.UserKeyRecord
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.login.AuthSource
import org.commcare.login.LoginError
import org.commcare.login.LoginProgressListener
import org.commcare.login.LoginRequest
import org.commcare.login.LoginResult
import org.commcare.login.PostLoginOutcome
import org.commcare.login.SeatResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectAppLauncherTest {
    private val context = mockk<Context>(relaxed = true)
    private val app = mockk<CommCareApplication>(relaxed = true)
    private val listener = LoginProgressListener { }

    private var seatResult: SeatResult = SeatResult.Success
    private var loginAnswer: suspend () -> LoginResult = { success() }
    private var username: String? = "Alice "
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
            connectUsername = { username },
        )

    @Before
    fun setUp() {
        mockkStatic(CommCareApplication::class)
        every { CommCareApplication.instance() } returns app
        every { app.getAppStorage(UserKeyRecord::class.java) } returns mockk(relaxed = true)

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportCccAppLaunch(any(), any()) } returns Unit
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
    fun `happy path closes session, reports launch, seats, and reports Launched`() =
        runTest {
            val outcome = launcher.awaitOutcome(context, "app-1", isLearning = false, listener)

            assertEquals(LaunchOutcome.Launched, outcome)
            verify(exactly = 1) { app.closeUserSession() }
            verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccAppLaunch("Deliver", "app-1") }
            assertEquals(listOf("app-1"), seatedApps)
        }

    @Test
    fun `learning launch reports Learn app type`() =
        runTest {
            launcher.awaitOutcome(context, "app-1", isLearning = true, listener)

            verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccAppLaunch("Learn", "app-1") }
        }

    @Test
    fun `request uses normalized PersonalId credentials`() =
        runTest {
            launcher.awaitOutcome(context, "app-1", isLearning = false, listener)

            val request = capturedRequests.single()
            assertEquals("alice", request.username)
            assertEquals("app-1", request.appId)
            assertEquals(AuthSource.PersonalId, request.authSource)
            assertEquals("", request.passwordOrPin)
        }

    @Test
    fun `missing connect user short-circuits to CredentialResolutionFailed`() =
        runTest {
            username = null

            val outcome = launcher.awaitOutcome(context, "app-1", isLearning = false, listener)

            assertEquals(LaunchOutcome.CredentialResolutionFailed, outcome)
            assertTrue(capturedRequests.isEmpty())
        }

    @Test
    fun `blank connect user short-circuits to CredentialResolutionFailed`() =
        runTest {
            username = "   "

            val outcome = launcher.awaitOutcome(context, "app-1", isLearning = false, listener)

            assertEquals(LaunchOutcome.CredentialResolutionFailed, outcome)
            assertTrue(capturedRequests.isEmpty())
        }

    @Test
    fun `seat failure short-circuits before login`() =
        runTest {
            seatResult = SeatResult.Failed

            val outcome = launcher.awaitOutcome(context, "app-1", isLearning = false, listener)

            assertEquals(LaunchOutcome.AppSeatFailed, outcome)
            assertTrue(capturedRequests.isEmpty())
        }

    @Test
    fun `token denied maps to TokenDenied`() =
        runTest {
            loginAnswer = { LoginResult.Failed(LoginError.TokenDenied) }

            val outcome = launcher.awaitOutcome(context, "app-1", isLearning = false, listener)

            assertEquals(LaunchOutcome.TokenDenied, outcome)
        }

    @Test
    fun `other failures map to Retryable carrying the error`() =
        runTest {
            loginAnswer = { LoginResult.Failed(LoginError.BadCredentials) }

            val outcome = launcher.awaitOutcome(context, "app-1", isLearning = false, listener)

            assertEquals(LaunchOutcome.Retryable(LoginError.BadCredentials), outcome)
        }
}

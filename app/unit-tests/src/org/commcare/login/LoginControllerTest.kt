package org.commcare.login

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.commcare.activities.CommCareActivity
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode
import org.commcare.connect.PersonalIdManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Hand-written fakes for collaborators whose suspend functions return sealed types.
 * mockk 1.12.7 cannot fabricate a default placeholder for sealed classes during coEvery
 * recording, so fakes are used instead for KeyRecordOperations, SyncOperations, and
 * DemoLoginPath. ConnectCredentialResolver and PostLoginSideEffects return concrete types
 * and are mocked normally.
 */
private class FakeKeyRecordOperations(
    private val result: KeyRecordOutcome,
) : KeyRecordOperations(
        context = mockk(relaxed = true),
        app = mockk(relaxed = true),
    ) {
    var capturedRequest: LoginRequest? = null
    var callCount = 0

    override suspend fun manageKeyRecord(
        request: LoginRequest,
        sink: LoginProgressSink,
    ): KeyRecordOutcome {
        capturedRequest = request
        callCount++
        return result
    }
}

private class FakeSyncOperations(
    private val result: SyncOutcome,
) : SyncOperations(context = mockk(relaxed = true)) {
    var capturedUsername: String? = null
    var capturedPassword: String? = null
    var callCount = 0

    override suspend fun pullData(
        username: String,
        password: String,
        sink: LoginProgressSink,
    ): SyncOutcome {
        capturedUsername = username
        capturedPassword = password
        callCount++
        return result
    }
}

private class FakeDemoLoginPath(
    private val result: SyncOutcome,
) : DemoLoginPath(context = mockk(relaxed = true)) {
    var callCount = 0

    override suspend fun login(sink: LoginProgressSink): SyncOutcome {
        callCount++
        return result
    }
}

class LoginControllerTest {
    private val credentialResolver = mockk<ConnectCredentialResolver>(relaxed = true)
    private val postLoginSideEffects = mockk<PostLoginSideEffects>(relaxed = true)
    private val personalIdManager = mockk<PersonalIdManager>(relaxed = true)
    private val activity = mockk<CommCareActivity<Any>>(relaxed = true)
    private val sink = LoginProgressSink { /* no-op */ }

    @Before
    fun setUp() {
        mockkStatic(PersonalIdManager::class)
        every { PersonalIdManager.getInstance() } returns personalIdManager
        every { personalIdManager.isloggedIn() } returns false
        coEvery { postLoginSideEffects.runOnSuccess(any(), any()) } returns PostLoginOutcome(false)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun buildController(
        keyRecordOutcome: KeyRecordOutcome = KeyRecordOutcome.LocalLoginComplete,
        syncOutcome: SyncOutcome = SyncOutcome.Success,
        demoOutcome: SyncOutcome = SyncOutcome.Success,
    ): Triple<LoginController, FakeKeyRecordOperations, FakeSyncOperations> {
        val fakeKeyRecord = FakeKeyRecordOperations(keyRecordOutcome)
        val fakeSync = FakeSyncOperations(syncOutcome)
        val fakeDemo = FakeDemoLoginPath(demoOutcome)
        val controller =
            LoginController(
                fakeKeyRecord,
                fakeSync,
                fakeDemo,
                credentialResolver,
                postLoginSideEffects,
            )
        return Triple(controller, fakeKeyRecord, fakeSync)
    }

    private fun buildControllerWithDemo(demoOutcome: SyncOutcome): Pair<LoginController, FakeDemoLoginPath> {
        val fakeDemo = FakeDemoLoginPath(demoOutcome)
        val fakeKeyRecord = FakeKeyRecordOperations(KeyRecordOutcome.LocalLoginComplete)
        val controller =
            LoginController(
                fakeKeyRecord,
                FakeSyncOperations(SyncOutcome.Success),
                fakeDemo,
                credentialResolver,
                postLoginSideEffects,
            )
        return Pair(controller, fakeDemo)
    }

    @Test
    fun `manual happy path - local login complete returns Success`() =
        runTest {
            val (controller, fakeKeyRecord, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.LocalLoginComplete,
                )

            val result = controller.performLogin(activity, manualRequest(), sink)

            assertTrue(result is LoginResult.Success)
            assertEquals(false, (result as LoginResult.Success).connectManagedLogin)
            assertEquals(1, fakeKeyRecord.callCount)
            coVerify(exactly = 1) { postLoginSideEffects.runOnSuccess(activity, "alice") }
        }

    @Test
    fun `AutoFromConnect rewrites password using credentialResolver`() =
        runTest {
            val (controller, fakeKeyRecord, fakeSync) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.ReadyForSync("resolved-pw", DataPullMode.NORMAL),
                    syncOutcome = SyncOutcome.Success,
                )
            val resolved = ResolvedCredentials(password = "resolved-pw", record = mockk(relaxed = true))
            every { credentialResolver.resolve("app-1", "alice", true) } returns resolved

            val request = manualRequest().copy(authSource = AuthSource.AutoFromConnect)
            val result = controller.performLogin(activity, request, sink)

            assertTrue(result is LoginResult.Success)
            assertEquals(true, (result as LoginResult.Success).connectManagedLogin)
            assertEquals("alice", fakeSync.capturedUsername)
            assertEquals("resolved-pw", fakeSync.capturedPassword)
            assertEquals("resolved-pw", fakeKeyRecord.capturedRequest?.passwordOrPin)
            verify(exactly = 1) { credentialResolver.resolve("app-1", "alice", true) }
        }

    @Test
    fun `Demo path uses DemoLoginPath only`() =
        runTest {
            val (controller, fakeDemo) = buildControllerWithDemo(SyncOutcome.Success)

            val request = manualRequest().copy(authSource = AuthSource.Demo)
            val result = controller.performLogin(activity, request, sink)

            assertTrue(result is LoginResult.Success)
            assertEquals(1, fakeDemo.callCount)
            verify(exactly = 0) { credentialResolver.resolve(any(), any(), any()) }
        }

    @Test
    fun `BadCredentials from key-record returns Failed without running side-effects`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.Failed(LoginError.BadCredentials),
                )

            val result = controller.performLogin(activity, manualRequest(), sink)

            assertEquals(LoginResult.Failed(LoginError.BadCredentials), result)
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any(), any()) }
        }

    @Test
    fun `NetworkUnavailable from key-record returns Failed`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.Failed(LoginError.NetworkUnavailable),
                )

            val result = controller.performLogin(activity, manualRequest(), sink)

            assertEquals(LoginResult.Failed(LoginError.NetworkUnavailable), result)
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any(), any()) }
        }

    @Test
    fun `TokenDenied from key-record returns Failed`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.Failed(LoginError.TokenDenied),
                )

            val result = controller.performLogin(activity, manualRequest(), sink)

            assertEquals(LoginResult.Failed(LoginError.TokenDenied), result)
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any(), any()) }
        }

    @Test
    fun `SyncFailed from sync after ReadyForSync returns Failed without side-effects`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.ReadyForSync("secret", DataPullMode.NORMAL),
                    syncOutcome = SyncOutcome.Failed(LoginError.SyncFailed("SERVER_ERROR", "something went wrong")),
                )

            val result = controller.performLogin(activity, manualRequest(), sink)

            assertEquals(
                LoginResult.Failed(LoginError.SyncFailed("SERVER_ERROR", "something went wrong")),
                result,
            )
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any(), any()) }
        }

    @Test
    fun `Demo failure returns Failed`() =
        runTest {
            val (controller, _) =
                buildControllerWithDemo(
                    SyncOutcome.Failed(LoginError.SyncFailed("DEMO_RESTORE_MISSING", null)),
                )

            val request = manualRequest().copy(authSource = AuthSource.Demo)
            val result = controller.performLogin(activity, request, sink)

            assertEquals(
                LoginResult.Failed(LoginError.SyncFailed("DEMO_RESTORE_MISSING", null)),
                result,
            )
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any(), any()) }
        }

    @Test
    fun `PostLoginOutcome carries through to Success`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.LocalLoginComplete,
                )
            coEvery { postLoginSideEffects.runOnSuccess(any(), any()) } returns
                PostLoginOutcome(redirectToConnectOpportunityInfo = true)

            val result = controller.performLogin(activity, manualRequest(), sink)

            assertTrue(result is LoginResult.Success)
            assertEquals(true, (result as LoginResult.Success).postLoginOutcome.redirectToConnectOpportunityInfo)
        }

    private fun manualRequest(username: String = "alice") =
        LoginRequest(
            appId = "app-1",
            username = username,
            passwordOrPin = "secret",
            credentialType = LoginMode.PASSWORD,
            authSource = AuthSource.Manual,
            restoreSession = false,
            pullMode = DataPullMode.NORMAL,
            triggerMultipleUsersWarning = false,
            blockRemoteKeyManagement = false,
        )
}

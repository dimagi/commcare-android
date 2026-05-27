package org.commcare.login

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.commcare.activities.LoginMode
import org.commcare.connect.PersonalIdManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

class LoginControllerTest {
    private val credentialResolver = mockk<ConnectCredentialResolver>(relaxed = true)
    private val postLoginSideEffects = mockk<PostLoginSideEffects>(relaxed = true)
    private val personalIdManager = mockk<PersonalIdManager>(relaxed = true)
    private val sink = LoginProgressSink { }

    @Before
    fun setUp() {
        mockkStatic(PersonalIdManager::class)
        every { PersonalIdManager.getInstance() } returns personalIdManager
        every { personalIdManager.isloggedIn() } returns false
        coEvery { postLoginSideEffects.runOnSuccess(any()) } returns PostLoginOutcome(false)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private data class Harness(
        val controller: LoginController,
        val keyRecord: FakeKeyRecordOperations,
        val sync: FakeSyncOperations,
    )

    private fun buildController(
        keyRecordOutcome: KeyRecordOutcome = KeyRecordOutcome.LocalLoginComplete,
        syncOutcome: SyncOutcome = SyncOutcome.Success,
    ): Harness {
        val fakeKeyRecord = FakeKeyRecordOperations(keyRecordOutcome)
        val fakeSync = FakeSyncOperations(syncOutcome)
        val controller =
            LoginController(
                fakeKeyRecord,
                fakeSync,
                credentialResolver,
                postLoginSideEffects,
            )
        return Harness(controller, fakeKeyRecord, fakeSync)
    }

    @Test
    fun `manual happy path - local login complete returns Success`() =
        runTest {
            val (controller, fakeKeyRecord, _) =
                buildController(keyRecordOutcome = KeyRecordOutcome.LocalLoginComplete)

            val result = controller.performLogin(manualRequest(), sink)

            assertTrue(result is LoginResult.Success)
            val success = result as LoginResult.Success
            assertFalse(success.connectManagedLogin)
            assertFalse(success.personalIdManagedLogin)
            assertEquals(LoginMode.PASSWORD, success.loginMode)
            assertFalse(success.restoreSession)
            assertEquals(1, fakeKeyRecord.callCount)
            coVerify(exactly = 1) { postLoginSideEffects.runOnSuccess("alice") }
        }

    @Test
    fun `AutoFromConnect rewrites password using credentialResolver`() =
        runTest {
            val (controller, fakeKeyRecord, fakeSync) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.ReadyForSync("resolved-pw"),
                    syncOutcome = SyncOutcome.Success,
                )
            val resolved = ResolvedCredentials(password = "resolved-pw", record = mockk(relaxed = true))
            every { credentialResolver.resolve("app-1", "alice", true) } returns resolved

            val request = manualRequest().copy(authSource = AuthSource.AutoFromConnect)
            val result = controller.performLogin(request, sink)

            assertTrue(result is LoginResult.Success)
            val success = result as LoginResult.Success
            assertTrue(success.connectManagedLogin)
            assertTrue(success.personalIdManagedLogin)
            assertEquals("alice", fakeSync.capturedUsername)
            assertEquals("resolved-pw", fakeSync.capturedPassword)
            assertEquals("resolved-pw", fakeKeyRecord.capturedRequest?.passwordOrPin)
            verify(exactly = 1) { credentialResolver.resolve("app-1", "alice", true) }
        }

    @Test
    fun `BadCredentials from key-record returns Failed without running side-effects`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.Failed(LoginError.BadCredentials),
                )

            val result = controller.performLogin(manualRequest(), sink)

            assertEquals(LoginResult.Failed(LoginError.BadCredentials), result)
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any()) }
        }

    @Test
    fun `NetworkUnavailable from key-record returns Failed`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.Failed(LoginError.NetworkUnavailable),
                )

            val result = controller.performLogin(manualRequest(), sink)

            assertEquals(LoginResult.Failed(LoginError.NetworkUnavailable), result)
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any()) }
        }

    @Test
    fun `TokenDenied from key-record returns Failed`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.Failed(LoginError.TokenDenied),
                )

            val result = controller.performLogin(manualRequest(), sink)

            assertEquals(LoginResult.Failed(LoginError.TokenDenied), result)
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any()) }
        }

    @Test
    fun `SyncFailed from sync after ReadyForSync returns Failed without side effects`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.ReadyForSync("secret"),
                    syncOutcome = SyncOutcome.Failed(LoginError.SyncFailed("SERVER_ERROR", "something went wrong")),
                )

            val result = controller.performLogin(manualRequest(), sink)

            assertEquals(
                LoginResult.Failed(LoginError.SyncFailed("SERVER_ERROR", "something went wrong")),
                result,
            )
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any()) }
        }

    @Test
    fun `PostLoginOutcome carries through to Success`() =
        runTest {
            val (controller, _, _) =
                buildController(keyRecordOutcome = KeyRecordOutcome.LocalLoginComplete)
            coEvery { postLoginSideEffects.runOnSuccess(any()) } returns
                PostLoginOutcome(redirectToConnectOpportunityInfo = true)

            val result = controller.performLogin(manualRequest(), sink)

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
            triggerMultipleUsersWarning = false,
            blockRemoteKeyManagement = false,
        )
}

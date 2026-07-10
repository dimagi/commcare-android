package org.commcare.login

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.connect.PersonalIdManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeKeyRecordOperations(
    private vararg val results: KeyRecordOutcome,
) : KeyRecordOperations(
        context = mockk(relaxed = true),
        app = mockk(relaxed = true),
    ) {
    var capturedRequest: LoginRequest? = null
    val capturedRequests = mutableListOf<LoginRequest>()
    var callCount = 0

    override suspend fun manageKeyRecord(
        request: LoginRequest,
        listener: LoginProgressListener,
    ): KeyRecordOutcome {
        capturedRequest = request
        capturedRequests += request
        val outcome = results[callCount.coerceAtMost(results.lastIndex)]
        callCount++
        return outcome
    }
}

private class FakeSyncOperations(
    private val result: SyncOutcome,
) : SyncOperations(context = mockk(relaxed = true)) {
    var capturedUsername: String? = null
    var capturedPassword: String? = null
    var capturedMode: DataPullMode? = null
    var callCount = 0

    override suspend fun pullData(
        username: String,
        password: String,
        mode: DataPullMode,
        listener: LoginProgressListener,
    ): SyncOutcome {
        capturedUsername = username
        capturedPassword = password
        capturedMode = mode
        callCount++
        return result
    }
}

class LoginControllerTest {
    private val credentialResolver = mockk<ConnectCredentialResolver>(relaxed = true)
    private val postLoginSideEffects = mockk<PostLoginSideEffects>(relaxed = true)
    private val personalIdManager = mockk<PersonalIdManager>(relaxed = true)
    private val listener = LoginProgressListener { }

    @Before
    fun setUp() {
        mockkStatic(PersonalIdManager::class)
        every { PersonalIdManager.getInstance() } returns personalIdManager
        every { personalIdManager.isloggedIn() } returns false
        coEvery {
            postLoginSideEffects.runOnSuccess(any())
        } returns PostLoginOutcome(false)
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
        keyRecordOutcomes: Array<KeyRecordOutcome> = arrayOf(keyRecordOutcome),
    ): Harness {
        val fakeKeyRecord = FakeKeyRecordOperations(*keyRecordOutcomes)
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

            val result = controller.performLogin(manualRequest(), listener)

            assertTrue(result is LoginResult.Success)
            val success = result as LoginResult.Success
            assertFalse(success.personalIdManagedLogin)
            assertEquals(LoginMode.PASSWORD, success.loginMode)
            assertFalse(success.restoreSession)
            assertEquals("secret", success.linkPassword)
            assertEquals(1, fakeKeyRecord.callCount)
            coVerify(exactly = 1) { postLoginSideEffects.runOnSuccess("alice") }
        }

    @Test
    fun `PersonalId rewrites password using credentialResolver`() =
        runTest {
            val (controller, fakeKeyRecord, fakeSync) =
                buildController(
                    keyRecordOutcomes =
                        arrayOf(
                            KeyRecordOutcome.ReadyForSync("resolved-pw"),
                            KeyRecordOutcome.LocalLoginComplete,
                        ),
                    syncOutcome = SyncOutcome.Success,
                )
            val resolved = mockk<ConnectLinkedAppRecord>()
            every { resolved.password } returns "resolved-pw"
            every {
                credentialResolver.resolve("app-1", "alice", true)
            } returns resolved

            val request =
                manualRequest().copy(
                    authSource = AuthSource.PersonalId,
                    passwordOrPin = "user-entered-pw",
                )
            val result = controller.performLogin(request, listener)

            assertTrue(result is LoginResult.Success)
            val success = result as LoginResult.Success
            assertTrue(success.personalIdManagedLogin)
            assertEquals("alice", fakeSync.capturedUsername)
            assertNotEquals("user-entered-pw", fakeSync.capturedPassword)
            assertEquals("resolved-pw", fakeSync.capturedPassword)
            assertEquals("resolved-pw", fakeKeyRecord.capturedRequests[0].passwordOrPin)
            assertEquals("resolved-pw", fakeKeyRecord.capturedRequests[1].passwordOrPin)
            assertEquals("resolved-pw", success.linkPassword)
            assertTrue(fakeKeyRecord.capturedRequests[1].blockRemoteKeyManagement)
            verify(exactly = 1) { credentialResolver.resolve("app-1", "alice", true) }
        }

    @Test
    fun `BadCredentials from key-record returns Failed without running side-effects`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.Failed(LoginError.BadCredentials),
                )

            val result = controller.performLogin(manualRequest(), listener)

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

            val result = controller.performLogin(manualRequest(), listener)

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

            val result = controller.performLogin(manualRequest(), listener)

            assertEquals(LoginResult.Failed(LoginError.TokenDenied), result)
            coVerify(exactly = 0) { postLoginSideEffects.runOnSuccess(any()) }
        }

    @Test
    fun `sync failure after ReadyForSync returns Failed without side effects`() =
        runTest {
            val (controller, _, _) =
                buildController(
                    keyRecordOutcome = KeyRecordOutcome.ReadyForSync("secret"),
                    syncOutcome =
                        SyncOutcome.Failed(
                            LoginError.UnknownFailure("something went wrong"),
                        ),
                )

            val result = controller.performLogin(manualRequest(), listener)

            assertEquals(
                LoginResult.Failed(LoginError.UnknownFailure("something went wrong")),
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

            val result = controller.performLogin(manualRequest(), listener)

            assertTrue(result is LoginResult.Success)
            assertEquals(true, (result as LoginResult.Success).postLoginOutcome.redirectToConnectOpportunityInfo)
        }

    @Test
    fun `post-success side effects run inside NonCancellable when parent scope is cancelled`() =
        runTest {
            val (controller, _, _) =
                buildController(keyRecordOutcome = KeyRecordOutcome.LocalLoginComplete)
            val sideEffectsStarted = CompletableDeferred<Unit>()
            val parentJobCancelled = CompletableDeferred<Unit>()
            val sideEffectsCompleted = CompletableDeferred<PostLoginOutcome>()
            coEvery { postLoginSideEffects.runOnSuccess("alice") } coAnswers {
                sideEffectsStarted.complete(Unit)
                parentJobCancelled.await()
                val outcome = PostLoginOutcome(redirectToConnectOpportunityInfo = false)
                sideEffectsCompleted.complete(outcome)
                outcome
            }

            val parentJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    controller.performLogin(manualRequest(), listener)
                }
            sideEffectsStarted.await()
            parentJob.cancel()
            parentJobCancelled.complete(Unit)

            assertEquals(
                PostLoginOutcome(redirectToConnectOpportunityInfo = false),
                sideEffectsCompleted.await(),
            )
            coVerify(exactly = 1) { postLoginSideEffects.runOnSuccess("alice") }
        }

    @Test
    fun `CONSUMER_APP happy path syncs with consumer-app mode then completes`() =
        runTest {
            val (controller, fakeKeyRecord, fakeSync) =
                buildController(
                    keyRecordOutcomes =
                        arrayOf(
                            KeyRecordOutcome.ReadyForSync("secret"),
                            KeyRecordOutcome.LocalLoginComplete,
                        ),
                    syncOutcome = SyncOutcome.Success,
                )

            val request = manualRequest().copy(dataPullMode = DataPullMode.CONSUMER_APP)
            val result = controller.performLogin(request, listener)

            assertTrue(result is LoginResult.Success)
            assertEquals(DataPullMode.CONSUMER_APP, fakeSync.capturedMode)
            assertEquals(1, fakeSync.callCount)
            assertEquals(2, fakeKeyRecord.callCount)
            assertTrue(fakeKeyRecord.capturedRequests[1].blockRemoteKeyManagement)
            coVerify(exactly = 1) { postLoginSideEffects.runOnSuccess("alice") }
        }

    @Test
    fun `CCZ_DEMO happy path syncs with demo mode then completes`() =
        runTest {
            val (controller, _, fakeSync) =
                buildController(
                    keyRecordOutcomes =
                        arrayOf(
                            KeyRecordOutcome.ReadyForSync("demo-user-password"),
                            KeyRecordOutcome.LocalLoginComplete,
                        ),
                    syncOutcome = SyncOutcome.Success,
                )

            val request =
                manualRequest(username = "demo_user").copy(
                    dataPullMode = DataPullMode.CCZ_DEMO,
                    blockRemoteKeyManagement = true,
                )
            val result = controller.performLogin(request, listener)

            assertTrue(result is LoginResult.Success)
            assertEquals(DataPullMode.CCZ_DEMO, fakeSync.capturedMode)
            assertEquals("demo_user", fakeSync.capturedUsername)
            assertEquals("demo-user-password", fakeSync.capturedPassword)
            coVerify(exactly = 1) { postLoginSideEffects.runOnSuccess("demo_user") }
        }

    @Test
    fun `CONSUMER_APP local login without sync skips pullData`() =
        runTest {
            val (controller, _, fakeSync) =
                buildController(keyRecordOutcome = KeyRecordOutcome.LocalLoginComplete)

            val request = manualRequest().copy(dataPullMode = DataPullMode.CONSUMER_APP)
            val result = controller.performLogin(request, listener)

            assertTrue(result is LoginResult.Success)
            assertEquals(0, fakeSync.callCount)
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

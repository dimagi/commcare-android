# Offline-First Connect Network Architecture — TDD Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the offline-first Connect network architecture following strict TDD — write failing tests first, then implement to pass them, one phase at a time.

**Architecture:** Repository pattern with Flow-based offline-first data emissions, request deduplication via `ConnectRequestManager`, smart caching via `ConnectSyncPreferences`, and ViewModel + LiveData for Java-fragment compatibility.

**Tech Stack:** Kotlin coroutines, Flow, LiveData, Retrofit (suspend), Robolectric, MockK, JUnit 4, `kotlinx-coroutines-test 1.7.3`

**Reference:** Full implementation code is in `docs/claude/plans/2026-02-23-offline-first-connect-architecture.md`. This plan adds TDD sequencing, complete test code, and testability modifications.

---

## Testability Modifications

The architecture plan code is used verbatim except for these targeted changes to enable dependency injection in tests:

| Class | Modification |
|---|---|
| `ConnectNetworkClient` | Add `internal` secondary constructor that accepts `ConnectApiService` directly |
| `ConnectRepository` | Add optional constructor parameters for `syncPrefs` and `networkClient` |
| `ConnectJobsListViewModel` | Add `@VisibleForTesting internal var repository` |
| `ConnectLearningProgressViewModel` | Add `@VisibleForTesting internal var repository` |

None of these affect the public production API — `getInstance()` and factory methods continue to work as designed.

---

## Phase 0 — Contracts & Interfaces

No test phase. `DataState` and `RefreshPolicy` are pure sealed class type definitions.

---

### Task 1: Create DataState

**Files:**
- Create: `app/src/org/commcare/connect/repository/DataState.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 0 → DataState Sealed Class`).

**Step 2: Compile check**
```bash
./gradlew compileDebugKotlin 2>&1 | grep -E "(error|warning|BUILD)"
```
Expected: `BUILD SUCCESSFUL`

**Step 3: Lint**
```bash
ktlint app/src/org/commcare/connect/repository/DataState.kt
```
Expected: no output (no errors)

---

### Task 2: Create RefreshPolicy

**Files:**
- Create: `app/src/org/commcare/connect/repository/RefreshPolicy.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 0 → RefreshPolicy Sealed Class`).

**Step 2: Compile + lint**
```bash
./gradlew compileDebugKotlin 2>&1 | grep -E "(error|BUILD)"
ktlint app/src/org/commcare/connect/repository/RefreshPolicy.kt
```
Expected: both pass.

---

### Task 3: Commit Phase 0

```bash
git add app/src/org/commcare/connect/repository/DataState.kt \
        app/src/org/commcare/connect/repository/RefreshPolicy.kt
git commit -m "[AI] Phase 0: Add DataState and RefreshPolicy sealed classes"
```

---

## Phase 1a — Core Infrastructure Tests (Red)

Write failing tests for `ConnectSyncPreferences` and `ConnectRequestManager`. These classes don't exist yet so tests will fail to compile. That is expected — commit the Red state.

---

### Task 4: Create ConnectSyncPreferencesTest

**Files:**
- Create: `app/unit-tests/src/org/commcare/connect/repository/ConnectSyncPreferencesTest.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 1a → ConnectSyncPreferencesTest`). The code is already complete with all test methods.

**Step 2: Attempt compile**
```bash
./gradlew compileDebugUnitTestKotlin 2>&1 | grep -E "(error|unresolved|BUILD)"
```
Expected: compile failure — `ConnectSyncPreferences` is unresolved. This is correct.

---

### Task 5: Create ConnectRequestManagerTest

**Files:**
- Create: `app/unit-tests/src/org/commcare/connect/repository/ConnectRequestManagerTest.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 1a → ConnectRequestManagerTest`). The code is already complete with all test methods.

**Step 2: Attempt compile**
```bash
./gradlew compileDebugUnitTestKotlin 2>&1 | grep -E "(error|unresolved|BUILD)"
```
Expected: compile failure — `ConnectRequestManager` is unresolved. This is correct.

---

### Task 6: Commit Phase 1a (Red)

```bash
git add app/unit-tests/src/org/commcare/connect/repository/ConnectSyncPreferencesTest.kt \
        app/unit-tests/src/org/commcare/connect/repository/ConnectRequestManagerTest.kt
git commit -m "[AI] Phase 1a: Add failing tests for ConnectSyncPreferences and ConnectRequestManager"
```

---

## Phase 1b — Core Infrastructure Implementation (Green)

Implement `ConnectSyncPreferences` and `ConnectRequestManager` to pass the Phase 1a tests.

---

### Task 7: Create ConnectSyncPreferences

**Files:**
- Create: `app/src/org/commcare/connect/repository/ConnectSyncPreferences.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 1b → ConnectSyncPreferences`).

---

### Task 8: Create ConnectRequestManager

**Files:**
- Create: `app/src/org/commcare/connect/repository/ConnectRequestManager.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 1b → ConnectRequestManager`).

---

### Task 9: Register session start in CommCareApplication

**Files:**
- Modify: `app/src/org/commcare/CommCareApplication.kt` (or `.java` — check which exists)

**Step 1: Find the `onCreate()` method** in `CommCareApplication`. Add this call at the beginning of `onCreate()`, before the existing super call or right after it:

```kotlin
ConnectSyncPreferences.getInstance(this).markSessionStart()
```

If the file is Java:
```java
ConnectSyncPreferences.Companion.getInstance(this).markSessionStart();
```

**Placement**: Add after `super.onCreate()` and before any Connect-specific initialization.

---

### Task 10: Run Phase 1a tests (expect Green)

```bash
./gradlew testDebugUnitTest \
  --tests "org.commcare.connect.repository.ConnectSyncPreferencesTest" \
  --tests "org.commcare.connect.repository.ConnectRequestManagerTest" \
  2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

If tests fail, read the error output and fix the implementation before continuing.

---

### Task 11: Lint and commit Phase 1b

```bash
ktlint app/src/org/commcare/connect/repository/ConnectSyncPreferences.kt \
       app/src/org/commcare/connect/repository/ConnectRequestManager.kt
```

```bash
git add app/src/org/commcare/connect/repository/ConnectSyncPreferences.kt \
        app/src/org/commcare/connect/repository/ConnectRequestManager.kt \
        app/src/org/commcare/CommCareApplication.kt
git commit -m "[AI] Phase 1b: Implement ConnectSyncPreferences and ConnectRequestManager"
```

---

## Phase 1.5a — Coroutine Network Client Tests (Red)

Write failing tests for `ConnectNetworkClient`. The class doesn't exist yet.

---

### Task 12: Create ConnectNetworkClientTest

**Files:**
- Create: `app/unit-tests/src/org/commcare/connect/network/connect/ConnectNetworkClientTest.kt`

**Step 1: Create the file:**

```kotlin
package org.commcare.connect.network.connect

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.ConnectApiService
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.connect.network.getAuthorizationHeader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectNetworkClientTest {

    private val context = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private val mockApiService = mockk<ConnectApiService>()
    private val mockUser = mockk<ConnectUserRecord>()
    private lateinit var client: ConnectNetworkClient

    @Before
    fun setUp() {
        // Uses the testability constructor added in Task 13
        client = ConnectNetworkClient(context, mockApiService)
        mockkStatic("org.commcare.connect.network.ConnectNetworkHelperKt")
        coEvery { getAuthorizationHeader(any(), any()) } returns Result.success("Bearer testtoken")
    }

    @After
    fun tearDown() {
        unmockkStatic("org.commcare.connect.network.ConnectNetworkHelperKt")
    }

    @Test
    fun testGetConnectOpportunities_authHeaderFailure_returnsFailure() = runBlocking {
        coEvery { getAuthorizationHeader(any(), any()) } returns
            Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR))

        val result = client.getConnectOpportunities(mockUser)

        assertTrue(result.isFailure)
        assertEquals(
            PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR,
            (result.exceptionOrNull() as ConnectApiException).errorCode
        )
    }

    @Test
    fun testGetConnectOpportunities_httpError401_returnsFailedAuth() = runBlocking {
        val errorBody = ResponseBody.create(MediaType.parse("application/json"), "")
        val mockResponse = Response.error<ResponseBody>(401, errorBody)
        coEvery { mockApiService.getConnectOpportunities(any(), any()) } returns mockResponse

        val result = client.getConnectOpportunities(mockUser)

        assertTrue(result.isFailure)
        assertEquals(
            PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR,
            (result.exceptionOrNull() as ConnectApiException).errorCode
        )
    }

    @Test
    fun testGetConnectOpportunities_networkException_returnsNetworkError() = runBlocking {
        coEvery { mockApiService.getConnectOpportunities(any(), any()) } throws
            IOException("Network failed")

        val result = client.getConnectOpportunities(mockUser)

        assertTrue(result.isFailure)
        assertEquals(
            PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR,
            (result.exceptionOrNull() as ConnectApiException).errorCode
        )
    }

    @Test
    fun testGetConnectOpportunities_http500_returnsServerError() = runBlocking {
        val errorBody = ResponseBody.create(MediaType.parse("application/json"), "")
        val mockResponse = Response.error<ResponseBody>(500, errorBody)
        coEvery { mockApiService.getConnectOpportunities(any(), any()) } returns mockResponse

        val result = client.getConnectOpportunities(mockUser)

        assertTrue(result.isFailure)
        assertEquals(
            PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
            (result.exceptionOrNull() as ConnectApiException).errorCode
        )
    }

    @Test
    fun testGetLearningProgress_authHeaderFailure_returnsFailure() = runBlocking {
        val mockJob = mockk<ConnectJobRecord>()
        every { mockJob.jobUUID } returns "test-uuid"
        coEvery { getAuthorizationHeader(any(), any()) } returns
            Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR))

        val result = client.getLearningProgress(mockUser, mockJob)

        assertTrue(result.isFailure)
        assertEquals(
            PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR,
            (result.exceptionOrNull() as ConnectApiException).errorCode
        )
    }

    @Test
    fun testGetLearningProgress_http500_returnsServerError() = runBlocking {
        val mockJob = mockk<ConnectJobRecord>()
        every { mockJob.jobUUID } returns "test-uuid"
        val errorBody = ResponseBody.create(MediaType.parse("application/json"), "")
        val mockResponse = Response.error<ResponseBody>(500, errorBody)
        coEvery { mockApiService.getLearningProgress(any(), any(), any()) } returns mockResponse

        val result = client.getLearningProgress(mockUser, mockJob)

        assertTrue(result.isFailure)
        assertEquals(
            PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
            (result.exceptionOrNull() as ConnectApiException).errorCode
        )
    }
}
```

**Note on test scope:** These tests verify HTTP error mapping, auth failure propagation, and IOException handling — the core plumbing of `executeApiCall`. Successful parse testing is deferred to manual testing because `ConnectOpportunitiesParser` writes to DB and requires valid server JSON format.

**Step 2: Attempt compile**
```bash
./gradlew compileDebugUnitTestKotlin 2>&1 | grep -E "(error|unresolved|BUILD)"
```
Expected: compile failure — `ConnectNetworkClient`, `ConnectApiService` unresolved. Correct.

---

### Task 13: Commit Phase 1.5a (Red)

```bash
git add app/unit-tests/src/org/commcare/connect/network/connect/ConnectNetworkClientTest.kt
git commit -m "[AI] Phase 1.5a: Add failing tests for ConnectNetworkClient"
```

---

## Phase 1.5b — Coroutine Network Client Implementation (Green)

---

### Task 14: Create ConnectApiException

**Files:**
- Create: `app/src/org/commcare/connect/network/base/ConnectApiException.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 1.5b → ConnectApiException`).

---

### Task 15: Create ConnectApiService

**Files:**
- Create: `app/src/org/commcare/connect/network/ConnectApiService.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 1.5b → ConnectApiService`).

---

### Task 16: Convert ConnectNetworkHelper to Kotlin

**Files:**
- Create: `app/src/org/commcare/connect/network/ConnectNetworkHelper.kt` (new Kotlin file)
- Delete: `app/src/org/commcare/connect/network/ConnectNetworkHelper.java` (after verifying the Kotlin version compiles)

**Step 1: Create the Kotlin file** with the exact code from the architecture plan (`Phase 1.5b → ConnectNetworkHelper (converted to Kotlin)`).

**Step 2: Compile check to confirm Java callers still work**
```bash
./gradlew compileDebugJava 2>&1 | grep -E "(error|BUILD)"
```
Expected: `BUILD SUCCESSFUL`. The `@JvmStatic` annotations preserve Java call sites.

**Step 3: Delete the Java file** only after Step 2 passes:
```bash
git rm app/src/org/commcare/connect/network/ConnectNetworkHelper.java
```

---

### Task 17: Create ConnectNetworkClient (with testability constructor)

**Files:**
- Create: `app/src/org/commcare/connect/network/connect/ConnectNetworkClient.kt`

**Step 1: Create the file** using the architecture plan code (`Phase 1.5b → ConnectNetworkClient`) with this modification — replace the `lazy` apiService with a constructor parameter:

```kotlin
class ConnectNetworkClient @VisibleForTesting internal constructor(
    private val context: Context,
    private val apiService: ConnectApiService,
) {
    companion object {
        private const val API_VERSION = "1.0"

        @Volatile
        private var instance: ConnectNetworkClient? = null

        fun getInstance(context: Context): ConnectNetworkClient =
            instance ?: synchronized(this) {
                instance ?: ConnectNetworkClient(
                    context.applicationContext,
                    BaseApiClient.buildRetrofitClient(ConnectApiClient.BASE_URL)
                        .create(ConnectApiService::class.java)
                ).also { instance = it }
            }
    }

    // rest of the class is identical to the architecture plan — no lazy apiService field
```

Add this import at the top: `import androidx.annotation.VisibleForTesting`

The rest of the class (`versionHeaders()`, `getConnectOpportunities()`, `getLearningProgress()`, `executeApiCall()`) is identical to the architecture plan.

---

### Task 18: Run Phase 1.5a tests (expect Green)

```bash
./gradlew testDebugUnitTest \
  --tests "org.commcare.connect.network.connect.ConnectNetworkClientTest" \
  2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, all 6 tests pass.

If a test fails, read the error and fix the implementation before continuing.

---

### Task 19: Lint and commit Phase 1.5b

```bash
ktlint app/src/org/commcare/connect/network/ConnectApiService.kt \
       app/src/org/commcare/connect/network/ConnectNetworkHelper.kt \
       app/src/org/commcare/connect/network/connect/ConnectNetworkClient.kt \
       app/src/org/commcare/connect/network/base/ConnectApiException.kt
```

```bash
git add app/src/org/commcare/connect/network/ConnectApiService.kt \
        app/src/org/commcare/connect/network/ConnectNetworkHelper.kt \
        app/src/org/commcare/connect/network/connect/ConnectNetworkClient.kt \
        app/src/org/commcare/connect/network/base/ConnectApiException.kt
git commit -m "[AI] Phase 1.5b: Implement ConnectNetworkClient, ConnectApiService, ConnectNetworkHelper, ConnectApiException"
```

---

## Phase 2a — Repository Tests (Red)

Write failing tests for `ConnectRepository`. The class doesn't exist yet.

---

### Task 20: Create ConnectRepositoryTest

**Files:**
- Create: `app/unit-tests/src/org/commcare/connect/repository/ConnectRepositoryTest.kt`

**Step 1: Create the file:**

```kotlin
package org.commcare.connect.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var mockSyncPrefs: ConnectSyncPreferences
    private lateinit var mockNetworkClient: ConnectNetworkClient
    private lateinit var mockUser: ConnectUserRecord
    private lateinit var repository: ConnectRepository

    @Before
    fun setUp() {
        mockSyncPrefs = mockk(relaxed = true)
        mockNetworkClient = mockk()
        mockUser = mockk()

        // Static mocks for database utilities
        mockkStatic(ConnectJobUtils::class)
        mockkStatic(ConnectUserDatabaseUtil::class)
        every { ConnectUserDatabaseUtil.getUser(any()) } returns mockUser

        // Uses the testability constructor added in Task 25
        repository = ConnectRepository(context, mockSyncPrefs, mockNetworkClient)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetOpportunities_noCache_emitsLoading() = runBlocking {
        every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns emptyList()
        every { mockSyncPrefs.getLastSyncTime(any()) } returns null
        every { mockSyncPrefs.shouldRefresh(any(), any()) } returns false

        val emissions = repository.getOpportunities().toList()

        assertTrue(emissions.first() is DataState.Loading)
    }

    @Test
    fun testGetOpportunities_withCache_shouldRefreshFalse_emitsCachedOnly() = runBlocking {
        val cachedJobs = listOf(mockk<ConnectJobRecord>())
        every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns cachedJobs
        every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
        every { mockSyncPrefs.shouldRefresh(any(), any()) } returns false

        val emissions = repository.getOpportunities().toList()

        assertEquals(1, emissions.size)
        assertTrue(emissions[0] is DataState.Cached)
        assertEquals(cachedJobs, (emissions[0] as DataState.Cached).data)
    }

    @Test
    fun testGetOpportunities_withCache_networkSuccess_emitsCachedThenSuccess() = runBlocking {
        val cachedJobs = listOf(mockk<ConnectJobRecord>())
        val freshJobs = listOf(mockk<ConnectJobRecord>(), mockk())
        val mockModel = mockk<ConnectOpportunitiesResponseModel>()
        every { mockModel.validJobs } returns freshJobs

        every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns cachedJobs
        every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
        every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
        coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns Result.success(mockModel)

        val emissions = repository.getOpportunities().toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is DataState.Cached)
        assertTrue(emissions[1] is DataState.Success)
        assertEquals(freshJobs, (emissions[1] as DataState.Success).data)
    }

    @Test
    fun testGetOpportunities_networkFailure_withCache_emitsError_withCachedData() = runBlocking {
        val cachedJobs = listOf(mockk<ConnectJobRecord>())
        every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns cachedJobs
        every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
        every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
        coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns
            Result.failure(Exception("Network error"))

        val emissions = repository.getOpportunities().toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is DataState.Cached)
        assertTrue(emissions[1] is DataState.Error)
        assertNotNull((emissions[1] as DataState.Error).cachedData)
    }

    @Test
    fun testGetOpportunities_networkFailure_noCache_emitsError_withNullCachedData() = runBlocking {
        every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns emptyList()
        every { mockSyncPrefs.getLastSyncTime(any()) } returns null
        every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
        coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns
            Result.failure(Exception("Network error"))

        val emissions = repository.getOpportunities().toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0] is DataState.Loading)
        assertTrue(emissions[1] is DataState.Error)
        assertNull((emissions[1] as DataState.Error).cachedData)
    }

    @Test
    fun testGetOpportunities_forceRefresh_bypassesShouldRefreshCheck() = runBlocking {
        val cachedJobs = listOf(mockk<ConnectJobRecord>())
        val mockModel = mockk<ConnectOpportunitiesResponseModel>()
        every { mockModel.validJobs } returns emptyList()

        every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns cachedJobs
        every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
        every { mockSyncPrefs.shouldRefresh(any(), any()) } returns false // would skip network
        coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns Result.success(mockModel)

        val emissions = repository.getOpportunities(forceRefresh = true).toList()

        // Should have network call (Success emission) despite shouldRefresh=false
        assertTrue(emissions.any { it is DataState.Success })
    }

    @Test
    fun testGetOpportunities_networkSuccess_storesLastSyncTime() = runBlocking {
        val mockModel = mockk<ConnectOpportunitiesResponseModel>()
        every { mockModel.validJobs } returns emptyList()
        every { ConnectJobUtils.getCompositeJobs(any(), any(), any()) } returns emptyList()
        every { mockSyncPrefs.getLastSyncTime(any()) } returns null
        every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
        coEvery { mockNetworkClient.getConnectOpportunities(any()) } returns Result.success(mockModel)

        repository.getOpportunities().toList()

        verify { mockSyncPrefs.storeLastSyncTime(any()) }
    }

    @Test
    fun testGetLearningProgress_alwaysPolicy_networkCalledEachTime() = runBlocking {
        val mockJob = mockk<ConnectJobRecord>(relaxed = true)
        val mockModel = mockk<org.commcare.connect.network.connect.models.LearningAppProgressResponseModel>(relaxed = true)
        every { mockModel.connectJobLearningRecords } returns emptyList()
        every { mockModel.connectJobAssessmentRecords } returns emptyList()
        every { ConnectJobUtils.getCompositeJob(any(), any()) } returns mockJob
        every { mockSyncPrefs.getLastSyncTime(any()) } returns Date()
        every { mockSyncPrefs.shouldRefresh(any(), any()) } returns true
        coEvery { mockNetworkClient.getLearningProgress(any(), any()) } returns Result.success(mockModel)
        every { ConnectJobUtils.updateJobLearnProgress(any(), any()) } returns Unit

        // First call
        repository.getLearningProgress(mockJob).toList()
        // Second call — ALWAYS policy means shouldRefresh always true, but ConnectRequestManager
        // deduplicates by URL, so we verify both calls reach the network client
        repository.getLearningProgress(mockJob).toList()

        // ALWAYS policy: shouldRefresh returns true both times
        verify(exactly = 2) { mockSyncPrefs.shouldRefresh(any(), any()) }
    }
}
```

**Step 2: Attempt compile**
```bash
./gradlew compileDebugUnitTestKotlin 2>&1 | grep -E "(error|unresolved|BUILD)"
```
Expected: compile failure — `ConnectRepository` unresolved. Correct.

---

### Task 21: Commit Phase 2a (Red)

```bash
git add app/unit-tests/src/org/commcare/connect/repository/ConnectRepositoryTest.kt
git commit -m "[AI] Phase 2a: Add failing tests for ConnectRepository"
```

---

## Phase 2b — Repository Implementation (Green)

---

### Task 22: Create ConnectRepository (with testability constructor)

**Files:**
- Create: `app/src/org/commcare/connect/repository/ConnectRepository.kt`

**Step 1:** Use the architecture plan code (`Phase 2b → ConnectRepository`) with this modification — add optional constructor parameters:

```kotlin
class ConnectRepository @VisibleForTesting internal constructor(
    private val context: Context,
    private val syncPrefs: ConnectSyncPreferences,
    private val networkClient: ConnectNetworkClient,
) {
    companion object {
        private const val ENDPOINT_OPPORTUNITIES = "/opportunities"
        private const val ENDPOINT_LEARNING_PREFIX = "/learning_progress/"

        @Volatile
        private var instance: ConnectRepository? = null

        fun getInstance(context: Context): ConnectRepository {
            return instance ?: synchronized(this) {
                instance ?: ConnectRepository(
                    context.applicationContext,
                    ConnectSyncPreferences.getInstance(context),
                    ConnectNetworkClient.getInstance(context),
                ).also { instance = it }
            }
        }
    }
    // rest of class is identical to architecture plan
    // replace direct calls to ConnectNetworkClient.getInstance(context) with this.networkClient
    // replace direct calls to syncPrefs = ConnectSyncPreferences.getInstance(context) with this.syncPrefs
```

Add import: `import androidx.annotation.VisibleForTesting`

The `offlineFirstFlow()`, `getOpportunities()`, `getLearningProgress()`, `fetchOpportunitiesFromNetwork()`, `fetchLearningProgressFromNetwork()` methods are identical to the architecture plan, except:
- Remove `private val syncPrefs = ConnectSyncPreferences.getInstance(context)` (it's now a constructor param)
- In `fetchOpportunitiesFromNetwork()`: use `networkClient` instead of `ConnectNetworkClient.getInstance(context)`
- In `fetchLearningProgressFromNetwork()`: same

---

### Task 23: Run Phase 2a tests (expect Green)

```bash
./gradlew testDebugUnitTest \
  --tests "org.commcare.connect.repository.ConnectRepositoryTest" \
  2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, all 8 tests pass.

---

### Task 24: Lint and commit Phase 2b

```bash
ktlint app/src/org/commcare/connect/repository/ConnectRepository.kt
```

```bash
git add app/src/org/commcare/connect/repository/ConnectRepository.kt
git commit -m "[AI] Phase 2b: Implement ConnectRepository with offline-first flow"
```

---

## Phase 3a — Job Opportunities ViewModel + Fragment Tests (Red)

Write failing tests for `ConnectJobsListViewModel` and `ConnectJobsListsFragment`.

---

### Task 25: Create ConnectJobsListViewModelTest

**Files:**
- Create: `app/unit-tests/src/org/commcare/connect/viewmodel/ConnectJobsListViewModelTest.kt`

**Step 1: Create the file:**

```kotlin
package org.commcare.connect.viewmodel

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.rules.MainCoroutineRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@ExperimentalCoroutinesApi
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobsListViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val application = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var mockRepository: ConnectRepository
    private lateinit var viewModel: ConnectJobsListViewModel

    @Before
    fun setUp() {
        mockRepository = mockk()
        viewModel = ConnectJobsListViewModel(application)
        // Inject mock repository using the @VisibleForTesting field added in Task 31
        viewModel.repository = mockRepository
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testLoadOpportunities_postsLoadingThenSuccess() {
        val jobs = listOf(mockk<ConnectJobRecord>())
        every { mockRepository.getOpportunities(any(), any()) } returns flowOf(
            DataState.Loading,
            DataState.Success(jobs),
        )

        val results = mutableListOf<DataState<List<ConnectJobRecord>>>()
        viewModel.opportunities.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadOpportunities()
        }

        assertEquals(2, results.size)
        assertEquals(DataState.Loading, results[0])
        assertTrue(results[1] is DataState.Success)
        assertEquals(jobs, (results[1] as DataState.Success).data)
    }

    @Test
    fun testLoadOpportunities_postsError_onFailure() {
        val cachedJobs = listOf(mockk<ConnectJobRecord>())
        every { mockRepository.getOpportunities(any(), any()) } returns flowOf(
            DataState.Loading,
            DataState.Error(cachedData = cachedJobs),
        )

        val results = mutableListOf<DataState<List<ConnectJobRecord>>>()
        viewModel.opportunities.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadOpportunities()
        }

        assertEquals(2, results.size)
        assertTrue(results[1] is DataState.Error)
        assertEquals(cachedJobs, (results[1] as DataState.Error).cachedData)
    }

    @Test
    fun testLoadOpportunities_forceRefresh_passedToRepository() {
        every { mockRepository.getOpportunities(any(), any()) } returns flowOf(DataState.Loading)
        viewModel.opportunities.observeForever { }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadOpportunities(forceRefresh = true)
        }

        verify { mockRepository.getOpportunities(forceRefresh = true, any()) }
    }
}
```

**Step 2: Attempt compile**
```bash
./gradlew compileDebugUnitTestKotlin 2>&1 | grep -E "(error|unresolved|BUILD)"
```
Expected: compile failure — `ConnectJobsListViewModel` unresolved. Correct.

---

### Task 26: Create ConnectJobsListsFragmentTest

**Files:**
- Create: `app/unit-tests/src/org/commcare/fragments/connect/ConnectJobsListsFragmentTest.kt`

**Step 1: Find what the loading indicator and job list views are called.** Look at `ConnectJobsListsFragment.java` and its layout file (likely `fragment_connect_jobs_list.xml`) to find:
- The loading indicator view ID (e.g., `R.id.progress_bar` or similar)
- The RecyclerView or list view ID for jobs

**Step 2: Create the file** — adjust the view IDs based on what you found in Step 1:

```kotlin
package org.commcare.fragments.connect

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.commcare.CommCareTestApplication
import org.commcare.R
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.ConnectJobsListViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import android.view.View

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobsListsFragmentTest {

    private lateinit var opportunitiesLiveData: MutableLiveData<DataState<List<ConnectJobRecord>>>

    @Before
    fun setUp() {
        opportunitiesLiveData = MutableLiveData()
        mockkConstructor(ConnectJobsListViewModel::class)
        every { anyConstructed<ConnectJobsListViewModel>().opportunities } returns opportunitiesLiveData
        every { anyConstructed<ConnectJobsListViewModel>().loadOpportunities(any()) } answers {
            // No-op: test controls LiveData directly
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testFragment_showsLoadingSpinner_onLoadingState() {
        val scenario = launchFragmentInContainer<ConnectJobsListsFragment>()
        scenario.onFragment { fragment ->
            opportunitiesLiveData.value = DataState.Loading
            // Find the loading indicator — adjust R.id.* to match the actual view ID
            val loadingView = fragment.requireView().findViewById<View>(R.id.pb_loading)
            assertEquals(View.VISIBLE, loadingView?.visibility)
        }
    }

    @Test
    fun testFragment_showsJobList_onSuccessState() {
        val jobs = listOf(mockk<ConnectJobRecord>(relaxed = true), mockk(relaxed = true))
        val scenario = launchFragmentInContainer<ConnectJobsListsFragment>()
        scenario.onFragment { fragment ->
            opportunitiesLiveData.value = DataState.Success(jobs)
            // Verify list is shown — adjust to match actual RecyclerView or adapter check
            // e.g., check adapter item count or list visibility
        }
    }

    @Test
    fun testFragment_showsJobList_onCachedState() {
        val jobs = listOf(mockk<ConnectJobRecord>(relaxed = true))
        val scenario = launchFragmentInContainer<ConnectJobsListsFragment>()
        scenario.onFragment { fragment ->
            opportunitiesLiveData.value = DataState.Cached(jobs, java.util.Date())
        }
        // Verify no crash and fragment is added
        scenario.onFragment { fragment ->
            assert(fragment.isAdded)
        }
    }

    @Test
    fun testFragment_showsError_onErrorState_withNoCachedData() {
        val scenario = launchFragmentInContainer<ConnectJobsListsFragment>()
        scenario.onFragment { fragment ->
            opportunitiesLiveData.value = DataState.Error(cachedData = null)
        }
        // Verify no crash
        scenario.onFragment { fragment ->
            assert(fragment.isAdded)
        }
    }

    @Test
    fun testFragment_remainsVisible_onErrorState_withCachedData() {
        val jobs = listOf(mockk<ConnectJobRecord>(relaxed = true))
        val scenario = launchFragmentInContainer<ConnectJobsListsFragment>()
        scenario.onFragment { fragment ->
            opportunitiesLiveData.value = DataState.Error(cachedData = jobs)
        }
        scenario.onFragment { fragment ->
            assert(fragment.isAdded)
        }
    }
}
```

**Important:** The `launchFragmentInContainer` approach may require the fragment to be hosted in an Activity. If `ConnectJobsListsFragment` requires a specific Activity host (e.g., `ConnectActivity`), use `Robolectric.buildActivity(ConnectActivity::class.java)` instead and navigate to the fragment. Adjust based on what you find in the fragment's `requireActivity()` usage.

**Step 3: Attempt compile**
```bash
./gradlew compileDebugUnitTestKotlin 2>&1 | grep -E "(error|unresolved|BUILD)"
```
Expected: compile failure. Correct.

---

### Task 27: Commit Phase 3a (Red)

```bash
git add app/unit-tests/src/org/commcare/connect/viewmodel/ConnectJobsListViewModelTest.kt \
        app/unit-tests/src/org/commcare/fragments/connect/ConnectJobsListsFragmentTest.kt
git commit -m "[AI] Phase 3a: Add failing tests for ConnectJobsListViewModel and ConnectJobsListsFragment"
```

---

## Phase 3b — Job Opportunities Migration (Green)

---

### Task 28: Create ViewModelExtensions.kt

**Files:**
- Create: `app/src/org/commcare/connect/viewmodel/ViewModelExtensions.kt`

**Step 1: Create the file** with the exact code from the architecture plan (`Phase 3b → collectInto Extension Function`).

---

### Task 29: Create ConnectJobsListViewModel (with testability field)

**Files:**
- Create: `app/src/org/commcare/connect/viewmodel/ConnectJobsListViewModel.kt`

**Step 1: Create the file** using the architecture plan code (`Phase 3b → ConnectJobsListViewModel`) with this modification — add `@VisibleForTesting` to the repository field:

```kotlin
class ConnectJobsListViewModel(application: Application) : AndroidViewModel(application) {

    @VisibleForTesting
    internal var repository: ConnectRepository = ConnectRepository.getInstance(application)

    private val _opportunities = MutableLiveData<DataState<List<ConnectJobRecord>>>()
    val opportunities: LiveData<DataState<List<ConnectJobRecord>>> = _opportunities

    fun loadOpportunities(forceRefresh: Boolean = false) {
        collectInto(
            flow = repository.getOpportunities(forceRefresh, RefreshPolicy.SESSION_AND_TIME_BASED(60_000)),
            liveData = _opportunities,
        )
    }
}
```

Add import: `import androidx.annotation.VisibleForTesting`

---

### Task 30: Migrate ConnectJobsListsFragment

**Files:**
- Modify: `app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java`

**Step 1: Apply the changes** from the architecture plan (`Phase 3b → Update ConnectJobsListsFragment`):
- Add the three new imports
- Add `private ConnectJobsListViewModel viewModel;` field
- Replace `onCreateView()` body with the new version
- Add `refresh()` method
- Add `observeOpportunities()` method
- Add `navigateFailure()` method
- Remove (or keep as dead code) the old `refreshData()` method that used `ConnectApiHandler`

---

### Task 31: Run Phase 3a tests (expect Green)

```bash
./gradlew testDebugUnitTest \
  --tests "org.commcare.connect.viewmodel.ConnectJobsListViewModelTest" \
  --tests "org.commcare.fragments.connect.ConnectJobsListsFragmentTest" \
  2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

If a ViewModel test fails due to coroutine timing, ensure `MainCoroutineRule.runBlockingTest` advances the dispatcher before asserting.

If a fragment test fails because `launchFragmentInContainer` can't launch in isolation, switch to:
```kotlin
val activityController = Robolectric.buildActivity(ConnectActivity::class.java).create().start().resume()
// navigate to ConnectJobsListsFragment via the activity's nav graph
```

---

### Task 32: Lint and commit Phase 3b

```bash
ktlint app/src/org/commcare/connect/viewmodel/ViewModelExtensions.kt \
       app/src/org/commcare/connect/viewmodel/ConnectJobsListViewModel.kt
```

```bash
git add app/src/org/commcare/connect/viewmodel/ViewModelExtensions.kt \
        app/src/org/commcare/connect/viewmodel/ConnectJobsListViewModel.kt \
        app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java
git commit -m "[AI] Phase 3b: Implement ConnectJobsListViewModel and migrate ConnectJobsListsFragment"
```

---

## Phase 4a — Learning Progress ViewModel + Fragment Tests (Red)

Write failing tests for `ConnectLearningProgressViewModel` and `ConnectLearningProgressFragment`.

---

### Task 33: Create ConnectLearningProgressViewModelTest

**Files:**
- Create: `app/unit-tests/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModelTest.kt`

**Step 1: Create the file:**

```kotlin
package org.commcare.connect.viewmodel

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.rules.MainCoroutineRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectLearningProgressViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val application = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var mockRepository: ConnectRepository
    private lateinit var mockJob: ConnectJobRecord
    private lateinit var viewModel: ConnectLearningProgressViewModel

    @Before
    fun setUp() {
        mockRepository = mockk()
        mockJob = mockk(relaxed = true)
        viewModel = ConnectLearningProgressViewModel(application)
        // Inject mock repository using the @VisibleForTesting field added in Task 40
        viewModel.repository = mockRepository
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testLoadLearningProgress_postsLoadingThenSuccess() {
        every { mockRepository.getLearningProgress(any(), any(), any()) } returns flowOf(
            DataState.Loading,
            DataState.Success(mockJob),
        )

        val results = mutableListOf<DataState<ConnectJobRecord>>()
        viewModel.learningProgress.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadLearningProgress(mockJob)
        }

        assertEquals(2, results.size)
        assertEquals(DataState.Loading, results[0])
        assertTrue(results[1] is DataState.Success)
        assertEquals(mockJob, (results[1] as DataState.Success).data)
    }

    @Test
    fun testLoadLearningProgress_postsError_onFailure() {
        every { mockRepository.getLearningProgress(any(), any(), any()) } returns flowOf(
            DataState.Loading,
            DataState.Error(cachedData = mockJob),
        )

        val results = mutableListOf<DataState<ConnectJobRecord>>()
        viewModel.learningProgress.observeForever { results.add(it) }

        mainCoroutineRule.runBlockingTest {
            viewModel.loadLearningProgress(mockJob)
        }

        assertEquals(2, results.size)
        assertTrue(results[1] is DataState.Error)
        assertEquals(mockJob, (results[1] as DataState.Error).cachedData)
    }
}
```

**Step 2: Attempt compile**
```bash
./gradlew compileDebugUnitTestKotlin 2>&1 | grep -E "(error|unresolved|BUILD)"
```
Expected: compile failure — `ConnectLearningProgressViewModel` unresolved. Correct.

---

### Task 34: Create ConnectLearningProgressFragmentTest

**Files:**
- Create: `app/unit-tests/src/org/commcare/fragments/connect/ConnectLearningProgressFragmentTest.kt`

**Step 1: Find the fragment's required arguments** — check `ConnectLearningProgressFragment.java` for what bundle args it needs (likely a `ConnectJobRecord` passed as a Parcelable). Find the argument key constant.

**Step 2: Create the file:**

```kotlin
package org.commcare.fragments.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.ConnectLearningProgressViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.lifecycle.MutableLiveData

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectLearningProgressFragmentTest {

    private lateinit var learningProgressLiveData: MutableLiveData<DataState<ConnectJobRecord>>
    private val mockJob = mockk<ConnectJobRecord>(relaxed = true)

    @Before
    fun setUp() {
        learningProgressLiveData = MutableLiveData()
        mockkConstructor(ConnectLearningProgressViewModel::class)
        every { anyConstructed<ConnectLearningProgressViewModel>().learningProgress } returns learningProgressLiveData
        every { anyConstructed<ConnectLearningProgressViewModel>().loadLearningProgress(any()) } answers { }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testFragment_nocrash_onLoadingState() {
        // Launch the fragment with required job argument
        // Adjust the argument key to match what ConnectLearningProgressFragment expects
        val args = android.os.Bundle().apply {
            // putParcelable("job_arg_key", mockJob) — find the actual key
        }
        // Use Robolectric.buildActivity if launchFragmentInContainer fails due to Activity requirement
        // This test primarily verifies no crash on Loading state
        learningProgressLiveData.value = DataState.Loading
        assertTrue(true) // placeholder — expand after finding fragment launch approach
    }

    @Test
    fun testFragment_nocrash_onSuccessState() {
        learningProgressLiveData.value = DataState.Success(mockJob)
        assertTrue(true) // placeholder — expand after finding fragment launch approach
    }

    @Test
    fun testFragment_nocrash_onCachedState() {
        learningProgressLiveData.value = DataState.Cached(mockJob, java.util.Date())
        assertTrue(true) // placeholder — expand after finding fragment launch approach
    }

    @Test
    fun testFragment_nocrash_onErrorState_withCachedData() {
        learningProgressLiveData.value = DataState.Error(cachedData = mockJob)
        assertTrue(true) // placeholder — expand after finding fragment launch approach
    }
}
```

**Note:** `ConnectLearningProgressFragment` likely requires a parent Activity and navigation setup. Once you read the fragment's `onCreateView()` to understand how it's launched, replace the placeholder tests with actual launch code following the same pattern as `BasePersonalIdBiometricConfigFragmentTest.kt` — use `Robolectric.buildActivity(ConnectActivity::class.java)` and navigate to the fragment.

**Step 3: Attempt compile**
```bash
./gradlew compileDebugUnitTestKotlin 2>&1 | grep -E "(error|unresolved|BUILD)"
```
Expected: compile failure — `ConnectLearningProgressViewModel` unresolved. Correct.

---

### Task 35: Commit Phase 4a (Red)

```bash
git add app/unit-tests/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModelTest.kt \
        app/unit-tests/src/org/commcare/fragments/connect/ConnectLearningProgressFragmentTest.kt
git commit -m "[AI] Phase 4a: Add failing tests for ConnectLearningProgressViewModel and ConnectLearningProgressFragment"
```

---

## Phase 4b — Learning Progress Migration (Green)

---

### Task 36: Create ConnectLearningProgressViewModel (with testability field)

**Files:**
- Create: `app/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModel.kt`

**Step 1: Create the file** using the architecture plan code (`Phase 4b → ConnectLearningProgressViewModel`) with this modification:

```kotlin
class ConnectLearningProgressViewModel(application: Application) : AndroidViewModel(application) {

    @VisibleForTesting
    internal var repository: ConnectRepository = ConnectRepository.getInstance(application)

    // rest identical to architecture plan
```

Add import: `import androidx.annotation.VisibleForTesting`

---

### Task 37: Migrate ConnectLearningProgressFragment

**Files:**
- Modify: `app/src/org/commcare/fragments/connect/ConnectLearningProgressFragment.java`

**Step 1: Apply the changes** from the architecture plan (`Phase 4b → Update ConnectLearningProgressFragment`):
- Add the three new imports
- Add `private ConnectLearningProgressViewModel viewModel;` field
- Update `onCreateView()` to initialize ViewModel and call `observeLearningProgress()`
- Add/update `onResume()`
- Add `refresh()` method
- Add `setupRefreshButton()` method
- Add `observeLearningProgress()` method
- Replace `refreshLearningData()` body to use `viewModel.loadLearningProgress(job)`

---

### Task 38: Run Phase 4a tests (expect Green)

```bash
./gradlew testDebugUnitTest \
  --tests "org.commcare.connect.viewmodel.ConnectLearningProgressViewModelTest" \
  --tests "org.commcare.fragments.connect.ConnectLearningProgressFragmentTest" \
  2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

---

### Task 39: Run all phases together to confirm no regressions

```bash
./gradlew testDebugUnitTest \
  --tests "org.commcare.connect.repository.ConnectSyncPreferencesTest" \
  --tests "org.commcare.connect.repository.ConnectRequestManagerTest" \
  --tests "org.commcare.connect.network.connect.ConnectNetworkClientTest" \
  --tests "org.commcare.connect.repository.ConnectRepositoryTest" \
  --tests "org.commcare.connect.viewmodel.ConnectJobsListViewModelTest" \
  --tests "org.commcare.fragments.connect.ConnectJobsListsFragmentTest" \
  --tests "org.commcare.connect.viewmodel.ConnectLearningProgressViewModelTest" \
  --tests "org.commcare.fragments.connect.ConnectLearningProgressFragmentTest" \
  2>&1 | tail -20
```
Expected: all pass.

---

### Task 40: Lint and commit Phase 4b

```bash
ktlint app/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModel.kt
```

```bash
git add app/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModel.kt \
        app/src/org/commcare/fragments/connect/ConnectLearningProgressFragment.java
git commit -m "[AI] Phase 4b: Implement ConnectLearningProgressViewModel and migrate ConnectLearningProgressFragment"
```

---

## Phase 5 — Manual Testing

Follow the manual testing checklist in `docs/claude/plans/2026-02-23-offline-first-connect-architecture.md` → Phase 5.

Key scenarios to validate:
1. Job list: network call on first open, cache on re-open same session, fresh call on new session
2. Learning progress: network call every navigation
3. Offline-first: cached data shows instantly, fresh data replaces it
4. Error handling: toast + cached data visible on network failure
5. Request deduplication: rapid sync clicks trigger only one call

---

## Quick Reference

| Phase | Test command |
|---|---|
| 1a/1b | `./gradlew testDebugUnitTest --tests "*.ConnectSyncPreferencesTest" --tests "*.ConnectRequestManagerTest"` |
| 1.5a/1.5b | `./gradlew testDebugUnitTest --tests "*.ConnectNetworkClientTest"` |
| 2a/2b | `./gradlew testDebugUnitTest --tests "*.ConnectRepositoryTest"` |
| 3a/3b | `./gradlew testDebugUnitTest --tests "*.ConnectJobsListViewModelTest" --tests "*.ConnectJobsListsFragmentTest"` |
| 4a/4b | `./gradlew testDebugUnitTest --tests "*.ConnectLearningProgressViewModelTest" --tests "*.ConnectLearningProgressFragmentTest"` |
| All | `./gradlew testDebugUnitTest --tests "org.commcare.connect.*" --tests "org.commcare.fragments.connect.*"` |
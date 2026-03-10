# Offline-First Connect Network Architecture Implementation Plan

## Overview

Implement an offline-first network architecture for Connect features that provides:
- **Single source of truth**: Repository pattern with Flow emissions
- **Request deduplication**: Shared in-flight requests across ViewModels
- **Smart caching**: Per-endpoint timestamp tracking with configurable refresh policies
- **Standardized patterns**: ViewModel + LiveData for consistent UI state management

Initial migration covers two fragments:
- `ConnectJobsListsFragment` (session-based refresh policy)
- `ConnectLearningProgressFragment` (always refresh policy)

**TDD approach**: Each implementation phase is preceded by a test phase (labeled `a`). Write failing tests first (Red), then implement to pass them (Green), then commit and proceed to the next phase. After each `b` phase passes automated verification, get manual confirmation that the manual testing checklist passes before proceeding.

## Current State Analysis

Callback-based `ConnectApiHandler` pattern: fragments create anonymous instances, make API calls, write to DB in callbacks. No deduplication, no caching, no repository. Best existing pattern: `PushNotificationViewModel.kt:31-70`.

## Desired End State

### Architecture Flow
```
Fragment (Java) → ViewModel (Kotlin) → Repository (Kotlin) → RequestManager → Network
                       ↓                      ↓
                   LiveData<DataState>   Database + Cache
```

### Key Characteristics
1. **Offline-first**: Emit cached data immediately, then fetch fresh data
2. **Request deduplication**: Multiple ViewModels requesting same endpoint share one network call
3. **Lifecycle independence**: Requests survive fragment navigation using application-scoped coroutines
4. **Smart refresh policies**:
   - Session/Time based: One fetch per app launch with a time threshold (ConnectJobsListsFragment)
   - Always: Fetch on every screen entry (ConnectLearningProgressFragment)

### Verification
- Fragments observe `LiveData<DataState<T>>` from ViewModels
- Database writes happen in Repository after network success
- No duplicate network requests when multiple ViewModels request same endpoint
- Cached data appears instantly, fresh data updates UI when available
- App launch timestamp determines "session" for session-based policies

## What We're NOT Doing

- Converting fragments from Java to Kotlin (keeping focused on architecture)
- Migrating other Connect fragments beyond the two specified
- Removing the old `BaseApiHandler` callback pattern (backward compatibility maintained)
- Using StateFlow instead of LiveData (LiveData works well with Java fragments)
- Implementing retry logic or exponential backoff
- Adding offline queue for failed requests
- Server-driven cache invalidation via response headers
- Batch database writes or complex transaction management (maintaining immediate writes)

## Implementation Approach

**Incremental Migration Strategy**:
1. Build new infrastructure alongside existing code (no breaking changes)
2. Migrate one fragment at a time with full testing between phases
3. Keep old `ConnectApiHandler` pattern available during transition
4. Use application-scoped `ConnectRequestManager` singleton for deduplication
5. Repository writes to database immediately after network success (maintaining current behavior)
6. LiveData emissions for Java fragment compatibility

**Key Design Decisions**:
- **LiveData over StateFlow**: Better Java interop, fragments already use LiveData observation
- **Application-scoped RequestManager**: Singleton pattern for cross-ViewModel deduplication
- **Session = App Launch**: Tracked via `ConnectSyncPreferences.sessionStartTime`
- **Repository owns database writes**: Centralizes data persistence logic
- **Coroutines with Dispatchers.IO**: Background work without blocking main thread

---

## Phase 0: Contracts & Interfaces

### Overview
Define the core data structures and contracts that all other phases depend on. This establishes the type-safe state management and refresh policy system.

**Note**: Phase 0 has no preceding test phase. `DataState` and `RefreshPolicy` are pure sealed class type definitions with no logic to test. Phase 1a tests reference these types, providing an implicit compile-time contract check.

### Changes Required

#### 1. DataState Sealed Class
**File**: `app/src/org/commcare/connect/repository/DataState.kt` (new file)
**Purpose**: Type-safe representation of data loading states

```kotlin
package org.commcare.connect.repository

import java.util.Date
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException

sealed class DataState<out T> {
    object Loading : DataState<Nothing>()
    data class Cached<T>(val data: T, val timestamp: Date) : DataState<T>()
    data class Success<T>(val data: T) : DataState<T>()

    // cachedData allows fragments to show stale data even on error
    data class Error<T>(
        val errorCode: PersonalIdOrConnectApiErrorCodes = PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
        val throwable: Throwable? = null,
        val cachedData: T? = null
    ) : DataState<T>() {
        companion object {
            /**
             * Builds a DataState.Error from a throwable, extracting the typed error code from
             * ConnectApiException or falling back to UNKNOWN_ERROR.
             */
            fun <T> from(throwable: Throwable, cachedData: T? = null): Error<T> = Error(
                errorCode = (throwable as? ConnectApiException)?.errorCode
                    ?: PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR,
                throwable = throwable,
                cachedData = cachedData
            )
        }
    }
}
```

#### 2. RefreshPolicy Sealed Class
**File**: `app/src/org/commcare/connect/repository/RefreshPolicy.kt` (new file)

```kotlin
package org.commcare.connect.repository

sealed class RefreshPolicy {
    object ALWAYS : RefreshPolicy()
    // Fetch if new app session since last sync OR cache older than timeThresholdMs
    data class SESSION_AND_TIME_BASED(val timeThresholdMs: Long = 60_000) : RefreshPolicy()
}
```

Must be a sealed class (not enum) because `SESSION_AND_TIME_BASED` needs different `timeThresholdMs` values at different call sites.

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/repository/*.kt`

#### Manual Verification:
- [ ] `DataState` sealed class hierarchy is correct (4 states: Loading, Cached, Success, Error)
- [ ] All DataState states have appropriate properties
- [ ] `RefreshPolicy` sealed class has 2 subtypes: `ALWAYS` (object) and `SESSION_AND_TIME_BASED` (data class with `timeThresholdMs`)

---

## Phase 1a: Core Infrastructure Tests (Red)

### Overview
Write failing tests for `ConnectSyncPreferences` and `ConnectRequestManager`. These tests also reference `DataState` and `RefreshPolicy` from Phase 0, providing compile-time verification of those contracts. Commit these tests before implementing the production classes in Phase 1b.

**Expected outcome**: Tests fail to compile (classes don't exist yet) or fail at runtime. This is correct — commit the Red state.

### Changes Required

#### 1. ConnectSyncPreferencesTest
**File**: `app/unit-tests/src/org/commcare/connect/repository/ConnectSyncPreferencesTest.kt` (new file)

```kotlin
package org.commcare.connect.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.commcare.connect.repository.RefreshPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(AndroidJUnit4::class)
@Config(application = CommCareTestApplication::class)
class ConnectSyncPreferencesTest {

    private lateinit var context: Context
    private lateinit var syncPrefs: ConnectSyncPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        syncPrefs = ConnectSyncPreferences.getInstance(context)
        syncPrefs.clearAll()
    }

    @After
    fun tearDown() {
        syncPrefs.clearAll()
    }

    @Test
    fun testSessionStartTime_initializedOnFirstAccess() {
        val sessionStart = syncPrefs.getSessionStartTime()
        assertNotNull(sessionStart)

        // Should be recent (within last minute)
        val ageMs = Date().time - sessionStart.time
        assertTrue(ageMs < 60_000)
    }

    @Test
    fun testStoreAndRetrieveLastSyncTime() {
        val endpoint = "/opportunities"

        // Initially no sync time
        assertNull(syncPrefs.getLastSyncTime(endpoint))

        // Store sync time
        syncPrefs.storeLastSyncTime(endpoint)

        // Retrieve sync time
        val lastSync = syncPrefs.getLastSyncTime(endpoint)
        assertNotNull(lastSync)

        // Should be recent
        val ageMs = Date().time - lastSync!!.time
        assertTrue(ageMs < 1_000)
    }

    @Test
    fun testShouldRefresh_alwaysPolicy() {
        val endpoint = "/learning_progress"

        // Store a recent sync
        syncPrefs.storeLastSyncTime(endpoint)

        // ALWAYS policy should always return true
        assertTrue(syncPrefs.shouldRefresh(endpoint, RefreshPolicy.ALWAYS))
    }

    @Test
    fun testShouldRefresh_hybridPolicy_freshCache_sameSession() {
        val endpoint = "/opportunities"

        // Mark session start
        syncPrefs.markSessionStart()

        // Wait a bit
        Thread.sleep(100)

        // Store sync time (after session start, cache is fresh)
        syncPrefs.storeLastSyncTime(endpoint)

        // Should NOT refresh - same session AND cache is fresh
        val policy = RefreshPolicy.SESSION_AND_TIME_BASED(60_000) // 1 minute
        assertFalse(syncPrefs.shouldRefresh(endpoint, policy))
    }

    @Test
    fun testShouldRefresh_hybridPolicy_staleCache_sameSession() {
        val endpoint = "/opportunities"

        // Mark session start
        syncPrefs.markSessionStart()

        // Wait a bit
        Thread.sleep(100)

        // Store sync time (after session start)
        syncPrefs.storeLastSyncTime(endpoint)

        // Should refresh - cache is stale (time threshold = 0)
        val policy = RefreshPolicy.SESSION_AND_TIME_BASED(0)
        assertTrue(syncPrefs.shouldRefresh(endpoint, policy))
    }

    @Test
    fun testShouldRefresh_hybridPolicy_freshCache_newSession() {
        val endpoint = "/opportunities"

        // Store sync time first
        syncPrefs.storeLastSyncTime(endpoint)

        // Wait a bit
        Thread.sleep(100)

        // Start new session (after sync)
        syncPrefs.markSessionStart()

        // Should refresh - new session (even though cache is fresh)
        val policy = RefreshPolicy.SESSION_AND_TIME_BASED(60_000) // 1 minute
        assertTrue(syncPrefs.shouldRefresh(endpoint, policy))
    }

    @Test
    fun testShouldRefresh_hybridPolicy_bothConditionsTrue() {
        val endpoint = "/opportunities"

        // Store sync time first
        syncPrefs.storeLastSyncTime(endpoint)

        // Wait a bit
        Thread.sleep(100)

        // Start new session
        syncPrefs.markSessionStart()

        // Should refresh - both new session AND stale cache
        val policy = RefreshPolicy.SESSION_AND_TIME_BASED(0)
        assertTrue(syncPrefs.shouldRefresh(endpoint, policy))
    }

    @Test
    fun testShouldRefresh_neverSynced() {
        val endpoint = "/opportunities"

        // Should always refresh if never synced
        assertTrue(syncPrefs.shouldRefresh(endpoint, RefreshPolicy.ALWAYS))
        assertTrue(syncPrefs.shouldRefresh(endpoint, RefreshPolicy.SESSION_AND_TIME_BASED(60_000)))
    }
}
```

#### 2. ConnectRequestManagerTest
**File**: `app/unit-tests/src/org/commcare/connect/repository/ConnectRequestManagerTest.kt` (new file)

```kotlin
package org.commcare.connect.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ConnectRequestManagerTest {

    @After
    fun tearDown() {
        ConnectRequestManager.cancelAll()
    }

    @Test
    fun testSingleRequest_succeeds() = runBlocking {
        val url = "/test"
        var callCount = 0

        val result = ConnectRequestManager.executeRequest(url) {
            callCount++
            Result.success("data")
        }

        assertTrue(result.isSuccess)
        assertEquals("data", result.getOrNull())
        assertEquals(1, callCount)
    }

    @Test
    fun testSingleRequest_fails() = runBlocking {
        val url = "/test"
        val exception = Exception("Network error")

        val result = ConnectRequestManager.executeRequest(url) {
            Result.failure<String>(exception)
        }

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun testDuplicateRequests_deduplicated() = runBlocking {
        val url = "/opportunities"
        var callCount = 0

        // Launch two concurrent requests for same URL
        val deferred1 = async {
            ConnectRequestManager.executeRequest(url) {
                callCount++
                delay(100) // Simulate slow network
                Result.success("data")
            }
        }

        val deferred2 = async {
            delay(10) // Start slightly later
            ConnectRequestManager.executeRequest(url) {
                callCount++
                Result.success("data")
            }
        }

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        // Both succeed
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)

        // But network was only called once
        assertEquals(1, callCount)
    }

    @Test
    fun testDifferentUrls_notDeduplicated() = runBlocking {
        var callCount = 0

        val deferred1 = async {
            ConnectRequestManager.executeRequest("/url1") {
                callCount++
                Result.success("data1")
            }
        }

        val deferred2 = async {
            ConnectRequestManager.executeRequest("/url2") {
                callCount++
                Result.success("data2")
            }
        }

        deferred1.await()
        deferred2.await()

        // Different URLs should both execute
        assertEquals(2, callCount)
    }

    @Test
    fun testIsRequestInProgress() = runBlocking {
        val url = "/test"

        assertFalse(ConnectRequestManager.isRequestInProgress(url))

        val deferred = async {
            ConnectRequestManager.executeRequest(url) {
                delay(100)
                Result.success("data")
            }
        }

        // Should be in progress
        delay(10)
        assertTrue(ConnectRequestManager.isRequestInProgress(url))

        // Wait for completion
        deferred.await()

        // Should no longer be in progress
        assertFalse(ConnectRequestManager.isRequestInProgress(url))
    }

    @Test
    fun testRequestContinuesAfterCallerCancellation() = runBlocking {
        // Verifies that the request lambda (network + DB write) runs to completion
        // even when the caller's coroutine is cancelled (simulating user navigation).
        val url = "/test"
        var requestCompleted = false

        val callerJob = async {
            ConnectRequestManager.executeRequest(url) {
                delay(100) // simulate network latency
                requestCompleted = true
                Result.success("data")
            }
        }

        delay(10) // let the request start
        callerJob.cancel() // simulate user backing out (viewModelScope cancelled)

        // Give the app-scoped scope.launch time to complete the request
        delay(200)

        // The request lambda must have completed despite caller cancellation
        assertTrue(requestCompleted)
    }
}
```

### Success Criteria

#### Automated Verification:
- [ ] No linting errors: `ktlint app/unit-tests/src/org/commcare/connect/repository/ConnectSyncPreferencesTest.kt app/unit-tests/src/org/commcare/connect/repository/ConnectRequestManagerTest.kt`
- [ ] Kotlin compilation of test files: `./gradlew compileDebugKotlin` (compile failure is expected and acceptable if `ConnectSyncPreferences`/`ConnectRequestManager` don't exist yet)
- [ ] Tests **fail** (Red — expected): `./gradlew testDebugUnitTest --tests "org.commcare.connect.repository.ConnectSyncPreferencesTest"` and `--tests "org.commcare.connect.repository.ConnectRequestManagerTest"`
- [ ] Commit failing tests before proceeding to Phase 1b

---

## Phase 1b: Core Infrastructure Implementation (Green)

### Overview
Implement `ConnectSyncPreferences` and `ConnectRequestManager` to pass the Phase 1a tests.

### Changes Required

#### 1. ConnectSyncPreferences
**File**: `app/src/org/commcare/connect/repository/ConnectSyncPreferences.kt` (new file)
**Purpose**: Tracks last sync times per endpoint and app session start time

```kotlin
package org.commcare.connect.repository

import android.content.Context
import android.content.SharedPreferences
import org.commcare.CommCareApplication
import java.util.Date

/**
 * Manages sync timestamps for Connect endpoints.
 * Stores per-endpoint last sync times and session start time for refresh policies.
 */
class ConnectSyncPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "connect_sync_prefs"
        private const val KEY_SESSION_START = "session_start_time"
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_"

        @Volatile
        private var instance: ConnectSyncPreferences? = null

        fun getInstance(context: Context): ConnectSyncPreferences {
            return instance ?: synchronized(this) {
                instance ?: ConnectSyncPreferences(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        if (!prefs.contains(KEY_SESSION_START)) {
            markSessionStart()
        }
    }

    fun markSessionStart() {
        prefs.edit()
            .putLong(KEY_SESSION_START, Date().time)
            .apply()
    }

    fun getSessionStartTime(): Date {
        val timestamp = prefs.getLong(KEY_SESSION_START, Date().time)
        return Date(timestamp)
    }

    fun storeLastSyncTime(endpoint: String) {
        val key = KEY_LAST_SYNC_PREFIX + endpoint.replace("/", "_")
        prefs.edit()
            .putLong(key, Date().time)
            .apply()
    }

    fun getLastSyncTime(endpoint: String): Date? {
        val key = KEY_LAST_SYNC_PREFIX + endpoint.replace("/", "_")
        val timestamp = prefs.getLong(key, -1)
        return if (timestamp == -1L) null else Date(timestamp)
    }

    fun shouldRefresh(
        endpoint: String,
        policy: RefreshPolicy
    ): Boolean {
        return when (policy) {
            RefreshPolicy.ALWAYS -> true

            is RefreshPolicy.SESSION_AND_TIME_BASED -> {
                val lastSync = getLastSyncTime(endpoint) ?: return true
                val sessionStart = getSessionStartTime()
                val isNewSession = lastSync.before(sessionStart)
                val ageMs = Date().time - lastSync.time
                val isStale = ageMs >= policy.timeThresholdMs
                isNewSession || isStale
            }
        }
    }

    /**
     * Clears all sync data (for testing or logout).
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        markSessionStart()
    }
}
```

**Integration Point**:
- Call `markSessionStart()` in `CommCareApplication.onCreate()` — NOT in an Activity (Activity.onCreate fires on rotation/back-nav, resetting the session).
- The `init{}` block handles first-ever launch (when `KEY_SESSION_START` doesn't exist yet); `Application.onCreate()` handles all subsequent launches.

#### 2. ConnectRequestManager
**File**: `app/src/org/commcare/connect/repository/ConnectRequestManager.kt` (new file)
**Purpose**: Deduplicates in-flight network requests across ViewModels

```kotlin
package org.commcare.connect.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object ConnectRequestManager {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Result<Any>>>()

    /**
     * Executes [request] in app scope — survives ViewModel cancellation.
     * Include DB writes inside [request] so they complete even on back navigation.
     * Duplicate calls to the same [url] share one in-flight request.
     */
    suspend fun <T> executeRequest(
        url: String,
        request: suspend () -> Result<T>
    ): Result<T> {
        val deferred = CompletableDeferred<Result<Any>>()
        val existing = inFlightRequests.putIfAbsent(url, deferred)
        if (existing != null) {
            @Suppress("UNCHECKED_CAST")
            return existing.await() as Result<T>
        }

        // Launch in app scope so request + DB writes survive viewModelScope cancellation.
        scope.launch {
            try {
                val result = request()
                deferred.complete(result as Result<Any>)
            } catch (e: CancellationException) {
                deferred.cancel(e)
                throw e
            } catch (e: Exception) {
                deferred.complete(Result.failure(e) as Result<Any>)
            } finally {
                inFlightRequests.remove(url)
            }
        }

        // deferred.await() IS cancellable — caller cancellation stops waiting but NOT the launch above.
        @Suppress("UNCHECKED_CAST")
        return deferred.await() as Result<T>
    }

    fun isRequestInProgress(url: String): Boolean = inFlightRequests.containsKey(url)

    // Call on logout to prevent stale data writes from previous-session requests.
    // Resets scope so the object can be reused after logout (important for tests and re-login).
    fun cancelAll() {
        inFlightRequests.values.forEach { it.cancel() }
        inFlightRequests.clear()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/repository/*.kt`
- [ ] Unit tests **pass** (Green): `./gradlew testDebugUnitTest --tests "org.commcare.connect.repository.ConnectSyncPreferencesTest"` and `--tests "org.commcare.connect.repository.ConnectRequestManagerTest"`

#### Manual Verification:
- [ ] `ConnectSyncPreferences` singleton initializes correctly
- [ ] Session start time is set on first access
- [ ] `shouldRefresh()` logic correctly implements hybrid policy (session OR time threshold)
- [ ] Hybrid policy refreshes on new session even with fresh cache
- [ ] Hybrid policy refreshes on stale cache even in same session
- [ ] `ConnectRequestManager` deduplication logic is thread-safe (ConcurrentHashMap)
- [ ] In-flight requests are properly cleaned up after completion


---

## Phase 1.5a: Coroutine Network Client Tests (Red)

### Overview
Write failing tests for `ConnectNetworkClient`. At this point `ConnectNetworkClient` doesn't exist, so tests will fail to compile or fail at runtime. Commit the Red state before proceeding to Phase 1.5b.

### Changes Required

#### 1. ConnectNetworkClientTest
**File**: `app/unit-tests/src/org/commcare/connect/network/connect/ConnectNetworkClientTest.kt` (new file)
**Dependencies to mock**: `ConnectApiService` (via Mockito), `getAuthorizationHeader` (top-level suspend function — use `mockk` or `mockkStatic`)

```kotlin
package org.commcare.connect.network.connect

// Test method signatures — implement bodies in Phase 1.5b
class ConnectNetworkClientTest {

    // mock ConnectApiService.getConnectOpportunities to return 200 with valid JSON body;
    // verify Result.success is returned containing a parsed ConnectOpportunitiesResponseModel
    @Test fun testGetConnectOpportunities_success_returnsModel(): Unit = TODO()

    // mock ConnectApiService.getConnectOpportunities to return HTTP 401;
    // verify Result.failure with ConnectApiException(FAILED_AUTH_ERROR)
    @Test fun testGetConnectOpportunities_httpError_returnsFailure(): Unit = TODO()

    // mock ConnectApiService.getConnectOpportunities to throw IOException;
    // verify Result.failure with ConnectApiException(NETWORK_ERROR)
    @Test fun testGetConnectOpportunities_networkException_returnsNetworkError(): Unit = TODO()

    // mock getAuthorizationHeader to return Result.failure;
    // verify Result.failure is propagated before any API call is made
    @Test fun testGetConnectOpportunities_authHeaderFailure_returnsFailure(): Unit = TODO()

    // mock ConnectApiService.getLearningProgress to return 200 with valid JSON body;
    // verify Result.success containing a parsed LearningAppProgressResponseModel
    @Test fun testGetLearningProgress_success_returnsModel(): Unit = TODO()

    // mock ConnectApiService.getLearningProgress to return HTTP 500;
    // verify Result.failure with ConnectApiException(SERVER_ERROR)
    @Test fun testGetLearningProgress_httpError_returnsFailure(): Unit = TODO()
}
```

### Success Criteria

#### Automated Verification:
- [ ] No linting errors: `ktlint app/unit-tests/src/org/commcare/connect/network/connect/ConnectNetworkClientTest.kt`
- [ ] Tests **fail** (Red — expected): `./gradlew testDebugUnitTest --tests "org.commcare.connect.network.connect.ConnectNetworkClientTest"`
- [ ] Commit failing tests before proceeding to Phase 1.5b

---

## Phase 1.5b: Coroutine-Based Network Client Implementation (Green)

### Overview
Create a new pure coroutine-based network client (`ConnectNetworkClient`) alongside the existing callback-based `ConnectApiHandler`. Because `ApiService.java` is a Java interface and Kotlin `suspend fun` bytecode is incompatible with Retrofit's Java codegen, suspend methods go in a separate Kotlin interface (`ConnectApiService`).

**Key principle**: `ConnectNetworkClient` does NOT wrap the old `ConnectApiHandler`. It's a parallel implementation using Retrofit suspend functions.


### Changes Required

#### 1. ConnectApiService (Kotlin Retrofit Interface)
**File**: `app/src/org/commcare/connect/network/ConnectApiService.kt` (new file)
**Purpose**: Kotlin-only Retrofit interface with suspend variants — cannot add `suspend fun` to `ApiService.java`

```kotlin
package org.commcare.connect.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.Path

interface ConnectApiService {

    @GET("/api/opportunity/")
    suspend fun getConnectOpportunities(
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>

    @GET("/api/opportunity/{id}/learn_progress")
    suspend fun getLearningProgress(
        @Header("Authorization") authorization: String,
        @Path("id") jobId: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>
}
```

**Note**: `ApiService.java` is unchanged. Existing callback-based code continues using it.

#### 2. ConnectNetworkClient
**File**: `app/src/org/commcare/connect/network/connect/ConnectNetworkClient.kt` (new file)
**Purpose**: Pure suspend function API client. Named differently from the existing `ConnectApiClient.kt`.

```kotlin
package org.commcare.connect.network.connect

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.ConnectApiService
import org.commcare.connect.network.ConnectNetworkHelper
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.LoginInvalidatedException
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.commcare.connect.network.connect.parser.ConnectOpportunitiesParser
import org.commcare.connect.network.connect.parser.LearningAppProgressResponseParser
import org.commcare.connect.network.getAuthorizationHeader
import org.commcare.connect.network.mapHttpErrorCode
import java.io.IOException
import java.io.InputStream
import okhttp3.ResponseBody
import retrofit2.Response

class ConnectNetworkClient(private val context: Context) {

    companion object {
        private const val API_VERSION = "1.0"

        @Volatile
        private var instance: ConnectNetworkClient? = null

        fun getInstance(context: Context): ConnectNetworkClient =
            instance ?: synchronized(this) {
                instance ?: ConnectNetworkClient(context.applicationContext).also { instance = it }
            }
    }

    private val apiService: ConnectApiService by lazy {
        BaseApiClient.buildRetrofitClient(ConnectApiClient.BASE_URL).create(ConnectApiService::class.java)
    }

    private fun versionHeaders(): Map<String, String> =
        HashMap<String, String>().also { ConnectNetworkHelper.addVersionHeader(it, API_VERSION) }

    suspend fun getConnectOpportunities(user: ConnectUserRecord): Result<ConnectOpportunitiesResponseModel> =
        executeApiCall(
            user = user,
            apiCall = { auth -> apiService.getConnectOpportunities(auth, versionHeaders()) },
            // ConnectOpportunitiesParser.parse() writes to DB internally; anyInputObject must be Context
            parse = { code, stream -> ConnectOpportunitiesParser().parse(code, stream, context) },
        )

    suspend fun getLearningProgress(user: ConnectUserRecord, job: ConnectJobRecord): Result<LearningAppProgressResponseModel> =
        executeApiCall(
            user = user,
            apiCall = { auth -> apiService.getLearningProgress(auth, job.jobUUID, versionHeaders()) },
            // LearningAppProgressResponseParser.parse() anyInputObject must be ConnectJobRecord
            parse = { code, stream -> LearningAppProgressResponseParser().parse(code, stream, job) },
        )

    // IOException → NETWORK_ERROR; parse exception → JSON_PARSING_ERROR; Exception → UNKNOWN_ERROR
    private suspend fun <T> executeApiCall(
        user: ConnectUserRecord,
        apiCall: suspend (authHeader: String) -> Response<ResponseBody>,
        parse: (responseCode: Int, stream: InputStream) -> T,
    ): Result<T> {
        return try {
            val authHeader = getAuthorizationHeader(context, user)
                .getOrElse { return Result.failure(it) }

            val response = apiCall(authHeader)

            if (response.isSuccessful) {
                val stream = response.body()?.byteStream()
                    ?: return Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.JSON_PARSING_ERROR))
                try {
                    Result.success(parse(response.code(), stream))
                } catch (e: Exception) {
                    Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.JSON_PARSING_ERROR, e))
                }
            } else {
                val errorCode = mapHttpErrorCode(response.code(), response.errorBody()?.string())
                Result.failure(ConnectApiException(errorCode))
            }
        } catch (e: LoginInvalidatedException) {
            throw e  // must propagate to CommCareExceptionHandler for logout/recovery flow
        } catch (e: IOException) {
            Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR, e))
        } catch (e: Exception) {
            Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR, e))
        }
    }
}
```

#### 3. ConnectNetworkHelper (converted to Kotlin)
**File**: `app/src/org/commcare/connect/network/ConnectNetworkHelper.kt` (converted from Java)
**Purpose**: Convert the existing `ConnectNetworkHelper.java` to Kotlin, preserving all existing static methods in a `companion object` with `@JvmStatic` so Java callers remain unaffected. Add the new `getAuthorizationHeader` and `mapHttpErrorCode` as top-level functions in the same file.

Token retrieval may require a network call (PersonalId token refresh), so `getAuthorizationHeader` is a `suspend fun` wrapping `ConnectSsoHelper.retrievePersonalIdToken` via `suspendCancellableCoroutine`.

```kotlin
package org.commcare.connect.network

import android.content.Context
import com.google.common.collect.ArrayListMultimap
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType
import okhttp3.RequestBody
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.core.network.AuthInfo
import org.commcare.core.network.ModernHttpRequester
import org.commcare.network.HttpUtils
import org.commcare.utils.JsonExtensions
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume

class ConnectNetworkHelper {
    companion object {
        @JvmStatic
        fun addVersionHeader(headers: HashMap<String, String>, version: String?) {
            if (version != null) {
                headers["Accept"] = "application/json;version=$version"
            }
        }

        @JvmStatic
        fun buildPostFormHeaders(
            params: HashMap<String, Any>,
            useFormEncoding: Boolean,
            version: String?,
            outputHeaders: HashMap<String, String>,
        ): RequestBody {
            val requestBody: RequestBody
            val headers: HashMap<String, String>

            if (useFormEncoding) {
                val multimap = ArrayListMultimap.create<String, String>()
                for ((key, value) in params) {
                    multimap.put(key, value.toString())
                }
                requestBody = ModernHttpRequester.getPostBody(multimap)
                headers = getContentHeadersForXFormPost(requestBody)
            } else {
                val json = Gson().toJson(params)
                requestBody = RequestBody.create(MediaType.parse("application/json"), json)
                headers = outputHeaders
            }

            addVersionHeader(headers, version)
            return requestBody
        }

        private fun getContentHeadersForXFormPost(postBody: RequestBody): HashMap<String, String> {
            val headers = HashMap<String, String>()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
            try {
                headers["Content-Length"] = postBody.contentLength().toString()
            } catch (e: Exception) {
                // Empty headers if something goes wrong
            }
            return headers
        }

        @JvmStatic
        fun checkForLoginFromDifferentDevice(errorBody: String?): Boolean {
            if (errorBody == null) return false
            return try {
                val json = JSONObject(errorBody)
                "LOGIN_FROM_DIFFERENT_DEVICE" == JsonExtensions.optStringSafe(json, "error_code", null)
            } catch (e: JSONException) {
                false
            }
        }
    }
}

suspend fun getAuthorizationHeader(
    context: Context,
    user: ConnectUserRecord,
): Result<String> = suspendCancellableCoroutine { continuation ->
    ConnectSsoHelper.retrievePersonalIdToken(
        context,
        user,
        object : ConnectSsoHelper.TokenCallback {
            override fun tokenRetrieved(token: AuthInfo.TokenAuth) {
                continuation.resume(Result.success(HttpUtils.getCredential(token)))
            }

            override fun tokenUnavailable() {
                continuation.resume(
                    Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR))
                )
            }

            override fun tokenRequestDenied() {
                continuation.resume(
                    Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR))
                )
            }
        },
    )
}

internal fun mapHttpErrorCode(
    responseCode: Int,
    errorBody: String?,
): PersonalIdOrConnectApiErrorCodes =
    when (responseCode) {
        401 -> PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR
        403 -> PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR
        429 -> PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR
        400 -> {
            if (ConnectNetworkHelper.checkForLoginFromDifferentDevice(errorBody)) {
                GlobalErrorUtil.triggerGlobalError(GlobalErrors.PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR)
            }
            PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR
        }
        in 500..509 -> PersonalIdOrConnectApiErrorCodes.SERVER_ERROR
        else -> PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR
    }
```

#### 4. ConnectApiException
**File**: `app/src/org/commcare/connect/network/base/ConnectApiException.kt` (new file)

```kotlin
package org.commcare.connect.network.base

// Carries typed error code through Result<T> chain; repository extracts it into DataState.Error.
class ConnectApiException(
    val errorCode: PersonalIdOrConnectApiErrorCodes,
    cause: Throwable? = null,
) : Exception(errorCode.name, cause)
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/network/ConnectNetworkClient.kt app/src/org/commcare/connect/network/ConnectNetworkHelper.kt`
- [ ] Unit tests **pass** (Green): `./gradlew testDebugUnitTest --tests "org.commcare.connect.network.connect.ConnectNetworkClientTest"`

#### Manual Verification:
- [ ] `ConnectNetworkClient.getConnectOpportunities()` successfully fetches and parses opportunities
- [ ] `ConnectNetworkClient.getLearningProgress()` successfully fetches and parses learning progress
- [ ] Suspend functions can be called from coroutine scope without blocking
- [ ] Error responses are properly wrapped in `Result.failure`
- [ ] Network errors (timeouts, connection failures) are handled gracefully
- [ ] Old callback-based `ConnectApiHandler` continues to work unchanged


---

## Phase 2a: Repository Tests (Red)

### Overview
Write failing tests for `ConnectRepository`. At this point `ConnectRepository` doesn't exist, so tests will fail to compile or fail at runtime. Commit the Red state before proceeding to Phase 2b.

### Changes Required

#### 1. ConnectRepositoryTest
**File**: `app/unit-tests/src/org/commcare/connect/repository/ConnectRepositoryTest.kt` (new file)
**Dependencies to mock**: `ConnectNetworkClient`, `ConnectSyncPreferences`, `ConnectJobUtils` (database layer)
**Flow testing**: use `kotlinx-coroutines-test` with `runTest` and `turbine` or collect-to-list pattern

```kotlin
package org.commcare.connect.repository

// Test method signatures — implement bodies in Phase 2b
class ConnectRepositoryTest {

    // mock ConnectJobUtils to return empty list; verify first (and only) emission is DataState.Loading
    @Test fun testGetOpportunities_noCache_emitsLoading(): Unit = TODO()

    // mock DB with job list + successful network call; verify emissions in order: DataState.Cached, DataState.Success
    @Test fun testGetOpportunities_withCache_emitsCachedThenSuccess(): Unit = TODO()

    // mock DB with job list + failed network call; verify emissions: DataState.Cached, then DataState.Error with cachedData != null
    @Test fun testGetOpportunities_networkFailure_emitsError_withCachedData(): Unit = TODO()

    // mock empty DB + failed network call; verify emissions: DataState.Loading, then DataState.Error with cachedData == null
    @Test fun testGetOpportunities_networkFailure_noCache_emitsError_withNullCachedData(): Unit = TODO()

    // mock syncPrefs.shouldRefresh to return false; call getOpportunities(forceRefresh=true);
    // verify network IS called (forceRefresh bypasses shouldRefresh)
    @Test fun testGetOpportunities_forceRefresh_bypassesShouldRefreshCheck(): Unit = TODO()

    // mock syncPrefs.shouldRefresh to return false + cached data in DB;
    // call getOpportunities(forceRefresh=false); verify only DataState.Cached is emitted (no network call)
    @Test fun testGetOpportunities_shouldRefreshFalse_emitsCachedOnly(): Unit = TODO()

    // mock successful network call; verify syncPrefs.storeLastSyncTime() is called after success
    @Test fun testGetOpportunities_networkSuccess_writesSyncTimestamp(): Unit = TODO()

    // call getLearningProgress twice with same job; verify network is called both times (ALWAYS policy)
    @Test fun testGetLearningProgress_alwaysPolicy_alwaysFetches(): Unit = TODO()
}
```

### Success Criteria

#### Automated Verification:
- [ ] No linting errors: `ktlint app/unit-tests/src/org/commcare/connect/repository/ConnectRepositoryTest.kt`
- [ ] Tests **fail** (Red — expected): `./gradlew testDebugUnitTest --tests "org.commcare.connect.repository.ConnectRepositoryTest"`
- [ ] Commit failing tests before proceeding to Phase 2b

---

## Phase 2b: Repository Foundation + Opportunities Endpoint (Green)

### Overview
Create the `ConnectRepository` class and implement both endpoints (`getOpportunities` and `getLearningProgress`) to pass the Phase 2a tests. This establishes the repository pattern and demonstrates the offline-first flow.

### Changes Required

#### 1. ConnectRepository
**File**: `app/src/org/commcare/connect/repository/ConnectRepository.kt` (new file)
**Purpose**: Single source of truth for Connect data with offline-first pattern

```kotlin
package org.commcare.connect.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel

class ConnectRepository(private val context: Context) {

    companion object {
        private const val ENDPOINT_OPPORTUNITIES = "/opportunities"
        private const val ENDPOINT_LEARNING_PREFIX = "/learning_progress/"

        @Volatile
        private var instance: ConnectRepository? = null

        fun getInstance(context: Context): ConnectRepository {
            return instance ?: synchronized(this) {
                instance ?: ConnectRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val syncPrefs = ConnectSyncPreferences.getInstance(context)

    fun getOpportunities(
        forceRefresh: Boolean = false,
        policy: RefreshPolicy = RefreshPolicy.SESSION_AND_TIME_BASED(60_000),
    ): Flow<DataState<List<ConnectJobRecord>>> = offlineFirstFlow(
        endpoint = ENDPOINT_OPPORTUNITIES,
        forceRefresh = forceRefresh,
        policy = policy,
        loadCache = {
            ConnectJobUtils.getCompositeJobs(context, ConnectJobRecord.STATUS_ALL_JOBS, null)
                .takeIf { it.isNotEmpty() }
        },
        networkCall = { fetchOpportunitiesFromNetwork() },
        onNetworkSuccess = {}, // ConnectOpportunitiesParser.parse() writes to DB internally via storeJobs()

        mapToEmit = { responseModel -> responseModel.validJobs },
    )

    fun getLearningProgress(
        job: ConnectJobRecord,
        forceRefresh: Boolean = false,
        policy: RefreshPolicy = RefreshPolicy.ALWAYS,
    ): Flow<DataState<ConnectJobRecord>> = offlineFirstFlow(
        endpoint = ENDPOINT_LEARNING_PREFIX + job.jobUUID,
        forceRefresh = forceRefresh,
        policy = policy,
        loadCache = { ConnectJobUtils.getCompositeJob(context, job.jobUUID) },
        networkCall = { fetchLearningProgressFromNetwork(job) },
        onNetworkSuccess = { responseModel ->
            job.learnings = responseModel.connectJobLearningRecords
            job.completedLearningModules = responseModel.connectJobLearningRecords.size
            job.assessments = responseModel.connectJobAssessmentRecords
            ConnectJobUtils.updateJobLearnProgress(context, job)
        },
        mapToEmit = { _ -> ConnectJobUtils.getCompositeJob(context, job.jobUUID) ?: job },
    )

    /**
     * Emits Loading/Cached, then Success or Error.
     * [onNetworkSuccess] runs in app scope — put all DB writes here, NOT in [networkCall].
     * [mapToEmit] runs in Flow scope — safe to re-read DB here after writes complete.
     */
    private fun <C, N> offlineFirstFlow(
        endpoint: String,
        forceRefresh: Boolean,
        policy: RefreshPolicy,
        loadCache: () -> C?,
        networkCall: suspend () -> Result<N>,
        onNetworkSuccess: suspend (N) -> Unit,
        mapToEmit: suspend (N) -> C,
    ): Flow<DataState<C>> = flow {
        val cachedData: C? = loadCache()
        val lastSyncTime = syncPrefs.getLastSyncTime(endpoint)

        if (cachedData != null && lastSyncTime != null) {
            emit(DataState.Cached(cachedData, lastSyncTime))
        } else {
            emit(DataState.Loading)
        }

        if (!forceRefresh && !syncPrefs.shouldRefresh(endpoint, policy)) return@flow

        try {
            val result = ConnectRequestManager.executeRequest(endpoint) {
                networkCall().also { networkResult ->
                    networkResult.onSuccess { data ->
                        onNetworkSuccess(data)
                        syncPrefs.storeLastSyncTime(endpoint)
                    }
                }
            }
            result
                .onSuccess { data -> emit(DataState.Success(mapToEmit(data))) }
                .onFailure { throwable -> emit(DataState.Error.from(throwable, cachedData)) }
        } catch (e: Exception) {
            emit(DataState.Error.from(e, cachedData))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchOpportunitiesFromNetwork(): Result<ConnectOpportunitiesResponseModel> {
        val user = requireNotNull(ConnectUserDatabaseUtil.getUser(context)) { "No Connect user found" }
        return ConnectNetworkClient.getInstance(context).getConnectOpportunities(user)
    }

    private suspend fun fetchLearningProgressFromNetwork(job: ConnectJobRecord): Result<LearningAppProgressResponseModel> {
        val user = requireNotNull(ConnectUserDatabaseUtil.getUser(context)) { "No Connect user found" }
        return ConnectNetworkClient.getInstance(context).getLearningProgress(user, job)
    }
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/repository/ConnectRepository.kt`
- [ ] Unit tests **pass** (Green): `./gradlew testDebugUnitTest --tests "org.commcare.connect.repository.ConnectRepositoryTest"`

#### Manual Verification:
- [ ] Repository emits `Loading` when no cached data exists
- [ ] Repository emits `Cached` when cached data exists in database
- [ ] Repository emits `Success` after successful network fetch
- [ ] Repository emits `Error` with cachedData when network fails but cache exists
- [ ] Database writes occur after successful network response
- [ ] Session-based policy prevents duplicate fetches within same app session
- [ ] Force refresh bypasses cache and always fetches from network


---

## Phase 3a: Job Opportunities ViewModel + Fragment Tests (Red)

### Overview
Write failing tests for `ConnectJobsListViewModel` and `ConnectJobsListsFragment`. The ViewModel doesn't exist yet; the fragment exists but hasn't been migrated to use the ViewModel. Commit the Red state before proceeding to Phase 3b.

### Changes Required

#### 1. ConnectJobsListViewModelTest
**File**: `app/unit-tests/src/org/commcare/connect/viewmodel/ConnectJobsListViewModelTest.kt` (new file)
**Dependencies**: mock `ConnectRepository`, `InstantTaskExecutorRule` for LiveData synchronous delivery

```kotlin
package org.commcare.connect.viewmodel

// Test method signatures — implement bodies in Phase 3b
class ConnectJobsListViewModelTest {

    // mock repository.getOpportunities to emit DataState.Loading then DataState.Success(jobList);
    // verify LiveData posts Loading then Success in order
    @Test fun testLoadOpportunities_postsLoadingThenSuccess(): Unit = TODO()

    // mock repository.getOpportunities to emit DataState.Loading then DataState.Error;
    // verify LiveData posts Error with the correct errorCode
    @Test fun testLoadOpportunities_postsError_onFailure(): Unit = TODO()

    // call loadOpportunities(forceRefresh = true);
    // verify repository.getOpportunities was called with forceRefresh = true
    @Test fun testLoadOpportunities_forceRefresh_passedToRepository(): Unit = TODO()
}
```

#### 2. ConnectJobsListsFragmentTest
**File**: `app/unit-tests/src/org/commcare/fragments/connect/ConnectJobsListsFragmentTest.kt` (new file)
**Type**: Robolectric, `@RunWith(AndroidJUnit4::class)`, `@Config(application = CommCareTestApplication::class)`
**Dependencies**: mock `ConnectJobsListViewModel`; post states via a `MutableLiveData` the fragment observes

```kotlin
package org.commcare.fragments.connect

// Test method signatures — implement bodies in Phase 3b
@RunWith(AndroidJUnit4::class)
@Config(application = CommCareTestApplication::class)
class ConnectJobsListsFragmentTest {

    // post DataState.Loading to the ViewModel LiveData; verify loading indicator is visible
    @Test fun testFragment_showsLoadingSpinner_onLoadingState(): Unit = TODO()

    // post DataState.Success with a non-empty job list; verify RecyclerView has the correct item count
    @Test fun testFragment_showsJobList_onSuccessState(): Unit = TODO()

    // post DataState.Cached with a non-empty job list; verify RecyclerView has the correct item count
    @Test fun testFragment_showsJobList_onCachedState(): Unit = TODO()

    // post DataState.Error with cachedData = null; verify error UI is shown and list is empty/hidden
    @Test fun testFragment_showsError_onErrorState(): Unit = TODO()

    // post DataState.Error with non-null cachedData; verify list is shown AND error toast appears
    @Test fun testFragment_showsCachedList_andErrorToast_whenErrorHasCachedData(): Unit = TODO()
}
```

### Success Criteria

#### Automated Verification:
- [ ] No linting errors: `ktlint app/unit-tests/src/org/commcare/connect/viewmodel/ConnectJobsListViewModelTest.kt app/unit-tests/src/org/commcare/fragments/connect/ConnectJobsListsFragmentTest.kt`
- [ ] Tests **fail** (Red — expected): `./gradlew testDebugUnitTest --tests "org.commcare.connect.viewmodel.ConnectJobsListViewModelTest"` and `--tests "org.commcare.fragments.connect.ConnectJobsListsFragmentTest"`
- [ ] Commit failing tests before proceeding to Phase 3b

---

## Phase 3b: Job Opportunities Migration (Green)

### Overview
Create `ConnectJobsListViewModel` and integrate it with the existing Java fragment `ConnectJobsListsFragment` to pass the Phase 3a tests. The fragment will observe LiveData from the ViewModel instead of using direct API callbacks.

### Changes Required

#### 1. collectInto Extension Function
**File**: `app/src/org/commcare/connect/viewmodel/ViewModelExtensions.kt` (new file)
**Purpose**: Eliminates the repeated `viewModelScope.launch / catch / collect / postValue` boilerplate across all Connect ViewModels.

```kotlin
package org.commcare.connect.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.connect.repository.DataState

// viewModelScope uses Dispatchers.Main.immediate — liveData.value = is safe (no postValue).
fun <T> ViewModel.collectInto(
    flow: Flow<DataState<T>>,
    liveData: MutableLiveData<DataState<T>>,
) {
    viewModelScope.launch {
        flow.catch { exception ->
            liveData.value = DataState.Error.from(exception)
        }.collect { dataState ->
            liveData.value = dataState
        }
    }
}
```

#### 2. ConnectJobsListViewModel
**File**: `app/src/org/commcare/connect/viewmodel/ConnectJobsListViewModel.kt` (new file)
**Purpose**: Manages UI state for job opportunities list

```kotlin
package org.commcare.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.repository.RefreshPolicy

class ConnectJobsListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConnectRepository.getInstance(application)

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

#### 2. Update ConnectJobsListsFragment
**File**: `app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java`
**Changes**: Replace direct API call with ViewModel observation

**Existing code (lines 81-106)**:
```java
public void refreshData() {
    ((ConnectActivity) requireActivity()).setWaitDialogEnabled(false);
    corruptJobs.clear();
    ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(getContext());
    new ConnectApiHandler<ConnectOpportunitiesResponseModel>(true, this) {

        @Override
        public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
            if (!isAdded()) {
                return;
            }

            Toast.makeText(requireContext(), PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), errorCode, t),Toast.LENGTH_LONG).show();
            navigateFailure();
        }

        @Override
        public void onSuccess(ConnectOpportunitiesResponseModel data) {
            corruptJobs = data.getCorruptJobs();

            if (isAdded()) {
                setJobListData(data.getValidJobs());
            }
        }
    }.getConnectOpportunities(requireContext(), user);
}
```

**New implementation**:
```java
package org.commcare.fragments.connect;

// Add new imports
import androidx.lifecycle.ViewModelProvider;
import org.commcare.connect.repository.DataState;
import org.commcare.connect.viewmodel.ConnectJobsListViewModel;

public class ConnectJobsListsFragment extends BaseConnectFragment<FragmentConnectJobsListBinding>
        implements RefreshableFragment {

    ArrayList<ConnectLoginJobListModel> inProgressJobs;
    ArrayList<ConnectLoginJobListModel> newJobs;
    ArrayList<ConnectLoginJobListModel> completedJobs;
    ArrayList<ConnectLoginJobListModel> corruptJobs = new ArrayList<>();

    private ConnectJobsListViewModel viewModel;

    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        requireActivity().setTitle(R.string.connect_title);

        viewModel = new ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(ConnectJobsListViewModel.class);

        observeOpportunities();
        viewModel.loadOpportunities(false);

        return view;
    }

    @Override
    public void refresh() {
        viewModel.loadOpportunities(true);
    }

    private void observeOpportunities() {
        viewModel.getOpportunities().observe(getViewLifecycleOwner(), dataState -> {
            if (!isAdded()) {
                return;
            }

            if (dataState instanceof DataState.Loading) {
                showLoading();
            } else if (dataState instanceof DataState.Cached) {
                hideLoading();
                hideError();
                DataState.Cached<List<ConnectJobRecord>> cached =
                    (DataState.Cached<List<ConnectJobRecord>>) dataState;
                corruptJobs.clear();
                setJobListData(cached.getData());

            } else if (dataState instanceof DataState.Success) {
                hideLoading();
                hideError();
                DataState.Success<List<ConnectJobRecord>> success =
                    (DataState.Success<List<ConnectJobRecord>>) dataState;
                corruptJobs.clear();
                setJobListData(success.getData());

            } else if (dataState instanceof DataState.Error) {
                hideLoading();
                DataState.Error<List<ConnectJobRecord>> error =
                    (DataState.Error<List<ConnectJobRecord>>) dataState;
                String errorMsg = PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), error.getErrorCode(), error.getThrowable());
                if (!errorMsg.isEmpty()) {
                    showError(errorMsg);
                }
                if (error.getCachedData() != null) {
                    setJobListData(error.getCachedData());
                } else {
                    navigateFailure();
                }
            }
        });
    }

    private void navigateFailure() {
        setJobListData(new ArrayList<>());
    }
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] Java compilation succeeds: `./gradlew compileDebugJava`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/viewmodel/*.kt`
- [ ] Unit tests **pass** (Green): `./gradlew testDebugUnitTest --tests "org.commcare.connect.viewmodel.ConnectJobsListViewModelTest"` and `--tests "org.commcare.fragments.connect.ConnectJobsListsFragmentTest"`

#### Manual Verification:
- [ ] Fragment displays cached job list immediately on screen entry
- [ ] Network fetch happens in background, UI updates when fresh data arrives
- [ ] Hybrid policy works: Second navigation in same session doesn't trigger network if cache < 1 minute old
- [ ] Hybrid policy works: New app session triggers network call even with fresh cache
- [ ] Hybrid policy works: Cache older than 1 minute triggers network call even in same session
- [ ] Force refresh (pull-to-refresh) bypasses cache and fetches from network
- [ ] Error toast displays when network fails
- [ ] Cached data still displays even when network fails
- [ ] Empty state shows when no cached data and network fails
- [ ] No duplicate network requests when rotating device


---

## Phase 4a: Learning Progress ViewModel + Fragment Tests (Red)

### Overview
Write failing tests for `ConnectLearningProgressViewModel` and `ConnectLearningProgressFragment`. The ViewModel doesn't exist yet; the fragment exists but hasn't been migrated. Commit the Red state before proceeding to Phase 4b.

### Changes Required

#### 1. ConnectLearningProgressViewModelTest
**File**: `app/unit-tests/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModelTest.kt` (new file)
**Dependencies**: mock `ConnectRepository`, `InstantTaskExecutorRule` for LiveData synchronous delivery

```kotlin
package org.commcare.connect.viewmodel

// Test method signatures — implement bodies in Phase 4b
class ConnectLearningProgressViewModelTest {

    // mock repository.getLearningProgress to emit DataState.Loading then DataState.Success(job);
    // verify LiveData posts Loading then Success in order
    @Test fun testLoadLearningProgress_postsLoadingThenSuccess(): Unit = TODO()

    // mock repository.getLearningProgress to emit DataState.Loading then DataState.Error;
    // verify LiveData posts Error with the correct errorCode
    @Test fun testLoadLearningProgress_postsError_onFailure(): Unit = TODO()
}
```

#### 2. ConnectLearningProgressFragmentTest
**File**: `app/unit-tests/src/org/commcare/fragments/connect/ConnectLearningProgressFragmentTest.kt` (new file)
**Type**: Robolectric, `@RunWith(AndroidJUnit4::class)`, `@Config(application = CommCareTestApplication::class)`
**Dependencies**: mock `ConnectLearningProgressViewModel`; post states via a `MutableLiveData` the fragment observes

```kotlin
package org.commcare.fragments.connect

// Test method signatures — implement bodies in Phase 4b
@RunWith(AndroidJUnit4::class)
@Config(application = CommCareTestApplication::class)
class ConnectLearningProgressFragmentTest {

    // post DataState.Loading; verify loading indicator is visible
    @Test fun testFragment_showsLoading_onLoadingState(): Unit = TODO()

    // post DataState.Success with a ConnectJobRecord; verify learning progress UI (percentage, modules) is updated
    @Test fun testFragment_updatesUI_onSuccessState(): Unit = TODO()

    // post DataState.Cached with a ConnectJobRecord; verify learning progress UI is updated
    @Test fun testFragment_updatesUI_onCachedState(): Unit = TODO()

    // post DataState.Error with non-null cachedData; verify error toast appears and cached UI is shown
    @Test fun testFragment_showsErrorToast_onErrorState_withCachedData(): Unit = TODO()
}
```

### Success Criteria

#### Automated Verification:
- [ ] No linting errors: `ktlint app/unit-tests/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModelTest.kt app/unit-tests/src/org/commcare/fragments/connect/ConnectLearningProgressFragmentTest.kt`
- [ ] Tests **fail** (Red — expected): `./gradlew testDebugUnitTest --tests "org.commcare.connect.viewmodel.ConnectLearningProgressViewModelTest"` and `--tests "org.commcare.fragments.connect.ConnectLearningProgressFragmentTest"`
- [ ] Commit failing tests before proceeding to Phase 4b

---

## Phase 4b: Learning Progress Endpoint + Migration (Green)

### Overview
Create `ConnectLearningProgressViewModel` and integrate it with `ConnectLearningProgressFragment` to pass the Phase 4a tests.

### Changes Required

#### 1. ConnectLearningProgressViewModel
**File**: `app/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModel.kt` (new file)
**Purpose**: Manages UI state for learning progress screen

```kotlin
package org.commcare.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.repository.RefreshPolicy

class ConnectLearningProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConnectRepository.getInstance(application)

    private val _learningProgress = MutableLiveData<DataState<ConnectJobRecord>>()
    val learningProgress: LiveData<DataState<ConnectJobRecord>> = _learningProgress

    fun loadLearningProgress(job: ConnectJobRecord) {
        collectInto(
            flow = repository.getLearningProgress(job, policy = RefreshPolicy.ALWAYS),
            liveData = _learningProgress,
        )
    }
}
```

#### 2. Update ConnectLearningProgressFragment
**File**: `app/src/org/commcare/fragments/connect/ConnectLearningProgressFragment.java`
**Changes**: Replace ConnectJobHelper callback with ViewModel observation

**Existing code (lines 81-93)**:
```java
private void refreshLearningData() {
    ConnectJobHelper.INSTANCE.updateLearningProgress(requireContext(), job, (success,error) -> {
        if (success && isAdded()) {
            updateLearningUI();
        } else if (!success && isAdded()) {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.connect_fetch_learning_progress_error),
                    Toast.LENGTH_LONG
            ).show();
        }
    });
}
```

**New implementation**:
```java
package org.commcare.fragments.connect;

// Add new imports
import androidx.lifecycle.ViewModelProvider;
import org.commcare.connect.repository.DataState;
import org.commcare.connect.viewmodel.ConnectLearningProgressViewModel;

public class ConnectLearningProgressFragment extends ConnectJobFragment<FragmentConnectLearningProgressBinding>
        implements RefreshableFragment {

    private boolean showAppLaunch = true;

    private ConnectLearningProgressViewModel viewModel;

    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (getArguments() != null) {
            showAppLaunch = getArguments().getBoolean(SHOW_LAUNCH_BUTTON, true);
        }

        requireActivity().setTitle(getString(R.string.connect_learn_title));

        viewModel = new ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(ConnectLearningProgressViewModel.class);

        observeLearningProgress();

        setupRefreshButton();
        populateJobCard(job);
        refreshLearningData();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PersonalIdManager.getInstance().isloggedIn()) {
            refreshLearningData();
        }
    }

    @Override
    public void refresh() {
        refreshLearningData();
    }

    private void setupRefreshButton() {
        getBinding().btnSync.setOnClickListener(v -> refreshLearningData());
    }

    private void observeLearningProgress() {
        viewModel.getLearningProgress().observe(getViewLifecycleOwner(), dataState -> {
            if (!isAdded()) {
                return;
            }

            if (dataState instanceof DataState.Loading) {
                showLoading();
            } else if (dataState instanceof DataState.Cached) {
                hideLoading();
                hideError();
                DataState.Cached<ConnectJobRecord> cached =
                    (DataState.Cached<ConnectJobRecord>) dataState;
                job = cached.getData();
                updateLearningUI();

            } else if (dataState instanceof DataState.Success) {
                hideLoading();
                hideError();
                DataState.Success<ConnectJobRecord> success =
                    (DataState.Success<ConnectJobRecord>) dataState;
                job = success.getData();
                updateLearningUI();

            } else if (dataState instanceof DataState.Error) {
                hideLoading();
                DataState.Error<ConnectJobRecord> error =
                    (DataState.Error<ConnectJobRecord>) dataState;
                String errorMsg = PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), error.getErrorCode(), error.getThrowable());
                if (!errorMsg.isEmpty()) {
                    showError(errorMsg);
                }
                if (error.getCachedData() != null) {
                    job = error.getCachedData();
                    updateLearningUI();
                }
            }
        });
    }

    private void refreshLearningData() {
        viewModel.loadLearningProgress(job);
    }
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] Java compilation succeeds: `./gradlew compileDebugJava`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/repository/*.kt app/src/org/commcare/connect/viewmodel/*.kt`
- [ ] Unit tests **pass** (Green): `./gradlew testDebugUnitTest --tests "org.commcare.connect.viewmodel.ConnectLearningProgressViewModelTest"` and `--tests "org.commcare.fragments.connect.ConnectLearningProgressFragmentTest"`

#### Manual Verification:
- [ ] Fragment displays cached learning progress immediately on screen entry
- [ ] Network fetch happens in background, UI updates when fresh data arrives
- [ ] Always-refresh policy works: Every navigation to screen triggers network call
- [ ] Sync button triggers network refresh
- [ ] Error toast displays when network fails
- [ ] Cached data still displays even when network fails
- [ ] Learning progress percentage, modules, and assessments update correctly
- [ ] Certificate view displays after passing assessment
- [ ] No duplicate network requests when rotating device or clicking sync rapidly


---

## Phase 5: Manual Testing & Validation

### Overview
End-to-end manual verification of the complete offline-first architecture across both migrated fragments.

### Manual Testing Checklist

**Test Scenarios**:

1. **Session-based caching (ConnectJobsListsFragment)**:
   - [ ] Open app, navigate to job list - network call happens
   - [ ] Back out, navigate to job list again - NO network call (cached)
   - [ ] Close app completely, reopen - network call happens (new session)
   - [ ] Pull to refresh - network call happens (force refresh)

2. **Always refresh (ConnectLearningProgressFragment)**:
   - [ ] Navigate to learning progress - network call happens
   - [ ] Back out, navigate to learning progress again - network call happens again
   - [ ] Click sync button - network call happens
   - [ ] Rotate device - network call happens once (not duplicated)

3. **Offline-first UX**:
   - [ ] With cached data: Navigate to screen, cached data appears instantly, then updates with fresh data
   - [ ] Without cached data: Navigate to screen, loading state (or empty state), then fresh data appears
   - [ ] Airplane mode with cached data: Cached data displays, error toast shows, data remains visible
   - [ ] Airplane mode without cached data: Empty state displays with error toast

4. **Request deduplication**:
   - [ ] Rapidly click sync button on learning progress - only one network call
   - [ ] Rotate device during network fetch - no duplicate request
   - [ ] Open two instances of same fragment (if possible) - requests are shared

5. **Database consistency**:
   - [ ] After successful network fetch, database contains new data
   - [ ] After failed network fetch, database retains old data
   - [ ] Timestamp tracking updates correctly after successful fetch

6. **Error handling**:
   - [ ] Network timeout shows appropriate error message
   - [ ] Server error (500) shows error toast with cached data still visible
   - [ ] Malformed response handled gracefully

### Success Criteria

#### Manual Verification:
- [ ] All 24 manual test scenarios pass
- [ ] No regressions in other Connect screens
- [ ] App doesn't crash during any test scenario
- [ ] Memory usage is reasonable (no leaks from coroutines)
- [ ] Performance is acceptable (no noticeable lag)


---

## Performance Considerations

**Memory**:
- Repository and ViewModels use application scope, but they're lightweight singletons
- `viewModelScope` coroutines (Flow collection) are cleaned up on ViewModel destruction; this does not cancel in-flight network requests
- `ConnectRequestManager` cleans up `inFlightRequests` map after the application-scoped request completes

**Network**:
- Request deduplication prevents duplicate concurrent calls
- Session-based caching reduces unnecessary network traffic
- Offline-first pattern provides instant UI response

**Database**:
- Maintains existing immediate-write pattern (no additional overhead)
- Composite jobs loaded from database are already optimized

**Potential Issues**:
- Large job lists could cause memory pressure when emitting via Flow - monitor with 100+ jobs
- Concurrent requests to different endpoints could exhaust thread pool - Dispatchers.IO handles this

## Migration Notes

**Backward Compatibility**:
- Old `ConnectApiHandler` callback pattern remains available for gradual migration
- New `ConnectApiClient` coroutine-based client coexists alongside old code
- Other Connect fragments continue to work unchanged
- No database schema changes required
- No API contract changes


**Session Initialization**:
Add to `CommCareApplication.onCreate()` (not Activity — Activity fires on rotation):
```kotlin
ConnectSyncPreferences.getInstance(this).markSessionStart()
```

## References

- Original research: `docs/claude/research/2026-02-23-offline-first-network-architecture.md`
- TDD iteration design: `docs/claude/plans/2026-03-09-offline-first-tdd-iteration-design.md`
- Best existing pattern: `app/src/org/commcare/activities/connect/viewmodel/PushNotificationViewModel.kt:31-70`
- Current sync tracking: `app/src/org/commcare/utils/SyncDetailCalculations.java`
- CommCareTask pattern: `app/src/org/commcare/tasks/templates/CommCareTask.java`

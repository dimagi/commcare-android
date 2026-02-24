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

## Current State Analysis

**Network Architecture**: Callback-based `ConnectApiHandler` pattern with no centralized repository
- Fragments create anonymous `ConnectApiHandler` instances
- API calls: `getConnectOpportunities()` and `getLearningAppProgress()`
- Database writes happen in callbacks via `ConnectJobUtils`
- No request deduplication or in-flight request tracking
- Network-first with database fallback on error

**Existing Patterns**:
- One good example: `PushNotificationViewModel.kt:31-70` uses cache-then-network with LiveData
- Some ViewModels exist but not standardized across Connect features

**Key Files**:
- `app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java:81-106` - Opportunities API call
- `app/src/org/commcare/fragments/connect/ConnectLearningProgressFragment.java:81-93` - Learning progress API call
- `app/src/org/commcare/connect/ConnectJobHelper.kt:62-89` - Learning progress helper
- `app/src/org/commcare/connect/network/connect/ConnectApiHandler.kt` - Current API handler
- `app/src/org/commcare/activities/connect/viewmodel/PushNotificationViewModel.kt` - Best existing pattern

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

### Changes Required

#### 1. DataState Sealed Class
**File**: `app/src/org/commcare/connect/repository/DataState.kt` (new file)
**Purpose**: Type-safe representation of data loading states

```kotlin
package org.commcare.connect.repository

import java.util.Date

/**
 * Represents the state of data in the offline-first architecture.
 * Emitted by Repository Flow and converted to LiveData in ViewModels.
 */
sealed class DataState<out T> {
    /**
     * Initial loading state with no data available yet.
     */
    object Loading : DataState<Nothing>()

    /**
     * Cached data available from local storage.
     * @param data The cached data
     * @param timestamp When this data was last fetched from network
     */
    data class Cached<T>(val data: T, val timestamp: Date) : DataState<T>()

    /**
     * Fresh data successfully fetched from network.
     * @param data The fresh data
     */
    data class Success<T>(val data: T) : DataState<T>()

    /**
     * Error occurred during network fetch.
     * @param error The error message
     * @param throwable The exception that caused the error
     * @param cachedData Optional cached data to display despite error
     */
    data class Error<T>(
        val error: String,
        val throwable: Throwable? = null,
        val cachedData: T? = null
    ) : DataState<T>()
}
```

#### 2. RefreshPolicy Enum
**File**: `app/src/org/commcare/connect/repository/RefreshPolicy.kt` (new file)
**Purpose**: Defines when data should be refreshed from network

```kotlin
package org.commcare.connect.repository

/**
 * Defines the refresh policy for repository data fetches.
 */
enum class RefreshPolicy(val timeThresholdMs: Long = 0) {
    /**
     * Always fetch from network, regardless of cache age or session.
     * Use for data that changes frequently (e.g., learning progress).
     */
    ALWAYS,

    /**
     * Hybrid policy: Fetch from network if EITHER:
     * 1. A new app session has started since last sync, OR
     * 2. Cache is older than the configured time threshold
     *
     * Use for data that should be fresh per session but also has a max staleness tolerance.
     * Example: Job opportunities (refresh per session OR if older than 5 minutes)
     *
     * @param timeThresholdMs Maximum cache age in milliseconds before forcing refresh
     */
    SESSION_AND_TIME_BASED(timeThresholdMs = 60_000) // Default: 1 minute
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/repository/*.kt`

#### Manual Verification:
- [ ] `DataState` sealed class hierarchy is correct (4 states: Loading, Cached, Success, Error)
- [ ] All DataState states have appropriate properties
- [ ] RefreshPolicy enum has 3 values with clear documentation

---

## Phase 1: Core Infrastructure

### Overview
Implement the foundational components for sync tracking and request deduplication. These are the "plumbing" that Repository will use.

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
        // Initialize session start time if not already set
        if (!prefs.contains(KEY_SESSION_START)) {
            markSessionStart()
        }
    }

    /**
     * Marks the start of a new app session.
     * Called on app launch.
     */
    fun markSessionStart() {
        prefs.edit()
            .putLong(KEY_SESSION_START, Date().time)
            .apply()
    }

    /**
     * Gets the timestamp when the current session started.
     */
    fun getSessionStartTime(): Date {
        val timestamp = prefs.getLong(KEY_SESSION_START, Date().time)
        return Date(timestamp)
    }

    /**
     * Stores the last successful sync time for an endpoint.
     * @param endpoint The API endpoint (e.g., "/opportunities", "/learning_progress/123")
     */
    fun storeLastSyncTime(endpoint: String) {
        val key = KEY_LAST_SYNC_PREFIX + endpoint.replace("/", "_")
        prefs.edit()
            .putLong(key, Date().time)
            .apply()
    }

    /**
     * Gets the last successful sync time for an endpoint.
     * @param endpoint The API endpoint
     * @return The last sync time, or null if never synced
     */
    fun getLastSyncTime(endpoint: String): Date? {
        val key = KEY_LAST_SYNC_PREFIX + endpoint.replace("/", "_")
        val timestamp = prefs.getLong(key, -1)
        return if (timestamp == -1L) null else Date(timestamp)
    }

    /**
     * Checks if data should be refreshed based on policy.
     * @param endpoint The API endpoint
     * @param policy The refresh policy to apply
     * @return true if data should be refreshed from network
     */
    fun shouldRefresh(
        endpoint: String,
        policy: RefreshPolicy
    ): Boolean {
        return when (policy) {
            RefreshPolicy.ALWAYS -> true

            RefreshPolicy.SESSION_AND_TIME_BASED -> {
                val lastSync = getLastSyncTime(endpoint) ?: return true
                val sessionStart = getSessionStartTime()

                // Refresh if EITHER condition is true:
                // 1. New session started since last sync
                val isNewSession = lastSync.before(sessionStart)

                // 2. Cache is older than time threshold
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
- Call `markSessionStart()` in `CommCareApplication.onCreate()` or main activity launch

#### 2. ConnectRequestManager
**File**: `app/src/org/commcare/connect/repository/ConnectRequestManager.kt` (new file)
**Purpose**: Deduplicates in-flight network requests across ViewModels

```kotlin
package org.commcare.connect.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages in-flight network requests to prevent duplicate concurrent calls.
 * Singleton pattern ensures requests are shared across all ViewModels.
 */
object ConnectRequestManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Tracks in-flight requests by URL.
     * Multiple callers requesting the same URL will share the same Deferred result.
     */
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Result<Any>>>()

    /**
     * Executes a network request with deduplication.
     * If a request for the same URL is already in progress, waits for that result instead.
     *
     * @param url The unique identifier for this request (typically the API endpoint)
     * @param request The suspend function that performs the actual network call
     * @return Result of the network call
     */
    suspend fun <T> executeRequest(
        url: String,
        request: suspend () -> Result<T>
    ): Result<T> {
        // Check if there's already a request in flight for this URL
        val existingRequest = inFlightRequests[url]

        if (existingRequest != null) {
            // Wait for the existing request to complete
            @Suppress("UNCHECKED_CAST")
            return existingRequest.await() as Result<T>
        }

        // Create a new deferred for this request
        val deferred = CompletableDeferred<Result<Any>>()
        inFlightRequests[url] = deferred

        return try {
            // Execute the actual network request
            val result = request()

            // Complete the deferred with the result
            deferred.complete(result as Result<Any>)

            result
        } catch (e: Exception) {
            // Complete the deferred with failure
            val failure = Result.failure<Any>(e)
            deferred.complete(failure)

            Result.failure(e)
        } finally {
            // Remove from in-flight map
            inFlightRequests.remove(url)
        }
    }

    /**
     * Checks if a request is currently in progress for the given URL.
     * Useful for UI loading indicators.
     */
    fun isRequestInProgress(url: String): Boolean {
        return inFlightRequests.containsKey(url)
    }

    /**
     * Cancels all in-flight requests (for testing or app shutdown).
     */
    fun cancelAll() {
        inFlightRequests.values.forEach {
            it.cancel()
        }
        inFlightRequests.clear()
    }
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/repository/*.kt`

#### Manual Verification:
- [ ] `ConnectSyncPreferences` singleton initializes correctly
- [ ] Session start time is set on first access
- [ ] `shouldRefresh()` logic correctly implements hybrid policy (session OR time threshold)
- [ ] Hybrid policy refreshes on new session even with fresh cache
- [ ] Hybrid policy refreshes on stale cache even in same session
- [ ] `ConnectRequestManager` deduplication logic is thread-safe (ConcurrentHashMap)
- [ ] In-flight requests are properly cleaned up after completion

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 1.5: Coroutine-Based Network Client

### Overview
Create a new pure coroutine-based network client (`ConnectApiClient` with suspend functions) that uses Retrofit suspend functions directly without any callbacks. This provides a modern, coroutine-first API alongside the existing callback-based `ConnectApiHandler` for gradual migration.

**Key principle**: This new client does NOT wrap or depend on the old callback-based `ConnectApiHandler`. It's a parallel implementation using Retrofit suspend functions.

### Changes Required

#### 1. Retrofit API Service with Suspend Functions
**File**: `app/src/org/commcare/connect/network/ApiService.java` (modifications)
**Purpose**: Add suspend function variants of existing API methods

```kotlin
// Add these suspend function variants to the existing ApiService interface
@GET("/api/v1/connect/opportunities")
suspend fun getConnectOpportunitiesSuspend(
    @Header("Authorization") authorization: String,
    @QueryMap params: Map<String, String>
): Response<JsonObject>

@GET("/api/v1/connect/learning_progress/{jobId}")
suspend fun getLearningProgressSuspend(
    @Path("jobId") jobId: String,
    @Header("Authorization") authorization: String,
    @QueryMap params: Map<String, String>
): Response<JsonObject>
```

**Note**: These coexist with existing callback-based methods. Old code continues using the callback variants.

#### 2. ConnectApiClient (Coroutines)
**File**: `app/src/org/commcare/connect/network/connect/ConnectApiClient.kt` (new file)
**Purpose**: Pure suspend function API client without callbacks

```kotlin
package org.commcare.connect.network.connect

import android.content.Context
import com.google.gson.JsonObject
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.ApiService
import org.commcare.connect.network.ConnectNetworkHelper
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import org.commcare.connect.network.connect.parser.ConnectOpportunitiesParser
import org.commcare.connect.network.connect.parser.LearningAppProgressResponseParser
import retrofit2.Response

/**
 * Pure coroutine-based API client for Connect network calls.
 * Uses Retrofit suspend functions directly - no callbacks.
 *
 * This is a parallel implementation alongside the callback-based ConnectApiHandler.
 * Gradually migrate to this approach for new code.
 */
class ConnectApiClient(private val context: Context) {

    companion object {
        @Volatile
        private var instance: ConnectApiClient? = null

        fun getInstance(context: Context): ConnectApiClient {
            return instance ?: synchronized(this) {
                instance ?: ConnectApiClient(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val apiService: ApiService by lazy {
        org.commcare.connect.network.ConnectApiClient.getRetrofitClient().create(ApiService::class.java)
    }

    /**
     * Fetches job opportunities from the Connect API.
     * Pure suspend function - no callbacks involved.
     *
     * @param user The Connect user credentials
     * @return Result containing ConnectOpportunitiesResponseModel or exception
     */
    suspend fun getConnectOpportunities(
        user: ConnectUserRecord
    ): Result<ConnectOpportunitiesResponseModel> {
        return try {
            val authHeader = ConnectNetworkHelper.getAuthorizationHeader(user)
            val params = ConnectNetworkHelper.buildOpportunitiesParams(context)

            val response = apiService.getConnectOpportunitiesSuspend(authHeader, params)

            if (response.isSuccessful && response.body() != null) {
                val jsonBody = response.body()!!
                val parser = ConnectOpportunitiesParser(context)
                val responseModel = parser.parse(response.code(), jsonBody, null)
                Result.success(responseModel)
            } else {
                Result.failure(Exception("API error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches learning progress for a specific job from the Connect API.
     * Pure suspend function - no callbacks involved.
     *
     * @param user The Connect user credentials
     * @param job The job to fetch learning progress for
     * @return Result containing LearningAppProgressResponseModel or exception
     */
    suspend fun getLearningProgress(
        user: ConnectUserRecord,
        job: ConnectJobRecord
    ): Result<LearningAppProgressResponseModel> {
        return try {
            val authHeader = ConnectNetworkHelper.getAuthorizationHeader(user)
            val params = ConnectNetworkHelper.buildLearningProgressParams(context, job)

            val response = apiService.getLearningProgressSuspend(
                jobId = job.jobUUID,
                authorization = authHeader,
                params = params
            )

            if (response.isSuccessful && response.body() != null) {
                val jsonBody = response.body()!!
                val parser = LearningAppProgressResponseParser(context)
                val responseModel = parser.parse(response.code(), jsonBody, job)
                Result.success(responseModel)
            } else {
                Result.failure(Exception("API error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Key Design Points**:
- **No callbacks**: Pure suspend functions returning `Result<T>`
- **Direct Retrofit usage**: Calls `apiService` suspend functions directly
- **Parallel to old code**: Existing `ConnectApiHandler` remains untouched
- **Singleton pattern**: Application-scoped instance
- **Error handling**: Uses Kotlin `Result` type for success/failure

#### 3. Helper Methods (if needed)
**File**: `app/src/org/commcare/connect/network/ConnectNetworkHelper.java` (add methods if missing)

Ensure these helper methods exist for building request headers and params:
- `getAuthorizationHeader(user)` - Returns auth header string
- `buildOpportunitiesParams(context)` - Returns query params map
- `buildLearningProgressParams(context, job)` - Returns query params map

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/network/connect/ConnectApiClient.kt`
- [ ] Unit tests pass: `./gradlew testDebugUnitTest --tests ConnectApiClientTest`

#### Manual Verification:
- [ ] `ConnectApiClient.getConnectOpportunities()` successfully fetches and parses opportunities
- [ ] `ConnectApiClient.getLearningProgress()` successfully fetches and parses learning progress
- [ ] Suspend functions can be called from coroutine scope without blocking
- [ ] Error responses are properly wrapped in `Result.failure`
- [ ] Network errors (timeouts, connection failures) are handled gracefully
- [ ] Old callback-based `ConnectApiHandler` continues to work unchanged
- [ ] Both old and new clients can coexist in the same codebase

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 2: Repository Foundation + Opportunities Endpoint

### Overview
Create the `ConnectRepository` class and implement the first endpoint (`getOpportunities`) with session-based refresh policy. This establishes the repository pattern and demonstrates the offline-first flow.

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
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.connect.ConnectApiHandler
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import org.commcare.connect.network.connect.models.LearningAppProgressResponseModel
import kotlin.coroutines.resume

/**
 * Repository for Connect data.
 * Provides offline-first data access with Flow emissions.
 *
 * Uses application-scoped coroutines so requests survive fragment lifecycle.
 */
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

    /**
     * Gets job opportunities with offline-first pattern.
     *
     * Flow emissions:
     * 1. Loading (if no cached data)
     * 2. Cached (if cached data exists)
     * 3. Success (fresh data from network) OR Error (network failed)
     *
     * @param forceRefresh If true, ignores cache and always fetches from network
     * @param policy Refresh policy (default: SESSION_AND_TIME_BASED with 1 minute threshold)
     * @return Flow of DataState emissions
     */
    fun getOpportunities(
        forceRefresh: Boolean = false,
        policy: RefreshPolicy = RefreshPolicy.SESSION_AND_TIME_BASED(60_000)
    ): Flow<DataState<List<ConnectJobRecord>>> = flow {
        // Step 1: Load cached data from database
        val cachedJobs = ConnectJobUtils.getCompositeJobs(
            context,
            ConnectJobRecord.STATUS_ALL_JOBS,
            null
        )

        val lastSyncTime = syncPrefs.getLastSyncTime(ENDPOINT_OPPORTUNITIES)

        // Step 2: Emit cached data if available
        if (cachedJobs.isNotEmpty() && lastSyncTime != null) {
            emit(DataState.Cached(cachedJobs, lastSyncTime))
        } else {
            emit(DataState.Loading)
        }

        // Step 3: Determine if we should refresh from network
        val shouldRefresh = forceRefresh ||
                syncPrefs.shouldRefresh(ENDPOINT_OPPORTUNITIES, policy)

        if (!shouldRefresh) {
            // Cache is fresh enough, no need to fetch from network
            return@flow
        }

        // Step 4: Fetch from network with deduplication
        try {
            val result = ConnectRequestManager.executeRequest(ENDPOINT_OPPORTUNITIES) {
                fetchOpportunitiesFromNetwork()
            }

            result.onSuccess { responseModel ->
                // Step 5: Write to database
                val validJobs = responseModel.validJobs
                for (job in validJobs) {
                    ConnectJobUtils.upsertJob(context, job)
                }

                // Step 6: Update sync timestamp
                syncPrefs.storeLastSyncTime(ENDPOINT_OPPORTUNITIES)

                // Step 7: Emit success with fresh data
                emit(DataState.Success(validJobs))
            }.onFailure { throwable ->
                // Step 8: Emit error (with cached data if available)
                val errorMessage = throwable.message ?: "Unknown error fetching opportunities"
                emit(DataState.Error(
                    error = errorMessage,
                    throwable = throwable,
                    cachedData = if (cachedJobs.isNotEmpty()) cachedJobs else null
                ))
            }
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            emit(DataState.Error(
                error = errorMessage,
                throwable = e,
                cachedData = if (cachedJobs.isNotEmpty()) cachedJobs else null
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches opportunities from network using new coroutine-based client.
     * Pure suspend function - no callbacks involved.
     */
    private suspend fun fetchOpportunitiesFromNetwork(): Result<ConnectOpportunitiesResponseModel> {
        val user = ConnectUserDatabaseUtil.getUser(context)
        val apiClient = ConnectApiClient.getInstance(context)
        return apiClient.getConnectOpportunities(user)
    }
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/repository/ConnectRepository.kt`
- [ ] Unit tests pass: `./gradlew testDebugUnitTest --tests ConnectRepositoryTest`

#### Manual Verification:
- [ ] Repository emits `Loading` when no cached data exists
- [ ] Repository emits `Cached` when cached data exists in database
- [ ] Repository emits `Success` after successful network fetch
- [ ] Repository emits `Error` with cachedData when network fails but cache exists
- [ ] Database writes occur after successful network response
- [ ] Session-based policy prevents duplicate fetches within same app session
- [ ] Force refresh bypasses cache and always fetches from network

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 3: Job Opportunities Migration

### Overview
Create `ConnectJobsListViewModel` and integrate it with the existing Java fragment `ConnectJobsListsFragment`. The fragment will observe LiveData from the ViewModel instead of using direct API callbacks.

### Changes Required

#### 1. ConnectJobsListViewModel
**File**: `app/src/org/commcare/connect/viewmodel/ConnectJobsListViewModel.kt` (new file)
**Purpose**: Manages UI state for job opportunities list

```kotlin
package org.commcare.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.repository.RefreshPolicy

/**
 * ViewModel for ConnectJobsListsFragment.
 * Manages job opportunities data with hybrid session-and-time-based refresh policy.
 */
class ConnectJobsListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConnectRepository.getInstance(application)

    private val _opportunities = MutableLiveData<DataState<List<ConnectJobRecord>>>()
    val opportunities: LiveData<DataState<List<ConnectJobRecord>>> = _opportunities

    /**
     * Loads job opportunities from repository.
     * Uses hybrid refresh: fetches from network if new session OR cache older than 1 minute.
     *
     * @param forceRefresh If true, ignores cache and always fetches from network
     */
    fun loadOpportunities(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.getOpportunities(
                forceRefresh = forceRefresh,
                policy = RefreshPolicy.SESSION_AND_TIME_BASED(60_000) // 1 minute
            ).catch { exception ->
                // Handle any unexpected Flow errors
                _opportunities.postValue(
                    DataState.Error(
                        error = exception.message ?: "Unknown error",
                        throwable = exception
                    )
                )
            }.collect { dataState ->
                _opportunities.postValue(dataState)
            }
        }
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

    // Add ViewModel field
    private ConnectJobsListViewModel viewModel;

    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        requireActivity().setTitle(R.string.connect_title);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(ConnectJobsListViewModel.class);

        // Observe opportunities LiveData
        observeOpportunities();

        // Load opportunities (will use cache if available)
        viewModel.loadOpportunities(false);

        return view;
    }

    @Override
    public void refresh() {
        // Force refresh from network
        viewModel.loadOpportunities(true);
    }

    /**
     * Observes opportunities LiveData from ViewModel.
     * Handles all DataState emissions: Loading, Cached, Success, Error.
     */
    private void observeOpportunities() {
        viewModel.getOpportunities().observe(getViewLifecycleOwner(), dataState -> {
            if (!isAdded()) {
                return;
            }

            ((ConnectActivity) requireActivity()).setWaitDialogEnabled(false);

            if (dataState instanceof DataState.Loading) {
                // Show loading indicator if needed
                // For now, we don't show anything since cached data will come next

            } else if (dataState instanceof DataState.Cached) {
                DataState.Cached<List<ConnectJobRecord>> cached =
                    (DataState.Cached<List<ConnectJobRecord>>) dataState;

                // Display cached data immediately
                corruptJobs.clear();
                setJobListData(cached.getData());

            } else if (dataState instanceof DataState.Success) {
                DataState.Success<List<ConnectJobRecord>> success =
                    (DataState.Success<List<ConnectJobRecord>>) dataState;

                // Update UI with fresh data from network
                corruptJobs.clear();
                setJobListData(success.getData());

            } else if (dataState instanceof DataState.Error) {
                DataState.Error<List<ConnectJobRecord>> error =
                    (DataState.Error<List<ConnectJobRecord>>) dataState;

                // Show error toast
                Toast.makeText(
                    requireContext(),
                    error.getError(),
                    Toast.LENGTH_LONG
                ).show();

                // If we have cached data despite error, show it
                if (error.getCachedData() != null) {
                    setJobListData(error.getCachedData());
                } else {
                    // No cached data, show empty state
                    navigateFailure();
                }
            }
        });
    }

    // Keep existing navigateFailure() method but it now just shows empty state
    private void navigateFailure() {
        setJobListData(new ArrayList<>());
    }

    // All other methods remain unchanged
    // ...
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] Java compilation succeeds: `./gradlew compileDebugJava`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/viewmodel/*.kt`
- [ ] Unit tests pass: `./gradlew testDebugUnitTest --tests ConnectJobsListViewModelTest`

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

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 4: Learning Progress Endpoint + Migration

### Overview
Add `getLearningProgress()` to Repository with always-refresh policy, create `ConnectLearningProgressViewModel`, and integrate with `ConnectLearningProgressFragment`.

### Changes Required

#### 1. Add getLearningProgress to ConnectRepository
**File**: `app/src/org/commcare/connect/repository/ConnectRepository.kt`
**Changes**: Add new method for learning progress endpoint

```kotlin
/**
 * Gets learning progress for a specific job with offline-first pattern.
 *
 * Flow emissions:
 * 1. Loading (if no cached data)
 * 2. Cached (if cached job data exists)
 * 3. Success (fresh data from network) OR Error (network failed)
 *
 * @param job The job to fetch learning progress for
 * @param forceRefresh If true, ignores cache and always fetches from network
 * @param policy Refresh policy (default: ALWAYS)
 * @return Flow of DataState emissions
 */
fun getLearningProgress(
    job: ConnectJobRecord,
    forceRefresh: Boolean = false,
    policy: RefreshPolicy = RefreshPolicy.ALWAYS
): Flow<DataState<ConnectJobRecord>> = flow {
    val endpoint = ENDPOINT_LEARNING_PREFIX + job.jobUUID

    // Step 1: Load cached job from database
    val cachedJob = ConnectJobUtils.getCompositeJob(context, job.jobUUID)

    val lastSyncTime = syncPrefs.getLastSyncTime(endpoint)

    // Step 2: Emit cached data if available
    if (cachedJob != null && lastSyncTime != null) {
        emit(DataState.Cached(cachedJob, lastSyncTime))
    } else {
        emit(DataState.Loading)
    }

    // Step 3: Determine if we should refresh from network
    val shouldRefresh = forceRefresh ||
            syncPrefs.shouldRefresh(endpoint, policy)

    if (!shouldRefresh) {
        // Cache is fresh enough, no need to fetch from network
        return@flow
    }

    // Step 4: Fetch from network with deduplication
    try {
        val result = ConnectRequestManager.executeRequest(endpoint) {
            fetchLearningProgressFromNetwork(job)
        }

        result.onSuccess { responseModel ->
            // Step 5: Update job with learning progress
            job.learnings = responseModel.connectJobLearningRecords
            job.completedLearningModules = responseModel.connectJobLearningRecords.size
            job.assessments = responseModel.connectJobAssessmentRecords

            // Step 6: Write to database
            ConnectJobUtils.updateJobLearnProgress(context, job)

            // Step 7: Update sync timestamp
            syncPrefs.storeLastSyncTime(endpoint)

            // Step 8: Reload updated job from database
            val updatedJob = ConnectJobUtils.getCompositeJob(context, job.jobUUID)
                ?: job

            // Step 9: Emit success with fresh data
            emit(DataState.Success(updatedJob))
        }.onFailure { throwable ->
            // Step 10: Emit error (with cached data if available)
            val errorMessage = throwable.message ?: "Unknown error fetching learning progress"
            emit(DataState.Error(
                error = errorMessage,
                throwable = throwable,
                cachedData = cachedJob
            ))
        }
    } catch (e: Exception) {
        val errorMessage = e.message ?: "Unknown error"
        emit(DataState.Error(
            error = errorMessage,
            throwable = e,
            cachedData = cachedJob
        ))
    }
}.flowOn(Dispatchers.IO)

/**
 * Fetches learning progress from network using new coroutine-based client.
 * Pure suspend function - no callbacks involved.
 */
private suspend fun fetchLearningProgressFromNetwork(
    job: ConnectJobRecord
): Result<LearningAppProgressResponseModel> {
    val user = ConnectUserDatabaseUtil.getUser(context)
    val apiClient = ConnectApiClient.getInstance(context)
    return apiClient.getLearningProgress(user, job)
}
```

#### 2. ConnectLearningProgressViewModel
**File**: `app/src/org/commcare/connect/viewmodel/ConnectLearningProgressViewModel.kt` (new file)
**Purpose**: Manages UI state for learning progress screen

```kotlin
package org.commcare.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.repository.RefreshPolicy

/**
 * ViewModel for ConnectLearningProgressFragment.
 * Manages learning progress data with always-refresh policy.
 */
class ConnectLearningProgressViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConnectRepository.getInstance(application)

    private val _learningProgress = MutableLiveData<DataState<ConnectJobRecord>>()
    val learningProgress: LiveData<DataState<ConnectJobRecord>> = _learningProgress

    /**
     * Loads learning progress for a job from repository.
     * Uses always-refresh policy: fetches from network on every call.
     *
     * @param job The job to load learning progress for
     */
    fun loadLearningProgress(job: ConnectJobRecord) {
        viewModelScope.launch {
            repository.getLearningProgress(
                job = job,
                forceRefresh = false,
                policy = RefreshPolicy.ALWAYS
            ).catch { exception ->
                // Handle any unexpected Flow errors
                _learningProgress.postValue(
                    DataState.Error(
                        error = exception.message ?: "Unknown error",
                        throwable = exception
                    )
                )
            }.collect { dataState ->
                _learningProgress.postValue(dataState)
            }
        }
    }
}
```

#### 3. Update ConnectLearningProgressFragment
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

    // Add ViewModel field
    private ConnectLearningProgressViewModel viewModel;

    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (getArguments() != null) {
            showAppLaunch = getArguments().getBoolean(SHOW_LAUNCH_BUTTON, true);
        }

        requireActivity().setTitle(getString(R.string.connect_learn_title));

        // Initialize ViewModel
        viewModel = new ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(ConnectLearningProgressViewModel.class);

        // Observe learning progress LiveData
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

    /**
     * Observes learning progress LiveData from ViewModel.
     * Handles all DataState emissions: Loading, Cached, Success, Error.
     */
    private void observeLearningProgress() {
        viewModel.getLearningProgress().observe(getViewLifecycleOwner(), dataState -> {
            if (!isAdded()) {
                return;
            }

            if (dataState instanceof DataState.Loading) {
                // Show loading indicator if needed
                // Currently we don't show a loading state

            } else if (dataState instanceof DataState.Cached) {
                DataState.Cached<ConnectJobRecord> cached =
                    (DataState.Cached<ConnectJobRecord>) dataState;

                // Update job reference and refresh UI
                job = cached.getData();
                updateLearningUI();

            } else if (dataState instanceof DataState.Success) {
                DataState.Success<ConnectJobRecord> success =
                    (DataState.Success<ConnectJobRecord>) dataState;

                // Update job reference and refresh UI with fresh data
                job = success.getData();
                updateLearningUI();

            } else if (dataState instanceof DataState.Error) {
                DataState.Error<ConnectJobRecord> error =
                    (DataState.Error<ConnectJobRecord>) dataState;

                // Show error toast
                Toast.makeText(
                    requireContext(),
                    getString(R.string.connect_fetch_learning_progress_error),
                    Toast.LENGTH_LONG
                ).show();

                // If we have cached data despite error, use it
                if (error.getCachedData() != null) {
                    job = error.getCachedData();
                    updateLearningUI();
                }
            }
        });
    }

    private void refreshLearningData() {
        // Load learning progress via ViewModel
        viewModel.loadLearningProgress(job);
    }

    // All other methods remain unchanged
    // ...
}
```

### Success Criteria

#### Automated Verification:
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] Java compilation succeeds: `./gradlew compileDebugJava`
- [ ] No linting errors: `ktlint app/src/org/commcare/connect/repository/*.kt app/src/org/commcare/connect/viewmodel/*.kt`
- [ ] Unit tests pass: `./gradlew testDebugUnitTest --tests ConnectLearningProgressViewModelTest`

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

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding to the next phase.

---

## Phase 5: Testing & Validation

### Overview
Comprehensive testing of the new offline-first architecture including unit tests, integration tests, and manual end-to-end testing.

### Changes Required

#### 1. Unit Tests for ConnectSyncPreferences
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
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
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

#### 2. Unit Tests for ConnectRequestManager
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
}
```

#### 3. Manual Testing Checklist

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

#### Automated Verification:
- [ ] All unit tests pass: `./gradlew testDebugUnitTest`
- [ ] No linting errors: `ktlint "app/src/org/commcare/connect/**/*.kt"`
- [ ] Kotlin compilation succeeds: `./gradlew compileDebugKotlin`
- [ ] Java compilation succeeds: `./gradlew compileDebugJava`

#### Manual Verification:
- [ ] All 24 manual test scenarios pass
- [ ] No regressions in other Connect screens
- [ ] App doesn't crash during any test scenario
- [ ] Memory usage is reasonable (no leaks from coroutines)
- [ ] Performance is acceptable (no noticeable lag)

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful.

---

## Testing Strategy

### Unit Tests
- **ConnectSyncPreferencesTest**: Tests all refresh policy logic
- **ConnectRequestManagerTest**: Tests deduplication and concurrency
- **ConnectRepositoryTest**: Tests Flow emissions and database writes (mocked)
- **ConnectJobsListViewModelTest**: Tests ViewModel state transformations
- **ConnectLearningProgressViewModelTest**: Tests ViewModel with always-refresh policy

### Integration Tests
Not required for this phase since we're maintaining existing database and network patterns.

### Manual Testing Steps
See Phase 5 manual testing checklist above for 24 specific test scenarios.

## Performance Considerations

**Memory**:
- Repository and ViewModels use application scope, but they're lightweight singletons
- Coroutines are properly scoped to `viewModelScope` and cleaned up on ViewModel destruction
- `ConnectRequestManager` cleans up `inFlightRequests` map after completion

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
Add to `CommCareApplication.onCreate()` or main activity launch:
```kotlin
ConnectSyncPreferences.getInstance(this).markSessionStart()
```

## References

- Original research: `docs/claude/research/2026-02-23-offline-first-network-architecture.md`
- Best existing pattern: `app/src/org/commcare/activities/connect/viewmodel/PushNotificationViewModel.kt:31-70`
- Current sync tracking: `app/src/org/commcare/utils/SyncDetailCalculations.java`
- CommCareTask pattern: `app/src/org/commcare/tasks/templates/CommCareTask.java`

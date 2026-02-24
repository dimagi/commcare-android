---
date: 2026-02-24T00:34:09+0000
researcher: Shubham Goyal
git_commit: 8779e4f69bb313c1785ad82a2f41776396c01122
branch: master
topic: "Offline-First Network Architecture - Current State and Proposed Components"
tags: [research, codebase, connect, network, offline-first, viewmodel, repository, sync-tracking]
status: complete
last_updated: 2026-02-23
last_updated_by: Shubham Goyal
---

# Research: Offline-First Network Architecture - Current State and Proposed Components

**Date**: 2026-02-24T00:34:09+0000
**Researcher**: Shubham Goyal
**Git Commit**: 8779e4f69bb313c1785ad82a2f41776396c01122
**Branch**: master

## Research Question

Document the current state of network request architecture in CommCare Android, specifically for Connect features, to understand what exists before implementing the proposed offline-first UX architecture with:
1. ConnectRepository (single source of truth with Flow emissions)
2. ConnectRequestManager (request deduplication)
3. ConnectSyncPreferences (sync timestamp tracking)
4. ViewModels with StateFlow/LiveData

## Summary

The codebase currently uses a **callback-based architecture** built on Android's AsyncTask (`CommCareTask`) for network operations, with some newer **ViewModel + LiveData patterns** emerging in Connect features. There is **no pure Repository pattern** implemented, **no StateFlow usage**, and **no centralized request deduplication mechanism**. Sync timestamps are tracked via SharedPreferences with user-specific keys, and some offline-first patterns exist (like cache-then-network in `PushNotificationViewModel`), but these are not standardized across the codebase.

## Detailed Findings

### 1. Current Network Request Architecture

#### Core Pattern: CommCareTask (AsyncTask-based)

**Primary Implementation**: `app/src/org/commcare/tasks/templates/CommCareTask.java`

The existing architecture uses a specialized AsyncTask pattern:

```
Fragment/Activity → CommCareTask → Connector → Background Work → Callback Delivery
```

**Key Characteristics**:
- **Type Parameters**: `<Params, Progress, Result, Receiver>` where Receiver is the callback handler
- **Connector Pattern**: Tasks connect to a `CommCareTaskConnector` that manages UI lifecycle
- **Abstract Callbacks**: Subclasses implement `deliverResult()`, `deliverUpdate()`, `deliverError()`
- **Lifecycle Management**: Connectors can disconnect/reconnect during configuration changes (rotation)
- **Timeout Handling**: Tasks wait up to 2 seconds for connector reconnection
- **Dialog Management**: Automatic progress dialog management via task IDs

**Task Lifecycle** (`CommCareTask.java:43-112`):
1. `onPreExecute()`: Starts blocking UI via connector
2. `doInBackground()`: Wraps subclass `doTaskBackground()` with exception handling
3. `onProgressUpdate()`: Delivers progress to receiver via `deliverUpdate()`
4. `onPostExecute()`: Delivers result/error to receiver, stops blocking UI

**Common Task Types**:
- `DataPullTask.java` - Syncs data from server (cases, users, ledgers)
- `ProcessAndSendTask.java` - Processes and uploads forms
- `ModernHttpTask.java` - General HTTP requests
- `HttpCalloutTask.java` - External HTTP callouts

#### Connect Network Architecture

**Layered Pattern** (callback-based, not repository):

```
Fragment → ApiHandler (callback) → ApiClient (Retrofit) → Network
                ↓
          BaseApiHandler (abstract)
                ↓
    onSuccess() / onFailure() callbacks
```

**Key Components**:

1. **ConnectApiHandler** (`app/src/org/commcare/connect/network/connect/ConnectApiHandler.kt`)
   - Abstract base class extending `BaseApiHandler<T>`
   - Methods: `getConnectOpportunities()`, `connectStartLearning()`, `getLearningAppProgress()`, `claimJob()`, `getDeliveries()`, `setPaymentConfirmations()`

2. **PersonalIdApiHandler** (`app/src/org/commcare/connect/network/connectId/PersonalIdApiHandler.java`)
   - Methods for PersonalID features: integrity reports, OTP, profile completion, work history, notifications, messaging

3. **BaseApiHandler** (`app/src/org/commcare/connect/network/base/BaseApiHandler.kt:13-159`)
   - Template method pattern with abstract `onSuccess(T)` and `onFailure(errorCode, throwable)`
   - Creates `BaseApiCallback` with parser for response processing
   - Optional loading state management via `view?.showLoading()`
   - Error code enumeration: `UNKNOWN_ERROR`, `NETWORK_ERROR`, `FORBIDDEN_ERROR`, `TOKEN_UNAVAILABLE_ERROR`, etc.

4. **ApiClient Layer**
   - `ConnectApiClient.kt` - Singleton Retrofit client for Connect
   - `PersonalIdApiClient.java` - Singleton Retrofit client for PersonalID
   - `BaseApiClient.kt` - Builds Retrofit with OkHttp, logging interceptors, 30s timeouts

**Fragment Usage Pattern** (`ConnectJobsListsFragment.java:81-110`):
```java
new ConnectApiHandler<ConnectOpportunitiesResponseModel>(true, this) {
    @Override
    public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
        Toast.makeText(requireContext(),
            PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), errorCode, t),
            Toast.LENGTH_LONG).show();
        // Fall back to database on network failure
        navigateFailure();
    }

    @Override
    public void onSuccess(ConnectOpportunitiesResponseModel data) {
        setJobListData(data.getValidJobs());
    }
}.getConnectOpportunities(requireContext(), user);
```

**Notable Absence**: No request deduplication, no in-flight request tracking, no centralized ConnectRepository or ConnectRequestManager exists.

### 2. Existing ViewModel Implementations

**Status**: ViewModels exist for some Connect features but not standardized across the app.

#### Pattern 1: AndroidViewModel with LiveData and Coroutines

**PersonalIdWorkHistoryViewModel** (`app/src/org/commcare/activities/connect/viewmodel/PersonalIdWorkHistoryViewModel.kt:24-115`):

```kotlin
class PersonalIdWorkHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val _apiError = MutableLiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>>()
    val apiError: LiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>> = _apiError

    private val _earnedWorkHistory = MutableLiveData<List<PersonalIdWorkHistory>>()
    val earnedWorkHistory: LiveData<List<PersonalIdWorkHistory>> = _earnedWorkHistory

    fun retrieveAndProcessWorkHistory(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            object : PersonalIdApiHandler<List<PersonalIdWorkHistory>>() {
                override fun onSuccess(result: List<PersonalIdWorkHistory>) {
                    _earnedWorkHistory.postValue(result.sortedByDescending { it.issuedDate })
                }
                override fun onFailure(failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                    _apiError.postValue(failureCode to t)
                }
            }.retrieveWorkHistory(context, user.userId, user.password)
        }
    }
}
```

**Key Observations**:
- Uses `MutableLiveData` (private) exposed as `LiveData` (public)
- Network calls in `viewModelScope.launch(Dispatchers.IO)`
- Still uses callback-based `PersonalIdApiHandler` pattern
- Updates LiveData with `postValue()` from background threads
- No StateFlow usage found in codebase

#### Pattern 2: Cache-Then-Network with LiveData

**PushNotificationViewModel** (`app/src/org/commcare/activities/connect/viewmodel/PushNotificationViewModel.kt:31-70`):

```kotlin
class PushNotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _allNotifications = MutableLiveData<List<PushNotificationRecord>>()
    val allNotifications: LiveData<List<PushNotificationRecord>> = _allNotifications

    fun loadNotifications(isRefreshed: Boolean, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)

            // Load from DB first
            if (!isRefreshed) {
                val cachedNotifications = NotificationRecordDatabaseHelper
                    .getAllNotifications(context)
                    .orEmpty()
                    .sortedByDescending { it.createdDate }
                if (cachedNotifications.isNotEmpty()) {
                    _isLoading.postValue(false)
                }
                _allNotifications.postValue(cachedNotifications)
            }

            // Then fetch from network
            val latestPushNotificationsFromApi = retrieveLatestPushNotifications(context)
            latestPushNotificationsFromApi
                .onSuccess { freshData ->
                    val currentNotifications = _allNotifications.value.orEmpty()
                    val updatedNotifications = (freshData + currentNotifications)
                        .distinctBy { it.notificationId }
                        .sortedByDescending { it.createdDate }
                    _isLoading.postValue(false)
                    _allNotifications.postValue(updatedNotifications)
                }
                .onFailure { error ->
                    _isLoading.postValue(false)
                    _fetchApiError.postValue(error.message)
                }
        }
    }
}
```

**This is the closest existing pattern to the proposed offline-first approach**:
- Emits cached data first (`DataState.Cached` equivalent)
- Then fetches fresh data (`DataState.Success` equivalent)
- Separate loading state
- Uses Kotlin Result type for network responses

#### Pattern 3: Sealed Class for States

**IntegrityTokenViewModel** (`app/src/org/commcare/android/integrity/IntegrityTokenViewModel.kt:126-129`):

```kotlin
sealed class TokenProviderState {
    data class Success(val provider: StandardIntegrityTokenProvider) : TokenProviderState()
    data class Failure(val exception: Exception) : TokenProviderState()
}

private val _providerState = MutableLiveData<TokenProviderState>()
val providerState: LiveData<TokenProviderState> = _providerState
val providerStateFlow: Flow<TokenProviderState> = _providerState.asFlow()
```

**This demonstrates a sealed class pattern similar to the proposed `DataState`**, but only has Success/Failure (no Loading or Cached states).

**Other Sealed Class**: `MissingMediaDownloadResult.kt` has `Success`, `InProgress`, `Error` states.

#### ViewModel Locations

**All ViewModels in codebase**:
- `app/src/org/commcare/activities/connect/viewmodel/PersonalIdWorkHistoryViewModel.kt`
- `app/src/org/commcare/activities/connect/viewmodel/PushNotificationViewModel.kt`
- `app/src/org/commcare/activities/connect/viewmodel/PersonalIdSessionDataViewModel.java` (uses SavedStateHandle)
- `app/src/org/commcare/android/integrity/IntegrityTokenViewModel.kt`

**Notable Absence**: No `ConnectJobsListViewModel`, `ConnectLearningProgressViewModel`, `ConnectDeliveryProgressViewModel` exist. These fragments still use direct callback-based API calls.

### 3. Sync Timestamp Tracking (Existing SharedPreferences)

#### Core Implementation

**SyncDetailCalculations** (`app/src/org/commcare/utils/SyncDetailCalculations.java`):

```java
private static final String LAST_SYNC_KEY_BASE = "last-succesful-sync-";

public static long getLastSyncTime(String username) {
    return CommCareApplication.instance().getCurrentApp()
        .getAppPreferences()
        .getLong(getLastSyncKey(username), 0);
}

private static String getLastSyncKey(String username) {
    return LAST_SYNC_KEY_BASE + username;
}

public static Pair<Long, String> getLastSyncTimeAndMessage() {
    long lastSync = getLastSyncTime();
    String message = getLastSyncMessage(lastSync);
    return new Pair<>(lastSync, message);
}
```

**Storage Pattern**: User-specific SharedPreferences keys like `"last-succesful-sync-{username}"`

**Update Location**: `DataPullTask.java:535`
```java
CommCareApplication.instance().getCurrentApp().getAppPreferences()
    .edit()
    .putLong(SyncDetailCalculations.getLastSyncKey(username), new Date().getTime())
    .apply();
```

#### UI Integration

**Navigation Drawer** (`HomeNavDrawerController.java:92`):
```java
syncItem.subtext = SyncDetailCalculations.getLastSyncTimeAndMessage().second
```

**Sync Button** (`HomeButtons.java:145`):
```java
SyncDetailCalculations.updateSubText(syncButton)
```

Displays messages like "Last synced 2 hours ago" or "Never synced"

#### Connect-Specific Timestamp Tracking

**Database Models with `lastUpdate` field**:
- `ConnectJobRecord.java` - `lastUpdate` (Date), `META_LAST_WORKED_DATE` metafield
- `ConnectJobDeliveryRecord.java` - `lastUpdate` (Date)
- `ConnectLearnModuleSummaryRecord.java` - `lastUpdate` (Date)
- `ConnectJobLearningRecord.java` - `lastUpdate` (Date)
- `ConnectJobAssessmentRecord.java` - `lastUpdate` (Date)
- `ConnectAppRecord.java` - `lastUpdate` (Date)
- `ConnectMessagingMessageRecord.java` - `timeStamp` (Date), `META_MESSAGE_TIMESTAMP` metafield

**UI Display** (`ConnectDeliveryProgressFragment.java:331`):
```java
private void updateLastUpdatedText(Date lastUpdate) {
    binding.connectDeliveryLastUpdate.setText(
        Localization.get("connect_last_update",
            ConnectDateUtils.INSTANCE.formatDateTime(lastUpdate))
    );
}
```

String resource: `<string name="connect_last_update">Updated: %s</string>`

**Notable Absence**: No SharedPreferences-based per-endpoint sync tracking for Connect features. The proposed `ConnectSyncPreferences` with `storeLastSyncTime(endpoint)` / `getLastSyncTime(endpoint)` does not exist.

### 4. Request Deduplication Mechanisms

#### WorkManager-Based Deduplication

**ConnectHeartbeatWorker** (`app/src/org/commcare/connect/workers/ConnectHeartbeatWorker.kt`):
- Scheduled via `WorkManager.getInstance().enqueueUniquePeriodicWork()` in `PersonalIdManager.java:132-137`
- Uses unique work name to prevent duplicate scheduling

**ConnectReleaseTogglesWorker** (`app/src/org/commcare/connect/workers/ConnectReleaseTogglesWorker.kt`):
- Constant: `ONE_TIME_FETCH_WORK_NAME` for unique work naming

**NotificationsSyncWorker** (`app/src/org/commcare/pn/workers/NotificationsSyncWorker.kt`):
- Scheduled via `NotificationsSyncWorkerManager.kt` with unique work names

**Pattern**: WorkManager's `enqueueUniquePeriodicWork()` prevents duplicate background tasks, but this doesn't help with foreground request deduplication.

#### FCM Message Deduplication

**FirebaseMessagingDataSyncer** (`app/src/org/commcare/sync/FirebaseMessagingDataSyncer.java`):

```java
if (fcmMessageData.getCreationTime().getMillis() <
    SyncDetailCalculations.getLastSyncTime(user.getUsername())) {
    // Skip sync if FCM message is older than last successful sync
    return;
}
```

**Pattern**: Timestamp-based deduplication to avoid syncing based on delayed/duplicate FCM messages.

#### Entity Cache Deduplication

**PrimeEntityCacheHelper** (`app/src/org/commcare/tasks/PrimeEntityCacheHelper.kt:52-59`):
- Uses `@Volatile private var inProgress = false` flag
- Single-threaded cache priming to avoid duplicate work

**CommCareEntityStorageCache** (`app/src/org/commcare/models/database/user/models/CommCareEntityStorageCache.java`):
- Locks cache during updates for consistency
- Database-backed cache with timestamps for expiration

**Notable Absence**: No in-memory concurrent request tracking like the proposed `ConnectRequestManager` with `ConcurrentHashMap<Url, DeferredRequest>`. Each network request is independent and can result in duplicate concurrent requests to the same endpoint.

### 5. Offline-First Patterns (Existing)

#### Pattern 1: Cache-Then-Network (Best Example)

**PushNotificationViewModel** - As documented above in section 2.

This is the **only standardized offline-first ViewModel pattern** found in the codebase.

#### Pattern 2: Network-First with Database Fallback

**ConnectJobsListsFragment** (`app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java:81-110`):

```java
new ConnectApiHandler<ConnectOpportunitiesResponseModel>(true, this) {
    @Override
    public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
        // Fall back to database on network failure
        navigateFailure();
    }

    @Override
    public void onSuccess(ConnectOpportunitiesResponseModel data) {
        setJobListData(data.getValidJobs());
    }
}.getConnectOpportunities(requireContext(), user);

private void navigateFailure() {
    setJobListData(ConnectJobUtils.getCompositeJobs(getActivity(),
        ConnectJobRecord.STATUS_ALL_JOBS, null));
}
```

**Pattern**: Network-first, database fallback. Does not show cached data immediately.

#### Pattern 3: Database Entity Cache

**CommCareEntityStorageCache** (`app/src/org/commcare/models/database/user/models/CommCareEntityStorageCache.java:234-275`):

```java
public void primeCache(Hashtable<String, AsyncEntity> entitySet, String[][] cachePrimeKeys, Detail detail) {
    if (detail.isCacheEnabled()) {
        String sqlStatement = "SELECT entity_key, cache_key, value FROM entity_cache " +
            "JOIN AndroidCase ON entity_cache.entity_key = AndroidCase.commcare_sql_id " +
            "WHERE " + whereClause + " AND cache_key IN " + validKeys;

        populateEntitySet(db, sqlStatement, args, entitySet);
    }
}

public void cache(String entityKey, String cacheKey, String value) {
    ContentValues cv = new ContentValues();
    cv.put(COL_CACHE_NAME, mCacheName);
    cv.put(COL_ENTITY_KEY, entityKey);
    cv.put(COL_CACHE_KEY, cacheKey);
    cv.put(COL_VALUE, value);
    cv.put(COL_TIMESTAMP, System.currentTimeMillis());
    db.insertWithOnConflict(TABLE_NAME, null, cv, db.getConflictReplace());
}
```

**Pattern**: Persistent SQLite cache with timestamps, used for case list data.

#### Pattern 4: LRU Memory Cache

**CachingAsyncImageLoader** (`app/src/org/commcare/utils/CachingAsyncImageLoader.java:20-45`):

```java
private final LruCache<String, Bitmap> cache;

public void display(String url, ImageView imageView, int defaultResource, int boundingWidth, int boundingHeight) {
    Bitmap image;
    synchronized (cache) {
        image = cache.get(url);
    }
    if (image != null) {
        imageView.setImageBitmap(image);
    } else {
        new SetImageTask(imageView, this.context, boundingWidth, boundingHeight)
            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
    }
}
```

**Pattern**: Synchronous cache check, asynchronous loading on miss, automatic cache population.

### 6. Database Access Patterns for Connect

**Direct Database Helper Pattern** (most common):

**NotificationRecordDatabaseHelper** (`app/src/org/commcare/connect/database/NotificationRecordDatabaseHelper.kt:9-104`):

```kotlin
object NotificationRecordDatabaseHelper {
    private fun getStorage(context: Context): SqlStorage<PushNotificationRecord> =
        ConnectDatabaseHelper.getConnectStorage(context, PushNotificationRecord::class.java)

    fun getAllNotifications(context: Context): List<PushNotificationRecord>? =
        getStorage(context).getRecordsForValues(arrayOf(), arrayOf())

    fun storeNotifications(context: Context, notifications: List<PushNotificationRecord>): List<String> {
        val storage = getStorage(context)
        for (incoming in notifications) {
            val existing = getNotificationById(context, incoming.notificationId)
            if (existing == null) {
                storage.write(incoming)
            }
        }
        // ...
    }
}
```

**Pattern**: Object singletons with direct SqlStorage access, no repository abstraction.

**JobStoreManager** (`app/src/org/commcare/connect/database/JobStoreManager.java`):
- Manages Connect job database storage instances
- No repository pattern, direct storage access

**ConnectUserDatabaseUtil**: Similar direct access pattern

**Notable Absence**: No Repository pattern separating data source from business logic. Database access is scattered across fragments, ViewModels, and helper classes.

### 7. Background Network Operations

#### NetworkNotificationService

**NetworkNotificationService** (`app/src/org/commcare/services/NetworkNotificationService.kt:26-137`):

```kotlin
class NetworkNotificationService : Service() {
    companion object {
        var isServiceRunning = false
        const val UPDATE_PROGRESS_NOTIFICATION_ACTION = "update_progress_notification"
        const val STOP_NOTIFICATION_ACTION = "stop_notification"
        const val START_NOTIFICATION_ACTION = "start_notification"
    }

    private val _taskIds = MutableStateFlow<List<Int>>(emptyList())
    val taskIds: StateFlow<List<Int>> = _taskIds
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NETWORK_NOTIFICATION_ID.hashCode(),
                buildNotification("network.notification.service.starting"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        serviceScope.launch {
            taskIds.collect { list ->
                if (list.isEmpty() && isServiceRunning) {
                    stopSelf()
                }
            }
        }
    }
}
```

**Purpose**: Foreground service for network operations on Android 14+ to comply with background restrictions.

**Integration with CommCareTask** (`CommCareTask.java:134-161`):
```java
@Override
protected void onPreExecute() {
    if (shouldRunNetworkNotificationService()) {
        CommCareApplication.instance().startForegroundService(getNotificationStartIntent());
    }
}

private boolean shouldRunNetworkNotificationService() {
    return !CommCareApplication.isSessionActive() && runNotificationService &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
}

@Override
protected void onPostExecute(Result result) {
    if (NetworkNotificationService.Companion.isServiceRunning()) {
        CommCareApplication.instance().startForegroundService(getNotificationStopIntent());
    }
}
```

**Pattern**: Tasks register/unregister with service via intents during lifecycle callbacks.

## Code References

### Network Architecture
- `app/src/org/commcare/tasks/templates/CommCareTask.java` - Base async task implementation
- `app/src/org/commcare/tasks/DataPullTask.java` - Data sync task
- `app/src/org/commcare/sync/ProcessAndSendTask.java` - Form upload task
- `app/src/org/commcare/connect/network/connect/ConnectApiHandler.kt` - Connect API handler
- `app/src/org/commcare/connect/network/base/BaseApiHandler.kt:13-159` - Base callback pattern
- `app/src/org/commcare/connect/network/base/BaseApiClient.kt:15-46` - Retrofit client builder

### ViewModels
- `app/src/org/commcare/activities/connect/viewmodel/PersonalIdWorkHistoryViewModel.kt:24-115` - Work history with LiveData
- `app/src/org/commcare/activities/connect/viewmodel/PushNotificationViewModel.kt:31-70` - Cache-then-network pattern
- `app/src/org/commcare/android/integrity/IntegrityTokenViewModel.kt:126-129` - Sealed class states

### Sync Tracking
- `app/src/org/commcare/utils/SyncDetailCalculations.java` - Last sync time tracking
- `app/src/org/commcare/tasks/DataPullTask.java:535` - Updates sync timestamp
- `app/src/org/commcare/fragments/connect/ConnectDeliveryProgressFragment.java:331` - Last update UI display

### Request Deduplication
- `app/src/org/commcare/sync/FirebaseMessagingDataSyncer.java` - FCM message deduplication
- `app/src/org/commcare/connect/workers/ConnectHeartbeatWorker.kt` - WorkManager unique work
- `app/src/org/commcare/tasks/PrimeEntityCacheHelper.kt:52-59` - Cache priming with volatile flag

### Offline-First Patterns
- `app/src/org/commcare/activities/connect/viewmodel/PushNotificationViewModel.kt:31-70` - Best cache-then-network example
- `app/src/org/commcare/models/database/user/models/CommCareEntityStorageCache.java:234-275` - Entity cache
- `app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java:81-110` - Network-first with DB fallback

### Database Access
- `app/src/org/commcare/connect/database/NotificationRecordDatabaseHelper.kt` - Direct database helper pattern
- `app/src/org/commcare/connect/database/JobStoreManager.java` - Job storage management

### Background Services
- `app/src/org/commcare/services/NetworkNotificationService.kt:26-137` - Foreground service for network ops
- `app/src/org/commcare/tasks/templates/CommCareTask.java:134-161` - Task integration with service

## Architecture Documentation

### Current Flow for Connect Network Requests

**Typical Fragment → Network → Database Flow**:

1. Fragment creates anonymous `ConnectApiHandler` instance with callbacks
2. Handler calls Retrofit API via `ConnectApiClient`
3. Network response parsed via `BaseApiResponseParser`
4. On success: `onSuccess(data)` callback → Fragment updates UI and writes to DB
5. On failure: `onFailure(errorCode, throwable)` → Fragment shows error, may fall back to DB

**No intermediate Repository layer exists.**

### Current Patterns vs. Proposed Architecture

| Component | Current State | Proposed Component |
|-----------|---------------|-------------------|
| **Repository** | ❌ Does not exist. Direct API handler + DB access | ✅ `ConnectRepository` - Single source of truth with Flow emissions |
| **Request Manager** | ❌ No deduplication. Each request is independent | ✅ `ConnectRequestManager` - ConcurrentHashMap for in-flight requests |
| **Sync Preferences** | ⚠️ Partial. User-level sync tracking exists, no per-endpoint tracking | ✅ `ConnectSyncPreferences` - Per-endpoint, per-session tracking |
| **ViewModels** | ⚠️ Exists for 4 features, not standardized | ✅ Standardize across all Connect fragments |
| **StateFlow** | ❌ Not used. LiveData only | ✅ Use StateFlow for UI state |
| **DataState Sealed Class** | ⚠️ Partial. `TokenProviderState` exists but not for network data | ✅ `DataState<Loading/Cached/Success/Error>` |
| **Offline-First** | ⚠️ One example (`PushNotificationViewModel`). Not standardized | ✅ Standardize cache-then-network across all fragments |
| **Coroutines** | ⚠️ Some usage in ViewModels with Dispatchers.IO | ✅ Expand to all network operations |

### Existing Sync Timestamp Tracking Mechanism

**User-Level Tracking** (for general CommCare sync):
- Key: `"last-succesful-sync-{username}"`
- Storage: App-scoped SharedPreferences
- Updated: After successful `DataPullTask` completion
- UI: Displayed in navigation drawer, sync button subtext

**Connect Database Records**:
- Each model has `lastUpdate` field (Date)
- Updated when record is created or modified
- Displayed in UI: "Updated: [datetime]"

**Gap**: No per-endpoint sync tracking like `"last-sync-/opportunities"`, `"last-sync-/learning_progress/{jobId}"`. The proposed `ConnectSyncPreferences` would add this capability.

### Existing Request Lifecycle Management

**CommCareTask Lifecycle**:
- Tasks survive configuration changes via `CommCareTaskConnector` reconnection
- Tasks auto-cancel if connector not found within 2 seconds
- Background work continues even if UI disconnects (for headless operations)

**Connect API Calls**:
- No lifecycle awareness beyond fragment lifecycle
- Backing out of fragment typically doesn't cancel network request
- No tracking of in-flight requests across navigation

**Gap**: The proposed architecture aims to keep requests running in application-scoped coroutines, which is not fully implemented. `NetworkNotificationService` provides some of this via foreground service, but it's only used for specific tasks on Android 14+.

## Open Questions

1. **Migration Strategy**: How will existing fragments migrate from callback-based `ConnectApiHandler` to Repository + ViewModel pattern?

2. **Backward Compatibility**: Should the old `CommCareTask` pattern remain for non-Connect features, or should the migration be codebase-wide?

3. **Database Transaction Strategy**: Current Connect writes are immediate after network success. Will Repository batch writes or maintain immediate writes?

4. **Error Handling**: Current error codes are defined in `BaseApiHandler.PersonalIdOrConnectApiErrorCodes`. Will these map to `DataState.Error` or be refined?

5. **Session-Based Tracking**: The spec mentions "avoid doing multiple requests to the same endpoint in same session (for pages with rare updates frequency)". How is "session" defined? App launch to close, or login to logout?

6. **Request Deduplication Scope**: Should `ConnectRequestManager` be global (app-scoped) or per-ViewModel? What happens if two different ViewModels request the same endpoint?

7. **Cache Invalidation**: When should cached data be invalidated? Time-based, manual refresh only, or server-driven via response headers?

8. **Refresh Policies**: The proposed `RefreshPolicy` enum (always, time-based, session-based) - what are the default policies per screen/endpoint?

## Related Research

- [CommCare Technical Overview](https://docs.google.com/document/d/1mr7MRboYGtsKKL9LMG-ZuBpYy10JHzznIKVO32QRM8)
- [CommCare Core Wiki](https://github.com/dimagi/commcare-core/wiki)
- [CommCare Android Wiki](https://github.com/dimagi/commcare-android/wiki)
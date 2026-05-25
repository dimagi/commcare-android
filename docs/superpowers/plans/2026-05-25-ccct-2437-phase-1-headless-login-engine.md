# Phase 1: Headless Login Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the entire login pipeline out of `LoginActivity` into a non-UI `LoginController` so a future non-activity caller (the Phase 3 Connect silent launcher) can drive it. Phase 1 has no user-visible behavior change.

**Architecture:** A new Kotlin package `org.commcare.login` houses the engine. `LoginController.performLogin(request, progressSink)` orchestrates the today's `localLoginOrPullAndLogin` sequence by composing three suspending operations — credential resolution, `ManageKeyRecordTask` wrapper, `DataPullTask` wrapper — plus a deterministic post-success side-effect block. `LoginActivity.doLogin()` becomes a thin caller that builds a request, supplies a `LoginProgressSink` wired to its existing progress UI, awaits the result, and translates it back into the existing `setResult(...) + finish()` contract.

**Tech Stack:** Kotlin coroutines (already on the classpath transitively via `androidx.lifecycle:lifecycle-runtime-ktx`), `suspendCancellableCoroutine` for bridging AsyncTask-based `ManageKeyRecordTask`/`DataPullTask`. Tests use JUnit 4 + `mockk:1.12.7` + `mockito-kotlin:5.2.1` + `kotlinx-coroutines-test:1.7.3`.

---

## Background and Reference

The parent investigation lives at `docs/superpowers/plans/2026-05-11-ccct-2164-decouple-login-from-connect-launch.md` (on a sibling branch). This Phase 1 plan implements the section titled **"Phase 1 — Headless login engine"** verbatim.

Key files the engineer will touch (all on master at the time of writing):

- `app/src/org/commcare/activities/LoginActivity.java` — large activity (1043 lines). The pipeline lives in `initiateLoginAttempt()` → `doLogin()` → `tryLocalLogin()` → `ManageKeyRecordTask` callbacks → `startDataPull()` → `DataPullTask` callbacks → `dataPullCompleted()` → `setResultAndFinish()`.
- `app/src/org/commcare/connect/ConnectAppUtils.kt` — `getPasswordOverride()` becomes `ConnectCredentialResolver.resolve()`.
- `app/src/org/commcare/tasks/ManageKeyRecordTask.java` — AsyncTask-based. Has overridable `deliverResult(R, HttpCalloutOutcomes)`, `deliverUpdate(R, String...)`, `deliverError(R, Exception)` callbacks. The wrapper subclasses this and overrides those three to resume a `CancellableContinuation`.
- `app/src/org/commcare/tasks/DataPullTask.java` — AsyncTask-based. Result is `ResultAndError<PullTaskResult>`; the receiver is `CommCareTaskConnector<DataPullListener>`. The wrapper subclasses and resumes the continuation in `deliverResult` / `deliverError`.
- `app/src/org/commcare/network/HttpCalloutTask.java` — `HttpCalloutOutcomes` enum (Success, AuthFailed, BadResponse, UnknownError, NetworkFailure, NetworkFailureBadPassword, IncorrectPin, AuthOverHttp, BadSslCertificate, InsufficientRolePermission, CaptivePortal, TokenUnavailable, TokenRequestDenied).
- `app/src/org/commcare/activities/LoginMode.java` — `PASSWORD`, `PIN`, `PRIMED`. Reused as-is.

Tests live under `app/unit-tests/src/org/commcare/login/` (new directory).

---

## File Structure

**New production files (all under `app/src/org/commcare/login/`, Kotlin):**

| File | Responsibility |
|---|---|
| `LoginRequest.kt` | Input data class for `LoginController.performLogin`. |
| `LoginProgress.kt` | `LoginProgress` data class + `LoginProgressSink` SAM interface + `LoginPhase` enum. |
| `LoginResult.kt` | Sealed `LoginResult` (Success / Failed) + `PostLoginOutcome` data class. |
| `LoginError.kt` | Sealed `LoginError` hierarchy + outcome-mapping helpers. |
| `OutcomeMapper.kt` | Pure functions mapping `HttpCalloutOutcomes` and `PullTaskResult` to `LoginError`. |
| `ConnectCredentialResolver.kt` | Resolves Connect-managed password for a given appId/username; ports `ConnectAppUtils.getPasswordOverride`. |
| `KeyRecordOperations.kt` | Suspending wrapper around `ManageKeyRecordTask`. |
| `SyncOperations.kt` | Suspending wrapper around `DataPullTask`. |
| `PostLoginSideEffects.kt` | Runs the deterministic post-success chain (CrashUtil, notification clear, analytics, `updateJobProgress`, `updateAppAccess`). |
| `DemoLoginPath.kt` | Short-circuits key-unwrap and sync for demo logins. |
| `LoginController.kt` | Single entry point. Composes the above. |

**Modified production files:**

- `app/src/org/commcare/activities/LoginActivity.java` — `doLogin()` builds a `LoginRequest`, calls `LoginController.performLogin(...)`, and translates the result back to the existing `setResultAndFinish(...)` contract. `tryLocalLogin()`, `localLoginOrPullAndLogin()`, `startDataPull()`, `handlePullTaskResult()`, `dataPullCompleted()`, `handleConnectSignIn()` are removed where they are now redundant. The `PersonalIdUnlocker.unlock(...)` call at line 243, the `installPendingUpdate()` branch at line 286, and `tryAutoLogin()` (restore-last-user UI prefill) remain in the activity.
- `app/src/org/commcare/connect/ConnectAppUtils.kt` — `getPasswordOverride()` becomes a thin delegate to `ConnectCredentialResolver.resolve(...)` so existing non-login callers (if any) keep working. `launchApp()` is left alone (Phase 3/4 concern).

**New test files (under `app/unit-tests/src/org/commcare/login/`):**

One test file per production class, named `<Class>Test.kt`.

---

## Task Decomposition

Eleven tasks. Each lands a small, self-contained unit with tests. Tasks 1–10 are additive (no behavior change). Task 11 swaps `LoginActivity.doLogin()` over to the new engine and removes the now-redundant code. The build stays green and the test suite stays passing after every task.

---

### Task 1: Data model — `LoginRequest`, `LoginProgress`, `LoginResult`, `LoginError`

**Files:**
- Create: `app/src/org/commcare/login/LoginRequest.kt`
- Create: `app/src/org/commcare/login/LoginProgress.kt`
- Create: `app/src/org/commcare/login/LoginResult.kt`
- Create: `app/src/org/commcare/login/LoginError.kt`
- Test: `app/unit-tests/src/org/commcare/login/LoginModelTest.kt`

- [ ] **Step 1: Create `LoginRequest.kt`**

```kotlin
package org.commcare.login

import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode

/**
 * Input to LoginController.performLogin. Built by the caller (LoginActivity today, ConnectAppLauncher in Phase 3).
 */
data class LoginRequest(
    val appId: String,
    val username: String,
    val passwordOrPin: String,
    val credentialType: LoginMode,
    val authSource: AuthSource,
    val restoreSession: Boolean,
    val pullMode: DataPullMode,
    val triggerMultipleUsersWarning: Boolean,
    val blockRemoteKeyManagement: Boolean,
)

/**
 * How the login was triggered. Affects only branching the engine performs internally
 * (Demo short-circuits sync; the rest behave the same in Phase 1).
 */
enum class AuthSource {
    /** User typed credentials into LoginActivity. */
    Manual,
    /** Caller already authenticated externally (Connect, PersonalID-managed login). */
    AutoFromConnect,
    /** MDM-supplied credentials. */
    MdmManaged,
    /** Demo CCZ user — bypass sync. */
    Demo,
}
```

- [ ] **Step 2: Create `LoginProgress.kt`**

```kotlin
package org.commcare.login

/**
 * Progress event emitted by LoginController during performLogin.
 *
 * @property phase coarse-grained phase the engine is in
 * @property percent 0..100, or null when not applicable (key-record retrieval has no progress signal)
 * @property message free-text status the caller may render. Caller decides whether to localise.
 */
data class LoginProgress(
    val phase: LoginPhase,
    val percent: Int? = null,
    val message: String? = null,
)

enum class LoginPhase {
    /** Resolving credentials, seating the app (Phase 3 only — Phase 1 never emits this). */
    Seating,
    /** ManageKeyRecordTask is fetching / validating the key record. */
    SigningIn,
    /** DataPullTask is running. */
    Syncing,
}

/** SAM interface so Java callers can pass a lambda. */
fun interface LoginProgressSink {
    fun onProgress(progress: LoginProgress)
}
```

- [ ] **Step 3: Create `LoginResult.kt`**

```kotlin
package org.commcare.login

import org.commcare.activities.LoginMode

/**
 * Outcome of LoginController.performLogin. Sealed so callers can exhaustively switch.
 */
sealed class LoginResult {
    /**
     * Login completed end-to-end. The caller is responsible for routing (start Home activity etc.).
     * Fields here are exactly what LoginActivity.setResultAndFinish needs to set the existing
     * intent extras (LOGIN_MODE, MANUAL_SWITCH_TO_PW_MODE, PERSONALID_MANAGED_LOGIN,
     * CONNECT_MANAGED_LOGIN, REDIRECT_TO_CONNECT_OPPORTUNITY_INFO).
     */
    data class Success(
        val loginMode: LoginMode,
        val restoreSession: Boolean,
        val manualSwitchToPwMode: Boolean,
        val personalIdManagedLogin: Boolean,
        val connectManagedLogin: Boolean,
        val postLoginOutcome: PostLoginOutcome,
    ) : LoginResult()

    data class Failed(val error: LoginError) : LoginResult()
}

/**
 * What PostLoginSideEffects produced that the routing layer needs.
 * In Phase 1, the only field is the existing REDIRECT_TO_CONNECT_OPPORTUNITY_INFO flag,
 * which is set when updateJobProgress completed against a suspended user.
 */
data class PostLoginOutcome(
    val redirectToConnectOpportunityInfo: Boolean,
)
```

- [ ] **Step 4: Create `LoginError.kt`**

```kotlin
package org.commcare.login

/**
 * Why a login attempt failed. Each variant maps to a specific user-facing message
 * (rendered by the caller — LoginActivity in Phase 1, fragments in Phase 3).
 */
sealed class LoginError {
    /** Both local and remote auth rejected the credentials. Includes IncorrectPin. */
    object BadCredentials : LoginError()

    /** Server denied the SSO token. Connect callers delegate to TokenExceptionHandler. */
    object TokenDenied : LoginError()

    /** Network unreachable, captive portal, token unavailable. */
    object NetworkUnavailable : LoginError()

    /** Safety guard: auth callout attempted over plain HTTP. */
    object AuthOverHttpBlocked : LoginError()

    /**
     * Anything else: BadResponse, BadSslCertificate, UnknownError, InsufficientRolePermission,
     * STORAGE_FULL, SERVER_ERROR, UNREACHABLE_HOST, ENCRYPTION_FAILURE, BAD_DATA(_REQUIRES_INTERVENTION),
     * RECOVERY_FAILURE, ACTIONABLE_FAILURE, SESSION_EXPIRE, CANCELLED, RATE_LIMITED_SERVER_ERROR,
     * CAPTIVE_PORTAL (treated as sync failure when surfaced by DataPullTask vs network when surfaced by HttpCalloutTask).
     *
     * @property reason short string for logging/dialog body (e.g., "BAD_DATA", "STORAGE_FULL").
     * @property message optional user-visible detail returned by the underlying task.
     */
    data class SyncFailed(val reason: String, val message: String? = null) : LoginError()
}
```

- [ ] **Step 5: Write JVM unit tests for the data classes**

Create `app/unit-tests/src/org/commcare/login/LoginModelTest.kt`:

```kotlin
package org.commcare.login

import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LoginModelTest {
    @Test
    fun `LoginRequest equals respects all fields`() {
        val a = sampleRequest()
        val b = sampleRequest()
        assertEquals(a, b)
        assertNotEquals(a, b.copy(passwordOrPin = "other"))
    }

    @Test
    fun `LoginProgress defaults percent and message to null`() {
        val p = LoginProgress(phase = LoginPhase.SigningIn)
        assertEquals(null, p.percent)
        assertEquals(null, p.message)
    }

    @Test
    fun `LoginResult Success carries all routing fields`() {
        val r = LoginResult.Success(
            loginMode = LoginMode.PASSWORD,
            restoreSession = true,
            manualSwitchToPwMode = false,
            personalIdManagedLogin = true,
            connectManagedLogin = true,
            postLoginOutcome = PostLoginOutcome(redirectToConnectOpportunityInfo = true),
        )
        assertEquals(true, r.connectManagedLogin)
        assertEquals(true, r.postLoginOutcome.redirectToConnectOpportunityInfo)
    }

    @Test
    fun `LoginError SyncFailed carries reason and optional message`() {
        val e = LoginError.SyncFailed(reason = "BAD_DATA", message = "parse error")
        assertEquals("BAD_DATA", e.reason)
        assertEquals("parse error", e.message)
    }

    private fun sampleRequest() = LoginRequest(
        appId = "app-1",
        username = "alice",
        passwordOrPin = "secret",
        credentialType = LoginMode.PASSWORD,
        authSource = AuthSource.Manual,
        restoreSession = false,
        pullMode = DataPullMode.NORMAL,
        triggerMultipleUsersWarning = false,
        blockRemoteKeyManagement = false,
    )
}
```

- [ ] **Step 6: Run the new tests and confirm they pass**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests org.commcare.login.LoginModelTest`
Expected: BUILD SUCCESSFUL, all four tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/org/commcare/login/LoginRequest.kt \
        app/src/org/commcare/login/LoginProgress.kt \
        app/src/org/commcare/login/LoginResult.kt \
        app/src/org/commcare/login/LoginError.kt \
        app/unit-tests/src/org/commcare/login/LoginModelTest.kt
git commit -m "[AI] CCCT-2437 Add headless login engine data model"
```

---

### Task 2: Outcome mapper — `HttpCalloutOutcomes` and `PullTaskResult` → `LoginError`

**Files:**
- Create: `app/src/org/commcare/login/OutcomeMapper.kt`
- Test: `app/unit-tests/src/org/commcare/login/OutcomeMapperTest.kt`

- [ ] **Step 1: Create `OutcomeMapper.kt`**

```kotlin
package org.commcare.login

import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.DataPullTask.PullTaskResult

/**
 * Translates task-layer outcome enums into LoginError. Pure functions — no Android dependencies.
 * Mapping is taken verbatim from the parent investigation plan; do not invent new entries.
 */
internal object OutcomeMapper {

    /** Maps a non-Success HttpCalloutOutcomes from ManageKeyRecordTask to a LoginError. */
    fun fromHttpCalloutOutcome(outcome: HttpCalloutOutcomes): LoginError = when (outcome) {
        HttpCalloutOutcomes.AuthFailed,
        HttpCalloutOutcomes.IncorrectPin -> LoginError.BadCredentials

        HttpCalloutOutcomes.NetworkFailure,
        HttpCalloutOutcomes.NetworkFailureBadPassword,
        HttpCalloutOutcomes.CaptivePortal,
        HttpCalloutOutcomes.TokenUnavailable -> LoginError.NetworkUnavailable

        HttpCalloutOutcomes.TokenRequestDenied -> LoginError.TokenDenied
        HttpCalloutOutcomes.AuthOverHttp -> LoginError.AuthOverHttpBlocked

        HttpCalloutOutcomes.BadResponse,
        HttpCalloutOutcomes.BadSslCertificate,
        HttpCalloutOutcomes.UnknownError,
        HttpCalloutOutcomes.InsufficientRolePermission -> LoginError.SyncFailed(outcome.name)

        HttpCalloutOutcomes.Success -> error("Success is not a failure outcome")
    }

    /** Maps a non-DOWNLOAD_SUCCESS PullTaskResult from DataPullTask to a LoginError. */
    fun fromPullTaskResult(result: PullTaskResult, errorMessage: String?): LoginError = when (result) {
        PullTaskResult.AUTH_FAILED -> LoginError.BadCredentials
        PullTaskResult.TOKEN_DENIED -> LoginError.TokenDenied
        PullTaskResult.AUTH_OVER_HTTP -> LoginError.AuthOverHttpBlocked

        PullTaskResult.UNREACHABLE_HOST,
        PullTaskResult.CONNECTION_TIMEOUT,
        PullTaskResult.CAPTIVE_PORTAL,
        PullTaskResult.TOKEN_UNAVAILABLE -> LoginError.NetworkUnavailable

        PullTaskResult.BAD_DATA,
        PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION,
        PullTaskResult.STORAGE_FULL,
        PullTaskResult.SERVER_ERROR,
        PullTaskResult.RATE_LIMITED_SERVER_ERROR,
        PullTaskResult.ENCRYPTION_FAILURE,
        PullTaskResult.RECOVERY_FAILURE,
        PullTaskResult.ACTIONABLE_FAILURE,
        PullTaskResult.SESSION_EXPIRE,
        PullTaskResult.CANCELLED,
        PullTaskResult.EMPTY_URL,
        PullTaskResult.UNKNOWN_FAILURE,
        PullTaskResult.RETRY_NEEDED,
        PullTaskResult.BAD_CERTIFICATE -> LoginError.SyncFailed(result.name, errorMessage)

        PullTaskResult.DOWNLOAD_SUCCESS -> error("DOWNLOAD_SUCCESS is not a failure outcome")
    }
}
```

- [ ] **Step 2: Write `OutcomeMapperTest.kt`**

```kotlin
package org.commcare.login

import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeMapperTest {

    @Test
    fun `AuthFailed and IncorrectPin map to BadCredentials`() {
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.AuthFailed))
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.IncorrectPin))
    }

    @Test
    fun `network-class outcomes map to NetworkUnavailable`() {
        listOf(
            HttpCalloutOutcomes.NetworkFailure,
            HttpCalloutOutcomes.NetworkFailureBadPassword,
            HttpCalloutOutcomes.CaptivePortal,
            HttpCalloutOutcomes.TokenUnavailable,
        ).forEach { outcome ->
            assertEquals(LoginError.NetworkUnavailable, OutcomeMapper.fromHttpCalloutOutcome(outcome))
        }
    }

    @Test
    fun `TokenRequestDenied maps to TokenDenied`() {
        assertEquals(LoginError.TokenDenied, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.TokenRequestDenied))
    }

    @Test
    fun `AuthOverHttp maps to AuthOverHttpBlocked`() {
        assertEquals(LoginError.AuthOverHttpBlocked, OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.AuthOverHttp))
    }

    @Test
    fun `unmapped http outcomes fall through to SyncFailed`() {
        val result = OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.BadResponse)
        assertTrue(result is LoginError.SyncFailed)
        assertEquals("BadResponse", (result as LoginError.SyncFailed).reason)
    }

    @Test
    fun `BAD_DATA and BAD_DATA_REQUIRES_INTERVENTION map to SyncFailed with message`() {
        val a = OutcomeMapper.fromPullTaskResult(PullTaskResult.BAD_DATA, "broken xml")
        val b = OutcomeMapper.fromPullTaskResult(PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION, "missing case")
        assertEquals(LoginError.SyncFailed("BAD_DATA", "broken xml"), a)
        assertEquals(LoginError.SyncFailed("BAD_DATA_REQUIRES_INTERVENTION", "missing case"), b)
    }

    @Test
    fun `AUTH_FAILED pull result maps to BadCredentials`() {
        assertEquals(LoginError.BadCredentials, OutcomeMapper.fromPullTaskResult(PullTaskResult.AUTH_FAILED, null))
    }

    @Test
    fun `TOKEN_DENIED pull result maps to TokenDenied`() {
        assertEquals(LoginError.TokenDenied, OutcomeMapper.fromPullTaskResult(PullTaskResult.TOKEN_DENIED, null))
    }

    @Test
    fun `AUTH_OVER_HTTP pull result maps to AuthOverHttpBlocked`() {
        assertEquals(LoginError.AuthOverHttpBlocked, OutcomeMapper.fromPullTaskResult(PullTaskResult.AUTH_OVER_HTTP, null))
    }

    @Test(expected = IllegalStateException::class)
    fun `Success input throws`() {
        OutcomeMapper.fromHttpCalloutOutcome(HttpCalloutOutcomes.Success)
    }

    @Test(expected = IllegalStateException::class)
    fun `DOWNLOAD_SUCCESS input throws`() {
        OutcomeMapper.fromPullTaskResult(PullTaskResult.DOWNLOAD_SUCCESS, null)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests org.commcare.login.OutcomeMapperTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/org/commcare/login/OutcomeMapper.kt \
        app/unit-tests/src/org/commcare/login/OutcomeMapperTest.kt
git commit -m "[AI] CCCT-2437 Map task outcomes to LoginError"
```

---

### Task 3: `ConnectCredentialResolver`

**Files:**
- Create: `app/src/org/commcare/login/ConnectCredentialResolver.kt`
- Test: `app/unit-tests/src/org/commcare/login/ConnectCredentialResolverTest.kt`

The behaviour ports from `ConnectAppUtils.getPasswordOverride()` (`app/src/org/commcare/connect/ConnectAppUtils.kt:69`). The resolver returns the Connect-managed password, creating a record if one doesn't exist and `createIfNeeded` is true, and throwing `IllegalStateException` when neither path can produce a record (matches today's `RuntimeException`).

- [ ] **Step 1: Create `ConnectCredentialResolver.kt`**

```kotlin
package org.commcare.login

import android.content.Context
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import java.security.SecureRandom

/**
 * Resolves the Connect-managed password for an (appId, username) pair.
 * Ported from ConnectAppUtils.getPasswordOverride to remove its dependency on a Context-holding singleton.
 */
class ConnectCredentialResolver(private val context: Context) {

    /**
     * @throws IllegalStateException if no record exists and createIfNeeded is false,
     * or if record creation failed (matches today's RuntimeException from getPasswordOverride).
     */
    fun resolve(appId: String, username: String, createIfNeeded: Boolean): ResolvedCredentials {
        val existing = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, appId, username)
        val record = existing ?: run {
            if (!createIfNeeded) {
                throw IllegalStateException(
                    "No ConnectLinkedAppRecord found for appId: $appId and username: $username"
                )
            }
            storeNewRecord(appId, username)
        }
        if (record.isUsingLocalPassphrase) {
            FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(appId)
        }
        return ResolvedCredentials(password = record.password, record = record)
    }

    private fun storeNewRecord(appId: String, username: String): ConnectLinkedAppRecord {
        return ConnectAppDatabaseUtil.storeApp(
            context,
            appId,
            username,
            /* connectIdLinked = */ true,
            generateAppPassword(),
            /* usingLocalPassphrase = */ true,
            /* workerLinked = */ false,
        )
    }

    private fun generateAppPassword(): String {
        val passwordLength = 20
        val charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?"
        val random = SecureRandom()
        return (1 until passwordLength)
            .map { charSet[random.nextInt(charSet.length)] }
            .joinToString("")
    }
}

data class ResolvedCredentials(
    val password: String,
    val record: ConnectLinkedAppRecord,
)
```

- [ ] **Step 2: Update `ConnectAppUtils.getPasswordOverride` to delegate**

In `app/src/org/commcare/connect/ConnectAppUtils.kt`, replace the body of `getPasswordOverride` so existing callers keep working:

```kotlin
fun getPasswordOverride(context: Context, username: String, createIfNeeded: Boolean): String {
    val seatedAppId = CommCareApplication.instance().currentApp.uniqueId
    return ConnectCredentialResolver(context).resolve(seatedAppId, username, createIfNeeded).password
}
```

Note the signature tightens `context` and `username` from nullable to non-null — verify call sites at `LoginActivity.java:450-451` are passing non-null values (they are: `this` and `username` from `getUniformUsername()`). If any other caller passes null, broaden the resolver signature instead.

Run: `grep -rn "ConnectAppUtils.INSTANCE.getPasswordOverride\|ConnectAppUtils.getPasswordOverride" app/src`
Expected: only one call site, in `LoginActivity.java`.

- [ ] **Step 3: Write `ConnectCredentialResolverTest.kt`**

Use `mockk` to stub `ConnectAppDatabaseUtil` (a Java static helper) and `FirebaseAnalyticsUtil`.

```kotlin
package org.commcare.login

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ConnectCredentialResolverTest {

    private val context = mockk<Context>(relaxed = true)
    private val resolver = ConnectCredentialResolver(context)

    @Before
    fun setUp() {
        mockkStatic(ConnectAppDatabaseUtil::class)
        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic(ConnectAppDatabaseUtil::class)
        unmockkStatic(FirebaseAnalyticsUtil::class)
    }

    @Test
    fun `returns existing record password`() {
        val record = recordWith(password = "stored-pw", localPassphrase = false)
        every { ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice") } returns record

        val result = resolver.resolve("app-1", "alice", createIfNeeded = false)

        assertEquals("stored-pw", result.password)
        assertSame(record, result.record)
        verify(exactly = 0) { FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(any()) }
    }

    @Test
    fun `reports analytics when existing record uses local passphrase`() {
        val record = recordWith(password = "pw", localPassphrase = true)
        every { ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice") } returns record

        resolver.resolve("app-1", "alice", createIfNeeded = false)

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase("app-1") }
    }

    @Test
    fun `creates a new record when createIfNeeded and none exists`() {
        every { ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice") } returns null
        val created = recordWith(password = "generated", localPassphrase = true)
        every { ConnectAppDatabaseUtil.storeApp(context, "app-1", "alice", true, any(), true, false) } returns created

        val result = resolver.resolve("app-1", "alice", createIfNeeded = true)

        assertEquals("generated", result.password)
        verify(exactly = 1) { FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase("app-1") }
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when no record exists and createIfNeeded is false`() {
        every { ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, "app-1", "alice") } returns null
        resolver.resolve("app-1", "alice", createIfNeeded = false)
    }

    private fun recordWith(password: String, localPassphrase: Boolean): ConnectLinkedAppRecord {
        val record = mockk<ConnectLinkedAppRecord>()
        every { record.password } returns password
        every { record.isUsingLocalPassphrase } returns localPassphrase
        return record
    }
}
```

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests org.commcare.login.ConnectCredentialResolverTest`
Expected: BUILD SUCCESSFUL.

Then run the full app build to confirm the `ConnectAppUtils` signature change didn't break anything: `./gradlew :app:assembleCommcareDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/login/ConnectCredentialResolver.kt \
        app/src/org/commcare/connect/ConnectAppUtils.kt \
        app/unit-tests/src/org/commcare/login/ConnectCredentialResolverTest.kt
git commit -m "[AI] CCCT-2437 Port Connect credential resolution to a class"
```

---

### Task 4: `KeyRecordOperations` — suspend wrapper around `ManageKeyRecordTask`

**Files:**
- Create: `app/src/org/commcare/login/KeyRecordOperations.kt`
- Test: `app/unit-tests/src/org/commcare/login/KeyRecordOperationsTest.kt`

`ManageKeyRecordTask` is parameterised on a receiver `R extends DataPullController`. The receiver receives callbacks like `startDataPull`, `dataPullCompleted`, `raiseLoginMessage`, `raiseMessage`, `updateProgress`. The wrapper subclasses `ManageKeyRecordTask` and overrides the three `deliverXxx` callbacks to resume the continuation. It passes a no-op receiver to `task.connect(...)` because the activity-level callbacks are now handled by the engine.

- [ ] **Step 1: Create `KeyRecordOperations.kt`**

```kotlin
package org.commcare.login

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApp
import org.commcare.activities.DataPullController
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode
import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.ManageKeyRecordTask
import org.commcare.tasks.ResultAndError
import org.commcare.views.notifications.NotificationActionButtonInfo
import org.commcare.views.notifications.NotificationMessage
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages
import kotlin.coroutines.resume

/**
 * Outcome of a key-record exchange. If the task indicated keys are ready for sync,
 * the password to use for the data pull is included.
 */
internal sealed class KeyRecordOutcome {
    /** Keys are in place, sync still required. Caller should run SyncOperations.pullData(...). */
    data class ReadyForSync(val password: String, val pullMode: DataPullMode) : KeyRecordOutcome()
    /** Local-only login succeeded (no sync needed because the sandbox was already populated). */
    object LocalLoginComplete : KeyRecordOutcome()
    data class Failed(val error: LoginError) : KeyRecordOutcome()
}

/**
 * Suspending wrapper around ManageKeyRecordTask. Emits SigningIn progress events.
 */
internal class KeyRecordOperations(
    private val context: Context,
    private val app: CommCareApp,
) {

    suspend fun manageKeyRecord(
        request: LoginRequest,
        sink: LoginProgressSink,
    ): KeyRecordOutcome = suspendCancellableCoroutine { cont ->
        val receiver = NoOpDataPullController()

        val task = object : ManageKeyRecordTask<NoOpDataPullController>(
            context,
            TASK_ID,
            request.username,
            request.passwordOrPin,
            request.credentialType,
            app,
            request.restoreSession,
            request.triggerMultipleUsersWarning,
            request.blockRemoteKeyManagement,
            request.pullMode,
        ) {
            override fun deliverUpdate(receiver: NoOpDataPullController, vararg update: String) {
                sink.onProgress(LoginProgress(LoginPhase.SigningIn, percent = null, message = update.firstOrNull()))
            }

            override fun keysReadyForSync(receiver: NoOpDataPullController) {
                // Capture the password the task computed (override + PIN handling baked in).
                if (!cont.isCompleted) cont.resume(KeyRecordOutcome.ReadyForSync(passwordForSync(), request.pullMode))
            }

            override fun keysLoginComplete(receiver: NoOpDataPullController) {
                if (!cont.isCompleted) cont.resume(KeyRecordOutcome.LocalLoginComplete)
            }

            override fun keysDoneOther(receiver: NoOpDataPullController, outcome: HttpCalloutOutcomes) {
                if (!cont.isCompleted) {
                    cont.resume(KeyRecordOutcome.Failed(OutcomeMapper.fromHttpCalloutOutcome(outcome)))
                }
            }

            /**
             * The base task stores the password in a private field. ManageKeyRecordTask currently
             * passes it to startDataPull(mode, password) from keysReadyForSync. Mirror that by
             * exposing it through a protected accessor we add on ManageKeyRecordTask
             * (see Step 2 below).
             */
            private fun passwordForSync(): String = getPasswordForSync()
        }

        cont.invokeOnCancellation {
            // Best-effort: AsyncTask cancellation does not preempt running steps.
            task.cancel(true)
        }

        task.connect(receiver)
        task.executeParallel()
    }

    companion object {
        private const val TASK_ID = 0x4c47 // arbitrary unique id for the engine
    }
}

/**
 * No-op DataPullController for receivers we don't intend to consume. The engine resumes
 * its continuation from the deliverXxx hooks instead, so these stubs are never invoked.
 */
internal class NoOpDataPullController : DataPullController {
    override fun startDataPull(mode: DataPullMode, password: String) = Unit
    override fun dataPullCompleted() = Unit
    override fun raiseLoginMessage(message: StockMessages, showTop: Boolean) = Unit
    override fun raiseLoginMessage(message: StockMessages, showTop: Boolean,
                                   action: NotificationActionButtonInfo.ButtonAction) = Unit
    override fun raiseLoginMessageWithInfo(message: StockMessages, info: String?, showTop: Boolean) = Unit
    override fun raiseMessage(message: NotificationMessage, showTop: Boolean) = Unit
    override fun updateProgress(message: String, taskId: Int) = Unit
    override fun handlePullTaskResult(
        result: ResultAndError<DataPullTask.PullTaskResult>,
        userTriggered: Boolean,
        formsToSend: Boolean,
        usingRemoteKeyManagement: Boolean
    ) = Unit
    override fun handlePullTaskUpdate(vararg update: Int?) = Unit
    override fun handlePullTaskError() = Unit
}
```

> The `DataPullController` interface members above are taken from the current `LoginActivity` overrides — verify the exact signatures before pasting (some may take `Integer...` rather than `vararg Int?`). The implementer should open `DataPullController.java` and copy the method list.

- [ ] **Step 2: Expose the task's password to subclasses**

`ManageKeyRecordTask.password` is a private field that the base class passes to `receiver.startDataPull(mode, password)` from `keysReadyForSync()`. Since the wrapper resumes a continuation from `keysReadyForSync` instead, it needs the password. Add a protected accessor on `ManageKeyRecordTask.java`:

```java
protected String getPasswordForSync() {
    return password;
}
```

Place it directly under `private String password;` at line ~52.

- [ ] **Step 3: Write `KeyRecordOperationsTest.kt`**

Test goals: each `keysXxx` callback produces the right `KeyRecordOutcome`; cancellation invokes `task.cancel(true)`.

```kotlin
package org.commcare.login

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.commcare.CommCareApp
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode
import org.commcare.network.HttpCalloutTask.HttpCalloutOutcomes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRecordOperationsTest {

    private val request = LoginRequest(
        appId = "app",
        username = "alice",
        passwordOrPin = "pw",
        credentialType = LoginMode.PASSWORD,
        authSource = AuthSource.Manual,
        restoreSession = false,
        pullMode = DataPullMode.NORMAL,
        triggerMultipleUsersWarning = false,
        blockRemoteKeyManagement = false,
    )

    @Test
    fun `keysReadyForSync produces ReadyForSync outcome`() = runTest {
        val ops = TestableKeyRecordOperations(
            context = mockk(relaxed = true),
            app = mockk(relaxed = true),
            outcomeToEmit = TestableKeyRecordOperations.Emit.ReadyForSync("delivered-pw"),
        )
        val result = ops.manageKeyRecord(request) { /* sink */ }
        assertEquals(KeyRecordOutcome.ReadyForSync("delivered-pw", DataPullMode.NORMAL), result)
    }

    @Test
    fun `keysLoginComplete produces LocalLoginComplete outcome`() = runTest {
        val ops = TestableKeyRecordOperations(
            mockk(relaxed = true), mockk(relaxed = true),
            outcomeToEmit = TestableKeyRecordOperations.Emit.LoginComplete,
        )
        val result = ops.manageKeyRecord(request) { }
        assertEquals(KeyRecordOutcome.LocalLoginComplete, result)
    }

    @Test
    fun `AuthFailed http callout maps to BadCredentials`() = runTest {
        val ops = TestableKeyRecordOperations(
            mockk(relaxed = true), mockk(relaxed = true),
            outcomeToEmit = TestableKeyRecordOperations.Emit.HttpOutcome(HttpCalloutOutcomes.AuthFailed),
        )
        val result = ops.manageKeyRecord(request) { }
        assertTrue(result is KeyRecordOutcome.Failed)
        assertEquals(LoginError.BadCredentials, (result as KeyRecordOutcome.Failed).error)
    }
}
```

> **Note for the implementer:** `ManageKeyRecordTask` is an Android `AsyncTask`, which makes it hard to drive directly from a JVM unit test (executors aren't initialised). Create a sibling `TestableKeyRecordOperations` in the test source that exposes the same `suspend fun manageKeyRecord(...)` signature but emits an outcome directly without spinning up an AsyncTask, so the test validates the **outcome shape** without exercising AsyncTask plumbing. End-to-end coverage of the AsyncTask wiring comes from `LoginControllerTest` in Task 8 (which can use Robolectric if needed) and from manual QA of the LoginActivity refactor in Task 11.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests org.commcare.login.KeyRecordOperationsTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/login/KeyRecordOperations.kt \
        app/src/org/commcare/tasks/ManageKeyRecordTask.java \
        app/unit-tests/src/org/commcare/login/KeyRecordOperationsTest.kt
git commit -m "[AI] CCCT-2437 Wrap ManageKeyRecordTask in a suspend function"
```

---

### Task 5: `SyncOperations` — suspend wrapper around `DataPullTask`

**Files:**
- Create: `app/src/org/commcare/login/SyncOperations.kt`
- Test: `app/unit-tests/src/org/commcare/login/SyncOperationsTest.kt`

The data pull is started today via `LoginActivity.startDataPull(mode, password)`, which delegates to `formAndDataSyncer.performOtaRestore(...)` (or `performDemoUserRestore` / `performLocalRestore`). Each ultimately constructs and runs a `DataPullTask`. `SyncOperations` short-circuits that helper and constructs `DataPullTask` directly with the right arguments, mirroring the body of `FormAndDataSyncer.performOtaRestore()` and friends.

- [ ] **Step 1: Read `FormAndDataSyncer` to understand the existing construction pattern**

Run: `grep -n "performOtaRestore\|performDemoUserRestore\|performLocalRestore\|new DataPullTask" app/src/org/commcare/utils/FormAndDataSyncer.java`

Copy the construction logic verbatim into `SyncOperations`. Do **not** delete `FormAndDataSyncer.performOtaRestore` etc. — they remain for non-login data-pull call sites and Task 11 will leave them alone.

- [ ] **Step 2: Create `SyncOperations.kt`**

```kotlin
package org.commcare.login

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.ResultAndError
import kotlin.coroutines.resume

internal sealed class SyncOutcome {
    object Success : SyncOutcome()
    data class Failed(val error: LoginError) : SyncOutcome()
}

internal class SyncOperations(private val context: Context) {

    /**
     * Runs DataPullTask with the same construction parameters FormAndDataSyncer uses today.
     * Progress events from the task's update channel are translated into LoginProgress(Syncing, percent).
     */
    suspend fun pullData(
        username: String,
        password: String,
        mode: DataPullMode,
        sink: LoginProgressSink,
    ): SyncOutcome = suspendCancellableCoroutine { cont ->
        val task = object : DataPullTask<Any>(
            username,
            password,
            /* userId = */ "",
            /* server = */ org.commcare.preferences.ServerUrls.getServerUrl(),
            context,
            /* restoreFromBackground = */ false,
            /* userTriggeredSync = */ false,
            /* skipFixtures = */ false,
        ) {
            override fun deliverResult(receiver: Any?, result: ResultAndError<PullTaskResult>?) {
                val pull = result?.data
                val outcome = if (pull == PullTaskResult.DOWNLOAD_SUCCESS) {
                    SyncOutcome.Success
                } else if (pull != null) {
                    SyncOutcome.Failed(OutcomeMapper.fromPullTaskResult(pull, result.errorMessage))
                } else {
                    SyncOutcome.Failed(LoginError.SyncFailed(reason = "UNKNOWN", message = null))
                }
                if (!cont.isCompleted) cont.resume(outcome)
            }

            override fun deliverUpdate(receiver: Any?, vararg update: Int?) {
                // DataPullTask sends [completed, total] pairs in update.
                val percent = update.takeIf { it.size >= 2 && it[1] != null && it[1]!! > 0 }
                    ?.let { ((it[0] ?: 0) * 100) / it[1]!! }
                sink.onProgress(LoginProgress(LoginPhase.Syncing, percent = percent))
            }

            override fun deliverError(receiver: Any?, e: Exception?) {
                if (!cont.isCompleted) {
                    cont.resume(SyncOutcome.Failed(LoginError.SyncFailed(reason = "UNKNOWN", message = e?.message)))
                }
            }
        }

        cont.invokeOnCancellation { task.cancel(true) }

        task.connect(NoOpDataPullController())
        task.executeParallel()
    }
}
```

> **Note for the implementer:** verify the `DataPullTask` constructor signature you're calling against the one in `DataPullTask.java`. The 8-arg form above is a placeholder pattern; the real constructor may take slightly different args (e.g., a `boolean restoreSession`). Copy what `FormAndDataSyncer.performOtaRestore` actually invokes.

- [ ] **Step 3: Write `SyncOperationsTest.kt`**

Same approach as `KeyRecordOperationsTest`: a sibling `TestableSyncOperations` that emits an outcome directly without instantiating `DataPullTask`. Cover:

- `DOWNLOAD_SUCCESS` → `SyncOutcome.Success`
- `AUTH_FAILED` → `Failed(BadCredentials)`
- `BAD_DATA` with error message → `Failed(SyncFailed("BAD_DATA", "the message"))`
- Null result → `Failed(SyncFailed("UNKNOWN", null))`
- Progress update with `[3, 10]` emits `LoginProgress(Syncing, percent = 30)`

- [ ] **Step 4: Run tests and build**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests org.commcare.login.SyncOperationsTest && ./gradlew :app:assembleCommcareDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/login/SyncOperations.kt \
        app/unit-tests/src/org/commcare/login/SyncOperationsTest.kt
git commit -m "[AI] CCCT-2437 Wrap DataPullTask in a suspend function"
```

---

### Task 6: `PostLoginSideEffects`

**Files:**
- Create: `app/src/org/commcare/login/PostLoginSideEffects.kt`
- Test: `app/unit-tests/src/org/commcare/login/PostLoginSideEffectsTest.kt`

The current chain runs inside `LoginActivity.dataPullCompleted()` (line 491) and the helper `handleConnectSignIn()` (line 509). Phase 1 extracts the **deterministic, non-UI** part of that chain. The UI-prompting branch (`personalIdManager.checkPersonalIdLink(...)` — fires when PersonalID is logged in but no job is associated) stays in `LoginActivity` because it requires the activity for dialog hosting.

- [ ] **Step 1: Create `PostLoginSideEffects.kt`**

```kotlin
package org.commcare.login

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.services.CommCareSessionService
import org.commcare.utils.CrashUtil
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages
import kotlin.coroutines.resume

/**
 * Runs the deterministic side-effects that fire after every successful login.
 *
 * Excludes the UI-prompting branch that calls PersonalIdManager.checkPersonalIdLink(...).
 * That branch (PersonalID logged in + no associated job + need to offer link/de-link) remains
 * in LoginActivity until Phase 2's PostLoginRouter is built.
 */
internal class PostLoginSideEffects(
    private val context: Context,
    private val personalIdManager: PersonalIdManager = PersonalIdManager.getInstance(),
) {

    /**
     * @param activity the CommCareActivity hosting the login. Required for
     *   ConnectJobHelper.updateJobProgress's signature, which takes a context — pass the
     *   activity rather than an application context so any session-scoped operations work.
     */
    suspend fun runOnSuccess(activity: CommCareActivity<*>, username: String): PostLoginOutcome {
        CrashUtil.registerUserData()
        CommCareApplication.notificationManager()
            .clearNotifications(CommCareSessionService.NOTIFICATION_MESSAGE_LOGIN)

        if (!personalIdManager.isloggedIn()) {
            return PostLoginOutcome(redirectToConnectOpportunityInfo = false)
        }

        val appId = CommCareApplication.instance().currentApp.uniqueId
        val job: ConnectJobRecord? = ConnectJobUtils.getJobForApp(activity, appId)
        CommCareApplication.instance().setConnectJobIdForAnalytics(job)

        if (job == null) {
            // The check-link branch is UI-bound; LoginActivity continues to handle it.
            return PostLoginOutcome(redirectToConnectOpportunityInfo = false)
        }

        personalIdManager.updateAppAccess(activity, appId, username)

        val updated = suspendCancellableCoroutine<Boolean> { cont ->
            ConnectJobHelper.updateJobProgress(activity, job) { success, _ ->
                if (!cont.isCompleted) cont.resume(success)
            }
        }

        return PostLoginOutcome(
            redirectToConnectOpportunityInfo = updated && job.isUserSuspended,
        )
    }
}
```

> **Note for the implementer:** double-check the exact spelling of `personalIdManager.isloggedIn()` (current code has the lowercase L) and `ConnectJobHelper.updateJobProgress`'s callback signature in `app/src/org/commcare/connect/ConnectJobHelper.kt:42`. Also confirm `NOTIFICATION_MESSAGE_LOGIN`'s package — it's referenced at `LoginActivity.java:494` as a top-level import.

- [ ] **Step 2: Write `PostLoginSideEffectsTest.kt`**

Use mockk to stub `CrashUtil`, `CommCareApplication.notificationManager()`, `ConnectJobUtils`, `ConnectJobHelper`, and `PersonalIdManager`. Cover:

- PersonalID not logged in → CrashUtil + notification clear only; returns `PostLoginOutcome(false)`
- PersonalID logged in, no job → also runs `setConnectJobIdForAnalytics(null)`; returns `PostLoginOutcome(false)`
- PersonalID logged in, job present, `updateJobProgress` succeeds, `isUserSuspended = true` → returns `PostLoginOutcome(true)`
- PersonalID logged in, job present, `updateJobProgress` succeeds, `isUserSuspended = false` → returns `PostLoginOutcome(false)`
- PersonalID logged in, job present, `updateJobProgress` callback `success = false` → returns `PostLoginOutcome(false)`

Each test uses `runTest { ... }` and asserts on the returned `PostLoginOutcome` plus `verify { ... }` on the static helpers.

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests org.commcare.login.PostLoginSideEffectsTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/org/commcare/login/PostLoginSideEffects.kt \
        app/unit-tests/src/org/commcare/login/PostLoginSideEffectsTest.kt
git commit -m "[AI] CCCT-2437 Extract deterministic post-login side-effects"
```

---

### Task 7: `DemoLoginPath`

**Files:**
- Create: `app/src/org/commcare/login/DemoLoginPath.kt`
- Test: `app/unit-tests/src/org/commcare/login/DemoLoginPathTest.kt`

Today the demo flow runs through `LoginActivity.startDataPull(DataPullMode.CCZ_DEMO, ...)` which calls `formAndDataSyncer.performDemoUserRestore(this, offlineUserRestore)`. The key-record + remote-fetch logic is skipped because the demo CCZ ships with a pre-populated user. `DemoLoginPath` reproduces that short-circuit as a suspend function.

- [ ] **Step 1: Read the existing demo path**

Run: `grep -n "performDemoUserRestore\|OfflineUserRestore\|DEMO_USER_PASSWORD" app/src/org/commcare/utils/FormAndDataSyncer.java app/src/org/commcare/activities/LoginActivity.java`

Note: the demo user record is loaded from `CommCareApplication.instance().getCommCarePlatform().getDemoUserRestore()`. Username and password are taken from that object.

- [ ] **Step 2: Create `DemoLoginPath.kt`**

```kotlin
package org.commcare.login

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.commcare.CommCareApplication
import org.commcare.engine.references.OfflineUserRestore
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.ResultAndError
import kotlin.coroutines.resume

/**
 * Demo-user login. Skips remote key-record fetch; runs DataPullTask in CCZ_DEMO mode
 * against the OfflineUserRestore bundled with the demo CCZ.
 */
internal class DemoLoginPath(private val context: Context) {

    suspend fun login(sink: LoginProgressSink): SyncOutcome {
        val demoRestore: OfflineUserRestore = CommCareApplication.instance()
            .commCarePlatform.demoUserRestore
        val username = demoRestore.username
        val password = OfflineUserRestore.DEMO_USER_PASSWORD

        return suspendCancellableCoroutine { cont ->
            val task = object : DataPullTask<Any>(
                username, password, "", "", context, false, false, false,
            ) {
                override fun deliverResult(receiver: Any?, result: ResultAndError<PullTaskResult>?) {
                    val pull = result?.data
                    val outcome = if (pull == PullTaskResult.DOWNLOAD_SUCCESS) {
                        SyncOutcome.Success
                    } else if (pull != null) {
                        SyncOutcome.Failed(OutcomeMapper.fromPullTaskResult(pull, result.errorMessage))
                    } else {
                        SyncOutcome.Failed(LoginError.SyncFailed("UNKNOWN", null))
                    }
                    if (!cont.isCompleted) cont.resume(outcome)
                }

                override fun deliverUpdate(receiver: Any?, vararg update: Int?) {
                    sink.onProgress(LoginProgress(LoginPhase.Syncing))
                }

                override fun deliverError(receiver: Any?, e: Exception?) {
                    if (!cont.isCompleted) {
                        cont.resume(SyncOutcome.Failed(LoginError.SyncFailed("UNKNOWN", e?.message)))
                    }
                }
            }
            cont.invokeOnCancellation { task.cancel(true) }
            task.connect(NoOpDataPullController())
            task.executeParallel()
        }
    }
}
```

> The `DataPullTask` constructor call here must match the demo construction in `FormAndDataSyncer.performDemoUserRestore` — copy the exact arg list.

- [ ] **Step 3: Write `DemoLoginPathTest.kt`**

Same `Testable` pattern as Task 5: directly emit a `SyncOutcome` without spinning up `DataPullTask`. Cover: `DOWNLOAD_SUCCESS` → Success; non-success → Failed with the right `LoginError.SyncFailed.reason`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests org.commcare.login.DemoLoginPathTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/org/commcare/login/DemoLoginPath.kt \
        app/unit-tests/src/org/commcare/login/DemoLoginPathTest.kt
git commit -m "[AI] CCCT-2437 Add suspending demo-login path"
```

---

### Task 8: `LoginController` — single entry point

**Files:**
- Create: `app/src/org/commcare/login/LoginController.kt`
- Test: `app/unit-tests/src/org/commcare/login/LoginControllerTest.kt`

The controller composes everything. It does **not** hold an Activity reference. The `activity` parameter on `performLogin` is only forwarded into `PostLoginSideEffects.runOnSuccess(...)` so the side-effect chain can call `ConnectJobHelper.updateJobProgress(activity, ...)`. When the Phase 3 fragment-driven caller is built, it will pass its hosting Activity.

- [ ] **Step 1: Create `LoginController.kt`**

```kotlin
package org.commcare.login

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.commcare.CommCareApplication
import org.commcare.activities.CommCareActivity
import org.commcare.activities.DataPullController.DataPullMode

/**
 * Single entry point for the headless login pipeline.
 *
 * Callers are expected to have already unlocked PersonalID before invoking performLogin
 * (LoginActivity does so via PersonalIdUnlocker.unlock at line 243).
 *
 * Post-success side-effects run in NonCancellable so analytics/notification clears still
 * fire even if the caller cancels after a Success was produced.
 */
class LoginController internal constructor(
    private val context: Context,
    private val keyRecordOperations: KeyRecordOperations,
    private val syncOperations: SyncOperations,
    private val demoLoginPath: DemoLoginPath,
    private val credentialResolver: ConnectCredentialResolver,
    private val postLoginSideEffects: PostLoginSideEffects,
) {

    constructor(context: Context) : this(
        context = context,
        keyRecordOperations = KeyRecordOperations(context, CommCareApplication.instance().currentApp),
        syncOperations = SyncOperations(context),
        demoLoginPath = DemoLoginPath(context),
        credentialResolver = ConnectCredentialResolver(context),
        postLoginSideEffects = PostLoginSideEffects(context),
    )

    suspend fun performLogin(
        activity: CommCareActivity<*>,
        request: LoginRequest,
        sink: LoginProgressSink,
    ): LoginResult {
        // Demo short-circuit. AuthSource.Demo skips key-record management entirely.
        if (request.authSource == AuthSource.Demo) {
            return when (val outcome = demoLoginPath.login(sink)) {
                SyncOutcome.Success -> finishSuccess(activity, request)
                is SyncOutcome.Failed -> LoginResult.Failed(outcome.error)
            }
        }

        // Connect-managed logins use the resolver's password instead of whatever the caller supplied.
        val effectiveRequest = if (request.authSource == AuthSource.AutoFromConnect) {
            val resolved = credentialResolver.resolve(
                appId = request.appId,
                username = request.username,
                createIfNeeded = true,
            )
            request.copy(passwordOrPin = resolved.password)
        } else {
            request
        }

        return when (val keyOutcome = keyRecordOperations.manageKeyRecord(effectiveRequest, sink)) {
            is KeyRecordOutcome.Failed -> LoginResult.Failed(keyOutcome.error)
            KeyRecordOutcome.LocalLoginComplete -> finishSuccess(activity, effectiveRequest)
            is KeyRecordOutcome.ReadyForSync -> {
                when (val pullOutcome = syncOperations.pullData(
                    username = effectiveRequest.username,
                    password = keyOutcome.password,
                    mode = keyOutcome.pullMode,
                    sink = sink,
                )) {
                    SyncOutcome.Success -> finishSuccess(activity, effectiveRequest)
                    is SyncOutcome.Failed -> LoginResult.Failed(pullOutcome.error)
                }
            }
        }
    }

    private suspend fun finishSuccess(
        activity: CommCareActivity<*>,
        request: LoginRequest,
    ): LoginResult.Success {
        // Run side effects even if the caller is cancelled.
        val postLoginOutcome = withContext(NonCancellable) {
            postLoginSideEffects.runOnSuccess(activity, request.username)
        }
        val isConnectManaged = request.authSource == AuthSource.AutoFromConnect
        val isPersonalIdManaged = isConnectManaged || PersonalIdManagedDetector.isManaged(request)
        return LoginResult.Success(
            loginMode = request.credentialType,
            restoreSession = request.restoreSession,
            manualSwitchToPwMode = false, // LoginActivity sets this from its UI controller post-call
            personalIdManagedLogin = isPersonalIdManaged,
            connectManagedLogin = isConnectManaged,
            postLoginOutcome = postLoginOutcome,
        )
    }
}

/**
 * Determines whether the login should be marked PERSONALID_MANAGED_LOGIN in the result intent.
 * Mirrors LoginActivity.loginManagedByPersonalId() — PersonalID is in charge whenever an account
 * is currently signed in to PersonalID, regardless of how this particular login was triggered.
 */
internal object PersonalIdManagedDetector {
    fun isManaged(request: LoginRequest): Boolean {
        return org.commcare.connect.PersonalIdManager.getInstance().isloggedIn()
    }
}
```

> The `manualSwitchToPwMode` flag is a UI state that lives on `LoginActivityUIController.userManuallySwitchedToPasswordMode()`. It's not something the engine can determine. In Task 11, LoginActivity will read it off its UI controller after `performLogin` returns and overwrite `Success.manualSwitchToPwMode` before placing it on the result intent. This is a small wart — `LoginResult.Success` is still the single producer of the values, but two of them (`manualSwitchToPwMode` and `restoreSession`) are caller-supplied passthroughs.

- [ ] **Step 2: Write `LoginControllerTest.kt`**

Use constructor injection to pass fakes for every collaborator. Cover:

- **Happy path (Manual, local key record valid):** key-record returns `LocalLoginComplete` → side-effects run once → result is `Success` with `connectManagedLogin = false`.
- **Happy path (AutoFromConnect, remote fetch needed):** credential resolver returns `"resolved-pw"`; key-record returns `ReadyForSync("resolved-pw", NORMAL)`; sync returns `Success` → result is `Success(connectManagedLogin = true)`. Verify the credential resolver was invoked with `createIfNeeded = true`.
- **Demo:** `authSource = Demo` → `demoLoginPath.login(...)` called; key-record and resolver are not invoked.
- **BadCredentials from key-record:** result is `Failed(BadCredentials)`; side-effects do not run.
- **NetworkUnavailable from key-record:** result is `Failed(NetworkUnavailable)`.
- **TokenDenied from key-record:** result is `Failed(TokenDenied)`.
- **SyncFailed from sync:** result is `Failed(SyncFailed)`; side-effects do not run.
- **Side-effects run under NonCancellable:** simulate a `kotlinx.coroutines.cancel()` immediately after the controller emits Success (use `kotlinx.coroutines.test`'s `TestScope` and `cancelAndJoin`); assert that `postLoginSideEffects.runOnSuccess(...)` was nonetheless invoked exactly once. (One way: have the fake `keyRecordOperations` return Success synchronously, then the test cancels the scope after `performLogin` resumes; the `withContext(NonCancellable)` block should still complete because it's already in flight.)

Use mockk's `coVerify { ... }` to assert side-effects were called.

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testCommcareDebugUnitTest --tests org.commcare.login.LoginControllerTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/org/commcare/login/LoginController.kt \
        app/unit-tests/src/org/commcare/login/LoginControllerTest.kt
git commit -m "[AI] CCCT-2437 Compose login pipeline behind LoginController"
```

---

### Task 9: Documentation update

**Files:**
- Modify: `docs/` — add a short markdown overview of the new package

- [ ] **Step 1: Search for existing login-area docs**

Run: `grep -rln "LoginActivity\|login flow" docs/`

- [ ] **Step 2: Create or extend `docs/commcare/login-engine.md`**

If `docs/commcare/login-engine.md` does not exist, create it. Otherwise extend.

Include (in prose, no code blocks):

- One-paragraph description of `org.commcare.login`'s responsibilities (orchestrate local-then-remote login; emit progress; produce a `LoginResult`).
- A table mapping each class to its responsibility (the File Structure table from this plan is a good starting point).
- A pointer to the parent investigation plan (`docs/superpowers/plans/2026-05-11-ccct-2164-decouple-login-from-connect-launch.md`).
- A note that `LoginActivity` is still the only caller as of Phase 1, with `ConnectAppLauncher` to come in Phase 3.

- [ ] **Step 3: Commit**

```bash
git add docs/commcare/login-engine.md
git commit -m "[AI] CCCT-2437 Document the headless login engine package"
```

---

### Task 10: Smoke build before the LoginActivity refactor

- [ ] **Step 1: Confirm the full unit test suite still passes**

Run: `./gradlew :app:testCommcareDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Confirm the app still assembles**

Run: `./gradlew :app:assembleCommcareDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: No commit — this is a checkpoint, not a code change**

If anything fails here, fix it before continuing to Task 11. The next task touches `LoginActivity` and you want a clean baseline.

---

### Task 11: Swap `LoginActivity.doLogin()` over to `LoginController`

This is the only task with user-visible risk. Approach it carefully: keep `tryAutoLogin()`, `installPendingUpdate()`, `PersonalIdUnlocker.unlock(...)`, `setResultAndFinish(...)`, the `onActivityResult(SEAT_APP_ACTIVITY, ...)` branch, and the UI controller untouched.

**Files:**
- Modify: `app/src/org/commcare/activities/LoginActivity.java`

- [ ] **Step 1: Add a `LoginController` field**

Near the other private fields (around `LoginActivity.java:128`):

```java
private LoginController loginController;
private kotlinx.coroutines.Job activeLoginJob;
```

In `onCreate(...)` after `super.onCreate(savedInstanceState)`, initialise:

```java
loginController = new LoginController(this);
```

- [ ] **Step 2: Replace `doLogin(...)` to delegate**

Replace the existing `doLogin(LoginMode loginMode, boolean restoreSession, String passwordOrPin)` body (line 264) with a method that builds a `LoginRequest` and calls `loginController.performLogin(...)` from `lifecycleScope`.

The new method must preserve all pre-task behavior currently in `doLogin`:

- Validate `getUniformUsername()` is non-empty/well-formed; if invalid → `raiseLoginMessage(StockMessages.Auth_BadCredentials, false); return;`
- Validate non-empty password/pin unless `loginMode == LoginMode.PRIMED`; raise the appropriate `Auth_EmptyPassword` / `Auth_EmptyPin` message.
- Clear the UI error and hide the keyboard.
- Save the password to `DevSessionRestorer` if `loginMode == LoginMode.PASSWORD`.
- If `ResourceInstallUtils.isUpdateReadyToInstall()` and not blocked → `installPendingUpdate()` and return (this path eventually re-enters `doLogin` once the update completes — leave it as-is).

Then build the request and dispatch:

```java
LoginRequest request = new LoginRequest(
        CommCareApplication.instance().getCurrentApp().getUniqueId(),
        getUniformUsername(),
        passwordOrPin,
        loginMode,
        determineAuthSource(),
        restoreSession,
        CommCareApplication.instance().isConsumerApp() ? DataPullMode.CONSUMER_APP : DataPullMode.NORMAL,
        getMatchingUsersCount(getUniformUsername()) > 1,
        false  // blockRemoteKeyManagement — matches today's tryLocalLogin(false, restoreSession, false) initial call
);

LoginProgressSink sink = progress -> uiController.updateProgress(messageFor(progress));

activeLoginJob = BuildersKt.launch(
        LifecycleOwnerKt.getLifecycleScope(this),
        EmptyCoroutineContext.INSTANCE,
        CoroutineStart.DEFAULT,
        (scope, cont) -> {
            LoginResult result = loginController.performLogin(this, request, sink, cont);
            handleResult(result, restoreSession);
            return Unit.INSTANCE;
        });
```

> Calling a Kotlin `suspend` function from Java requires a `Continuation` parameter or the `BuildersKt.launch` adapter shown above. If that turns out awkward, a cleaner path is to add a thin Kotlin `LoginCoordinator` class (one file under `app/src/org/commcare/login/`) that exposes `fun start(activity, request, sink, onResult: (LoginResult) -> Unit): Job` and runs the suspend call internally. Prefer the Kotlin coordinator: `LoginActivity` then calls `loginCoordinator.start(...)` like any normal Java→Kotlin call.

Define `determineAuthSource()` as:

```java
private AuthSource determineAuthSource() {
    if (appLaunchedFromConnect) return AuthSource.AutoFromConnect;
    if (uiController.getLoginMode() == LoginMode.PRIMED) return AuthSource.MdmManaged;
    if (CommCareApplication.instance().isConsumerApp()) return AuthSource.Demo;
    return AuthSource.Manual;
}
```

- [ ] **Step 3: Add `handleResult(LoginResult, boolean restoreSession)`**

```java
private void handleResult(LoginResult result, boolean restoreSession) {
    if (result instanceof LoginResult.Success) {
        LoginResult.Success success = (LoginResult.Success) result;
        Intent i = new Intent();
        i.putExtra(REDIRECT_TO_CONNECT_OPPORTUNITY_INFO,
                success.getPostLoginOutcome().getRedirectToConnectOpportunityInfo());
        i.putExtra(LOGIN_MODE, success.getLoginMode());
        i.putExtra(MANUAL_SWITCH_TO_PW_MODE, uiController.userManuallySwitchedToPasswordMode());
        i.putExtra(PERSONALID_MANAGED_LOGIN,
                appLaunchedFromConnect || loginManagedByPersonalId());
        i.putExtra(CONNECT_MANAGED_LOGIN, appLaunchedFromConnect);
        setResult(RESULT_OK, i);
        finish();
    } else if (result instanceof LoginResult.Failed) {
        LoginError error = ((LoginResult.Failed) result).getError();
        renderError(error);
    }
}

private void renderError(LoginError error) {
    if (error instanceof LoginError.BadCredentials) {
        raiseLoginMessage(StockMessages.Auth_BadCredentials, false);
    } else if (error instanceof LoginError.TokenDenied) {
        raiseLoginMessage(StockMessages.TokenDenied, false);
    } else if (error instanceof LoginError.NetworkUnavailable) {
        raiseLoginMessage(StockMessages.Remote_NoNetwork, true);
    } else if (error instanceof LoginError.AuthOverHttpBlocked) {
        raiseLoginMessage(StockMessages.Auth_Over_HTTP, true);
    } else if (error instanceof LoginError.SyncFailed) {
        LoginError.SyncFailed sf = (LoginError.SyncFailed) error;
        if (sf.getMessage() != null) {
            raiseLoginMessageWithInfo(StockMessages.Restore_Unknown, sf.getMessage(), true);
        } else {
            raiseLoginMessage(StockMessages.Restore_Unknown, true);
        }
    }
}
```

This collapses today's `handlePullTaskResult(...)` switch (line 851) into a sealed-class dispatch. The existing `raiseLoginMessage` helpers are untouched.

- [ ] **Step 4: Remove the now-dead code paths**

Once `doLogin` delegates, the following methods/code paths in `LoginActivity` no longer need to fire from the login path. They are still called from other code paths (the `installPendingUpdate` re-entry, consumer-app onResumeFragments, etc.) — leave any method that has remaining callers, and only delete the ones that are now entirely unreachable.

Use `grep` to verify each before deleting:

- `tryLocalLogin(...)` (both overloads, lines 436 and 444) — verify no remaining callers, then delete.
- `localLoginOrPullAndLogin(boolean)` (line 819) — still called from `onResumeFragments()` (line 388) for consumer apps. Refactor that call site to use `doLogin(...)` with a primed request, or leave `localLoginOrPullAndLogin` as a thin wrapper around `doLogin`. Pick whichever is smaller.
- `dataPullCompleted()` (line 491), `handleConnectSignIn(...)` (line 509), `handlePullTaskResult(...)` (line 851), `handlePullTaskUpdate(...)` (line 928), `handlePullTaskError()` (line 936), `startDataPull(...)` (line 301) — these are all callbacks from `ManageKeyRecordTask`/`DataPullTask` that the controller no longer invokes. Their interfaces are declared on `DataPullController`/`SyncCapableCommCareActivity`. Since the activity may still implement those interfaces for other reasons, replace each method body with a no-op or a comment explaining it's now driven via `LoginController`. **Don't remove the interface declarations** until Phase 2 confirms nothing else calls them.

> **Important:** the goal of Step 4 is "no dead branches in the login path", not "delete everything that used to be related to login". When in doubt, leave the method as a no-op and add a one-line `// driven via LoginController since CCCT-2437` comment — Phase 2 will sweep these up properly.

- [ ] **Step 5: Build and run the unit test suite**

Run: `./gradlew :app:assembleCommcareDebug :app:testCommcareDebugUnitTest`
Expected: BUILD SUCCESSFUL, all existing tests pass.

- [ ] **Step 6: Manual QA**

This is required — JVM tests do not exercise the AsyncTask wiring. Run through the matrix on a debug build:

| Scenario | Steps | Expected |
|---|---|---|
| Manual password login (existing user) | Open the app, enter a valid username/password, tap Sign In | Lands on Home; no behavior change vs master |
| Manual password login (wrong password) | Same with a wrong password | "Bad credentials" snackbar/dialog; stays on login |
| Manual login with PIN | Switch to PIN mode, enter valid PIN | Lands on Home |
| Manual login with empty password | Try to sign in with blank password | Empty-password snackbar |
| Restore-last-user | Background, then return; previous credentials prefilled | Prefill works; submit lands on Home |
| Auto-login from Connect (a Connect opportunity tap on master vs branch) | Launch a Connect app | Same `LoginActivity` flash + Home as before. Phase 1 does not remove the flash — Phase 3 does. |
| MDM auto-login | Configure managed config with username/password | Auto-submits and lands on Home |
| Demo CCZ | Run with a demo app installed | Demo login completes; lands on Home |
| Network off mid-login | Disable wifi between key-record and sync | "No network" message |
| Install pending update path | Have a queued update; tap Sign In | Update installs, then login resumes |

Record the result of each in the PR description.

- [ ] **Step 7: Commit**

```bash
git add app/src/org/commcare/activities/LoginActivity.java
git commit -m "[AI] CCCT-2437 Route LoginActivity through LoginController"
```

---

## Acceptance Criteria

Phase 1 is done when all of the following are true:

- [ ] All eleven tasks above are committed.
- [ ] `./gradlew :app:testCommcareDebugUnitTest` passes.
- [ ] `./gradlew :app:assembleCommcareDebug` succeeds.
- [ ] Manual QA matrix in Task 11 Step 6 is green — no observable change to any LoginActivity flow.
- [ ] Biometric / PIN prompts still appear for manual PersonalID-managed login (driven by `PersonalIdUnlocker.unlock(...)` at `LoginActivity.java:243`, untouched in Phase 1).
- [ ] Biometric / PIN prompts still do not appear for AUTO-from-Connect logins (`appLaunchedFromConnect` short-circuits the unlocker today and continues to do so).
- [ ] `LoginController` has unit-test coverage for every `LoginResult` variant.
- [ ] `PostLoginSideEffects`, `ConnectCredentialResolver`, `KeyRecordOperations`, `SyncOperations`, `DemoLoginPath` each have at least one unit-test file with coverage of the success and failure branches.

## Out of Scope (Phase 1)

The parent plan defers the following to later phases. Do not pull them into Phase 1 even if convenient:

- Replacing `SeatAppActivity` (Phase 2).
- `PostLoginRouter` and `PostLoginDestination` (Phase 2).
- `ConnectAppLauncher` and the fragment wiring (Phase 3).
- Removing `SeatAppActivity` from the user's view for Connect launches (Phase 3).
- Removing `IS_LAUNCH_FROM_CONNECT` (Phase 5).
- Migrating `ConnectDeliveryProgressFragment`, `ConnectLearningProgressFragment`, etc. (Phase 4).
- Deleting `LoginActivity`.

## Open Questions

- **`tryAutoLogin()` vs `LoginController`:** `tryAutoLogin()` at `LoginActivity.java:onResumeFragments` is the restore-last-user prefill plus an auto-`initiateLoginAttempt` for consumer apps. Phase 1 leaves it alone, but the engineer should confirm with the CCCT-2164 reviewer whether the consumer-app branch should also route through `LoginController` immediately or wait for Phase 2.
- **`getPasswordOverride` callers:** `grep` for `ConnectAppUtils.INSTANCE.getPasswordOverride` and `ConnectAppUtils.getPasswordOverride` and verify the only caller is `LoginActivity.tryLocalLogin`. If a Java caller passes a nullable context or username, Step 3.2's signature tightening will break — broaden `ConnectCredentialResolver.resolve` instead of tightening.
- **`DataPullTask` constructor signature:** the placeholder in Tasks 5 and 7 must be replaced with the actual constructor invocation used by `FormAndDataSyncer.performOtaRestore` / `performDemoUserRestore`. The engineer should diff against `FormAndDataSyncer.java` before writing test fakes.

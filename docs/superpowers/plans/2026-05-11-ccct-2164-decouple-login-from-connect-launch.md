# CCCT-2164 — Decouple Login From `LoginActivity` for Connect App Launches

**Jira:** [CCCT-2164](https://dimagi.atlassian.net/browse/CCCT-2164) — *[Investigation] Remove Login Screen for Connect App Launches*
**Branch:** `CCCT-2164-remove-login-screen-from-cc-app-launches-plan`

## Goal

Skip the intermediary `LoginActivity` screen when an FLW launches a CommCare app from the Connect opportunities list. Credentials are already stored in `ConnectLinkedAppRecord` and the login runs in "auto" mode today — but the login *work* (key unwrap, sandbox unlock, sync, post-login routing) is fused to `LoginActivity` and must be extracted before the screen can be removed. As a bonus, `SeatAppActivity` is replaced on the silent path by a modal progress dialog.

## Current Flow

When an FLW taps an opportunity:

1. `ConnectJobsListsFragment.launchAppForJob()` → `ConnectAppUtils.launchApp()` calls `CommCareApplication.closeUserSession()` then `CommCareLauncher.launchCommCareForAppId(..., IS_LAUNCH_FROM_CONNECT=true)`.
2. `DispatchActivity` sees no session → `launchLoginScreen()`.
3. `LoginActivity.onResume()` detects the Connect flag → auto-invokes `doLogin("AUTO")` with the password from `ConnectLinkedAppRecord`.
4. `ManageKeyRecordTask` validates and unwraps the key; `CommCareApplication.startUserSession()` runs sync.
5. `LoginActivity.dataPullCompleted()` packs flags (`REDIRECT_TO_CONNECT_OPPORTUNITY_INFO`, `LOGIN_MODE`, `MANUAL_SWITCH_TO_PW_MODE`, etc.) into the result Intent and finishes.
6. `DispatchActivity.onActivityResult(LOGIN_USER)` reads the flags and routes to the Connect opportunity info screen (or home).

The user sees the login screen flash for the duration of steps 3–5 with no credentials entered. If the app isn't seated yet, they also see `SeatAppActivity` for seconds-to-a-minute.

## What Makes This Hard

Everything below currently lives inside `LoginActivity` (or its result contract with `DispatchActivity`) and must be extracted before the screen can be skipped:

- **Auth logic is methods on the activity.** `doLogin()`, `tryLocalLogin()`, `dataPullCompleted()` all reference `this` (UI controller, snackbar, error rendering). No headless entry point exists.
- **PersonalID unlock needs an activity host** for biometric/PIN prompts. The Connect path still needs this when the token expires.
- **Routing flags are produced only in `LoginActivity.setResultAndFinish()`** and consumed only in `DispatchActivity.onActivityResult(LOGIN_USER, ...)`. Bypassing the activity means reproducing this contract.
- **Side-effects scattered through the lifecycle**: `CrashUtil.registerUserData`, `notificationManager.clearNotifications`, `setConnectJobIdForAnalytics`, multiple `FirebaseAnalyticsUtil` calls, `ConnectJobHelper.updateJobProgress`, `PersonalIdManager.updateAppAccess`, `seatAppIfNeeded`.
- **Failure UX is screen-shaped.** Today an auto-login failure leaves the user on `LoginActivity` with an error and a populated form. The new path needs an explicit error UX in the Connect screen.

`LoginActivity` itself stays — manual launches, MDM auto-login, shortcut launches, and `needAnotherAppLogin()` re-prompts all still need it. We're only short-circuiting the *Connect-initiated* path.

## Required Changes

Each change below is a discrete commit-sized unit, listed in dependency order.

### C1. Extract a headless `LoginController`

The prerequisite for everything else: a non-UI caller must be able to perform a login.

**New class:** `org.commcare.login.LoginController` (Kotlin).

- Constructor-injected collaborators: `KeyRecordOperations` (wraps `ManageKeyRecordTask`), `SyncOperations` (wraps `DataPullTask`), `PersonalIdUnlockHost`, `PostLoginSideEffects`, `DemoLoginPath`.
- Single entry point: `suspend fun performLogin(request: LoginRequest): LoginResult`. **The controller holds no Activity reference.**
- `LoginRequest`: `appId`, `username`, `password`, `mode: LoginMode` (`Manual`, `AutoFromConnect`, `MdmManaged`, `Demo`), `restoreSession`.
- `LoginResult` sealed class (8 variants):
  - `Success(destination: PostLoginDestination)`
  - `BadCredentials`
  - `SyncFailed(reason)`
  - `PersonalIdUnlockRequired`
  - `NetworkUnavailable`
  - `KeyRecordMissing`
  - `SandboxCorrupted`
  - `ConnectLinkageInvalid`
- Cancellation propagates as `CancellationException`. Post-success side-effects run in `withContext(NonCancellable)` so analytics still emit if the caller cancels after success.
- For `LoginMode.Demo`, delegate to `DemoLoginPath`, which short-circuits key-unwrap and sync.
- `LoginActivity` becomes a thin wrapper: UI controller + `LoginController` instance + result translation back into `setResult`. Restore-last-user stays in the UI controller — it's a "what credentials to populate" concern, not auth.

**No controller-side concurrency lock.** Double-launch is prevented at the UI layer in `ConnectJobsListsFragment` (see C4).

**`AppNotSeated` is not a `LoginResult`** — seat failure is a separate result from C6 that `ConnectAppLauncher` translates directly to a fallback without ever calling `performLogin`.

**Acceptance:** All existing `LoginActivity` flows (manual, auto-from-Connect, PersonalID-managed, demo, MDM, restore-last-user) keep working. Controller has JVM unit tests with `KeyRecordOperations` and `SyncOperations` mocked.

### C2. Move PersonalID unlock behind a host interface

Connect-managed launches still need biometric/PIN prompts when the PersonalID token has expired, but the controller can't call `personalIdManager.unlockConnect()` directly (it requires an Activity).

**New file:** `org.commcare.login.PersonalIdUnlockHost`.

```kotlin
interface PersonalIdUnlockHost {
    suspend fun requestUnlock(): UnlockResult
}
sealed class UnlockResult {
    object Granted : UnlockResult()
    object Cancelled : UnlockResult()
    object HostUnavailable : UnlockResult()   // activity GC'd mid-coroutine
    data class Failed(val reason: String) : UnlockResult()
}
```

`ActivityPersonalIdUnlockHost(activity)` holds a `WeakReference<AppCompatActivity>` and calls `personalIdManager.unlockConnect()` on it; returns `HostUnavailable` if the weak ref is null. Both `LoginActivity` and `ConnectAppLauncher` provide one. `LoginController` never sees `PersonalIdManager` directly.

`HostUnavailable` only realistically occurs if the user has fully backgrounded the app — the `lifecycleScope` cancellation in C4 handles that case, so no separate UX path is needed.

**Acceptance:** PersonalID unlock works from both entry points. `LoginController` unit tests inject a fake host. No NPE on `HostUnavailable`.

### C3. Replace `DispatchActivity`'s inline routing with `PostLoginRouter`

The current routing is split across `DispatchActivity.onActivityResult(LOGIN_USER, ...)` and `launchHomeScreen()` — both hand-marshal extras inline. We need a single authority that translates a `LoginResult` into a destination.

**New file:** `org.commcare.login.PostLoginRouter` (pure Kotlin, no Android dependencies).

- Input: `LoginResult` + `LaunchContext` (Connect-initiated vs not, target appId, restoreSession, jobId).
- Output: `PostLoginDestination` sealed class with typed variants:

```kotlin
sealed class PostLoginDestination {
    data class Home(
        val loginMode: LoginMode,
        val startFromLogin: Boolean,
        val manualSwitchToPwMode: Boolean,
        val personalIdManagedLogin: Boolean,
    ) : PostLoginDestination()

    data class ConnectOpportunityInfo(val jobId: String, val loginMode: LoginMode) : PostLoginDestination()

    data class FallbackToLoginActivity(val appId: String, val reason: FallbackReason) : PostLoginDestination()

    data class RetryableFailure(val reason: RetryableReason) : PostLoginDestination()   // SyncFailed / NetworkUnavailable

    object PersonalIdUnlockNeeded : PostLoginDestination()                                // PersonalIdUnlockRequired
}

enum class FallbackReason { BadCredentials, KeyRecordMissing, ConnectLinkageInvalid, SandboxCorrupted, AppNotSeated }
enum class RetryableReason { Sync, Network }
```

**New extension:** `fun PostLoginDestination.toIntent(context, launchContext): Intent`. Defined only on `Home` and `ConnectOpportunityInfo` — the failure variants have no Intent (the orchestrator emits `ShowError`; the activity wrapper stays on-screen).

This is the **single source of truth** for the extras `RootMenuHomeActivity` / `StandardHomeActivity` / `ConnectNavHelper` consume. Both the silent path (C4) and `DispatchActivity.onActivityResult` use it. The string extras themselves are unchanged — those activities are out of scope to migrate.

**Acceptance:** No behavior change. `DispatchActivity` no longer encodes routing rules inline. Both write-paths into Home-activity extras go through `toIntent(...)`.

### C4. Add `ConnectAppLauncher` — the new silent path

The orchestrator that replaces `LoginActivity` on Connect-initiated launches. Depends on C1, C2, C3.

**New class:** `org.commcare.connect.applauncher.ConnectAppLauncher`.

```kotlin
class ConnectAppLauncher(
    private val loginController: LoginController,
    private val postLoginRouter: PostLoginRouter,
    private val appSeater: AppSeater,
    private val credentialResolver: ConnectCredentialResolver,
    private val sideEffects: PostLoginSideEffects,
) {
    suspend fun launch(
        appId: String,
        isLearning: Boolean,
        jobId: String,
        host: PersonalIdUnlockHost,
        progressSink: AppSeaterProgressSink,
    ): SilentLaunchOutcome
}

sealed class SilentLaunchOutcome {
    data class StartActivity(val intent: Intent) : SilentLaunchOutcome()
    data class ShowError(
        val message: ErrorMessage,
        val manualEscalation: ManualEscalation?,
        val retry: RetryAction?,
    ) : SilentLaunchOutcome()
}

sealed class ManualEscalation {
    data class OpenLoginActivityManualMode(val appId: String, val reason: FallbackReason) : ManualEscalation()
}
```

**Orchestration order:**

1. Close the current session.
2. Seat the app via `AppSeater.seatIfNeeded(appId, progressSink)` — caller renders a modal progress dialog (no `SeatAppActivity`).
3. Resolve credentials via `ConnectCredentialResolver.resolve(appId, createIfNeeded = true)`. If null → `ShowError` with `OpenLoginActivityManualMode(appId, KeyRecordMissing)`.
4. Call `LoginController.performLogin(request)`.
5. Translate the result via `PostLoginRouter` → `PostLoginDestination`:
   - `Home` / `ConnectOpportunityInfo` → `toIntent(...)` → `SilentLaunchOutcome.StartActivity`.
   - `FallbackToLoginActivity(appId, reason)` → `ShowError` with `OpenLoginActivityManualMode`. Covers `BadCredentials`, `KeyRecordMissing`, `ConnectLinkageInvalid`, `SandboxCorrupted`, `AppNotSeated` — all "user must see the full login screen" cases.
   - `RetryableFailure` → `ShowError` with `RetryAction.RelaunchSame`. Covers `SyncFailed` / `NetworkUnavailable`.
   - `PersonalIdUnlockNeeded` → invoke `host.requestUnlock()`; on `Granted`, retry `performLogin` **once**; on `Cancelled` / `Failed` / `HostUnavailable`, emit `ShowError` with retry.

`CancellationException` propagates from `launch`; the caller's scope handles it. The orchestrator does not emit UI itself — it returns an outcome.

**Decision: always escalate on `BadCredentials`.** There is no rotate-password API to retry against, so silent rotation isn't an option.

**Caller wiring in `ConnectJobsListsFragment`:**

- Launch from `viewLifecycleOwner.lifecycleScope` — backgrounding cancels cleanly; the user re-taps on return.
- Disable all opportunity rows while a launch is in flight (prevents double-launch; UI-level lock is the only lock).
- Lock orientation on entry, restore on exit via `try/finally`:

```kotlin
val previousOrientation = requireActivity().requestedOrientation
requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
try {
    val outcome = launcher.launch(...)
    when (outcome) {
        is StartActivity -> startActivity(outcome.intent)
        is ShowError -> renderSnackbar(outcome.message, outcome.manualEscalation, outcome.retry)
    }
} finally {
    requireActivity().requestedOrientation = previousOrientation
    restoreRowsEnabled()
    dismissSeatProgressDialog()
}
```

- On `OpenLoginActivityManualMode`, the snackbar shows "Enter password manually". Tapping it calls:

```kotlin
CommCareLauncher.launchCommCareForAppId(
    activity,
    appId,
    mapOf(IS_LAUNCH_FROM_CONNECT to true, SKIP_AUTO_LOGIN to true),
)
```

The existing `LoginActivity → DispatchActivity → ConnectNavHelper.goToActiveInfoForJob` route lands the user back on Connect opp info once they enter credentials.

- The seat-progress dialog is a **non-dismissable Material alert** with spinner + text. The fragment shows it when seating begins and dismisses on every termination path.

`ConnectAppUtils.launchApp(...)` delegates to `ConnectAppLauncher`. `CommCareLauncher.launchCommCareForAppId(...)` and `IS_LAUNCH_FROM_CONNECT` remain — but the only live post-migration use is the manual-escalation re-entry above.

**Acceptance:** Tapping an opportunity for a healthy account never shows `LoginActivity` or `SeatAppActivity`. Time-to-home drops by one Activity transition. Manual flow (app drawer → CommCare → login screen) is unaffected.

### C5. Add `SKIP_AUTO_LOGIN` gate in `LoginActivity` for manual-escalation entry

Without this gate, the manual-escalation re-entry from C4 would re-run the silent auto-login that just failed.

- Add an Intent extra constant `SKIP_AUTO_LOGIN`.
- In `LoginActivity.onResume()`, gate the existing `appLaunchedFromConnect && !connectLaunchPerformed` auto-login branch with `&& !skipAutoLogin`.
- When `skipAutoLogin == true`:
  - Pre-populate username from the Connect linked-app record.
  - Leave the password field blank (pre-populating with the known-bad password just re-fails).
  - Optionally surface a small inline hint based on the `FallbackReason`.
- Post-login routing is unchanged — `IS_LAUNCH_FROM_CONNECT=true` still drives the route to Connect opp info.

**Acceptance:** Launching with both `IS_LAUNCH_FROM_CONNECT=true` and `SKIP_AUTO_LOGIN=true` shows the login form (username pre-filled, password blank), does not invoke `initiateLoginAttempt()` on resume, and routes back to Connect opp info on manual submission.

### C6. Inline `AppSeater` (skip `SeatAppActivity` on silent path)

Removes the second Activity transition. Independent of `LoginActivity` work but required by C4.

**New file:** `org.commcare.login.AppSeater`.

```kotlin
interface AppSeater {
    suspend fun seatIfNeeded(appId: String, progressSink: AppSeaterProgressSink): SeatResult
}
sealed class SeatResult {
    object AlreadySeated : SeatResult()
    object SeatedNow : SeatResult()
    data class Failed(val reason: Throwable) : SeatResult()
}
```

Port the body of `SeatAppActivity.SeatAppProcess.run()` into a `suspend fun` that calls `CommCareApplication.instance().initializeAppResources(...)` on `Dispatchers.IO`. **Preserve** existing exception-handling and `FirebaseAnalyticsUtil` reporting.

`AppSeaterProgressSink` is owned by the caller and is expected to drive the modal progress dialog.

**`LoginActivity` keeps using `SeatAppActivity`** via the existing `seatAppIfNeeded()`. Migrating that path is a follow-up.

**Acceptance:** Silent path seats apps without launching `SeatAppActivity`. Firebase reporting on seating errors is preserved. The modal dialog is shown while seating is in progress.

### C7. `ConnectCredentialResolver`

Both `LoginActivity` auto-login and `ConnectAppLauncher` need to resolve "what password do we use for this Connect-linked app." Centralize it.

**New file:** `org.commcare.connect.applauncher.ConnectCredentialResolver`.

```kotlin
class ConnectCredentialResolver(
    private val personalIdManager: PersonalIdManager,
    private val linkedAppRecordSource: ConnectLinkedAppRecordSource,
) {
    fun resolve(appId: String, createIfNeeded: Boolean): ResolvedCredentials?  // null on DB error
}
```

Port `ConnectAppUtils.getPasswordOverride()` into the resolver. Old call sites in `ConnectAppUtils` delegate. `LoginActivity` auto-login also goes through it. Silent path calls with `createIfNeeded = true`, mirroring today's `LoginActivity` behavior.

**Acceptance:** Single implementation of credential resolution. `LoginController` never sees `ConnectLinkedAppRecord`. Null return on the silent path produces `FallbackToLoginActivity(reason = KeyRecordMissing)`.

### C8. `PostLoginSideEffects` — collected post-success work

Pulls the scattered post-success calls into one testable place.

**New file:** `org.commcare.login.PostLoginSideEffects`.

```kotlin
class PostLoginSideEffects(
    private val crashUtil: CrashUtil,
    private val notificationManager: NotificationManager,
    private val analytics: FirebaseAnalyticsUtil,
    private val connectJobHelper: ConnectJobHelper,
    private val personalIdManager: PersonalIdManager,
) {
    suspend fun runOnSuccess(context: PostLoginContext)
}
```

Each scattered call becomes one line in `runOnSuccess`:

- `CrashUtil.registerUserData(...)`
- `CommCareApplication.notificationManager().clearNotifications()`
- `CommCareApplication.setConnectJobIdForAnalytics(...)`
- `FirebaseAnalyticsUtil.report*` for the auto-login path
- `ConnectJobHelper.updateJobProgress(...)`
- `PersonalIdManager.updateAppAccess(...)`

Invoked by `LoginController` inside `withContext(NonCancellable)`. The `suspend` marker is retained so the wrap is idiomatic.

**Acceptance:** Every call above runs on a successful login in both the `LoginActivity` and silent paths. Covered by a table-driven unit test.

### C9. AsyncTask suspend wrappers

Required by C1 (suspend controller).

`KeyRecordOperations` and `SyncOperations` (`org.commcare.login`) wrap `ManageKeyRecordTask` / `DataPullTask` in `suspendCancellableCoroutine { ... }` with `invokeOnCancellation { task.cancel(true) }`.

**Cancellation is best-effort.** The wrapper does not await task termination. The underlying task may continue executing in the background and complete normally (writing local state) even after the coroutine is cancelled. Document this in `docs/connect/connect-app-launcher.md` — it's acceptable for analytics fidelity but worth knowing.

Progress reported via a `(Progress) -> Unit` lambda. `LoginActivity` supplies a real callback; `ConnectAppLauncher` supplies a no-op (the seat modal is dismissed before sync begins).

**Acceptance:** `LoginController` can `await` either task and have its scope's cancellation propagate at suspension points.

### C10. Tests

**Tiering:**

- **JVM unit (default):** `LoginController`, `PostLoginRouter`, `PostLoginSideEffects`, `ConnectCredentialResolver`, `AppSeater`, `DemoLoginPath`, `ConnectAppLauncher`, `PostLoginDestination.toIntent`, AsyncTask wrappers.
- **Robolectric:** `ConnectJobsListsFragment` wiring (tap → orchestrator → snackbar → orientation lock/restore → modal show/dismiss); `LoginActivity` with `SKIP_AUTO_LOGIN`.
- **Instrumentation:** one happy-path end-to-end smoke (tap opportunity → home screen).
- **Manual QA:** `LoginActivity` manual login, MDM auto-login, restore-last-user, demo mode. No new automated regression tests on `LoginActivity` itself — exhaustive new controller tests provide the safety net.

**Required cases:**

1. `LoginController.performLogin` — one test per `LoginResult` variant (8).
2. Cancellation: scope cancellation during sync → `CancellationException` propagates, `task.cancel(true)` invoked, `NonCancellable` post-success block still runs if success arrived before cancellation.
3. `BadCredentials` → router emits `FallbackToLoginActivity(BadCredentials)` → orchestrator emits `ShowError` with `OpenLoginActivityManualMode`; `reportCccAppFailedAutoLogin` fires exactly once.
4. `PersonalIdUnlockHost.HostUnavailable`: controller surfaces clean failure (no NPE); router emits `PersonalIdUnlockNeeded`; orchestrator does not loop indefinitely.
5. `PostLoginDestination.toIntent`: table-driven over `Home` and `ConnectOpportunityInfo`; asserts produced Intent has expected component + extras.
6. `AppSeater` error preservation: inject failing `initializeAppResources`; assert `FirebaseAnalyticsUtil` still reports and `SeatResult.Failed(reason)` is returned.
7. `DemoLoginPath`: `LoginMode.Demo` bypasses sync/key unwrap; returns `Success` with correct destination.
8. `ConnectAppLauncher` rejects demo: fails fast if asked to launch with `LoginMode.Demo`.
9. `KeyRecordMissing` vs `ConnectLinkageInvalid`: distinct `FallbackReason` values.
10. `RetryableFailure`: `SyncFailed` and `NetworkUnavailable` each produce `ShowError` with `RetryAction.RelaunchSame` and no `manualEscalation`.
11. `PersonalIdUnlockNeeded` retry: `Granted` triggers exactly one retry; `Cancelled`/`Failed` emit `ShowError`; loop is bounded.
12. **Robolectric — orientation lock:** locked while in flight; previous orientation restored on every termination path (`try/finally` coverage).
13. **Robolectric — seat dialog:** shown when seating begins, dismissed on every termination path.
14. **Robolectric — `LoginActivity` skip-auto-login:** `IS_LAUNCH_FROM_CONNECT=true` + `SKIP_AUTO_LOGIN=true` does not invoke `initiateLoginAttempt`; username pre-filled, password blank; manual submission routes back to Connect opp info.

## Out Of Scope

- Deleting `LoginActivity`. Manual launches, MDM auto-login, shortcut launches, and `needAnotherAppLogin()` still need it.
- Reworking `RootMenuHomeActivity` / `StandardHomeActivity` to consume `PostLoginDestination` directly. They keep reading string extras; `toIntent(...)` is the single producer.
- Migrating `LoginActivity`'s seat path to the new inline `AppSeater`.
- Cooperative cancellation in `ManageKeyRecordTask` / `DataPullTask` themselves (follow-up if it becomes a priority).
- First-time-on-device flow (no `UserKeyRecord` yet — must sync with UI visible).
- Multi-user device flows where switching apps requires re-prompt.
- Auth-token refresh strategy — no protocol change required.
- Silent-login duration telemetry — deferred.
- Switching password storage from `String` to `CharArray`.
- Silent password rotation against Connect/HQ — no rotate-password API exists. If one is added later, revisit the manual-escalation policy.

## Citation Caveat

All file:line references in this plan were captured from branch `CCCT-2164-remove-login-screen-from-cc-app-launches-plan` on 2026-05-11. Re-verify before relying on a specific line — `LoginActivity.java` in particular is large and churns frequently.

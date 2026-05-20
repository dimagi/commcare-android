# CCCT-2164 — Decouple Login From `LoginActivity` for Connect App Launches

**Jira:** [CCCT-2164](https://dimagi.atlassian.net/browse/CCCT-2164) — *[Investigation] Remove Login Screen for Connect App Launches*
**Branch:** `CCCT-2164-remove-login-screen-from-cc-app-launches-plan`

## Goal

When an FLW launches a CommCare app from a Connect page, skip the `LoginActivity` and `SeatAppActivity` screens. Replace them with a progress dialog hosted by the Connect fragment. The login work itself is preserved end-to-end (local-then-remote, key unwrap, sandbox unlock, sync, post-login routing) but is moved off `LoginActivity` so a non-UI caller can drive it.

`LoginActivity` itself stays. Manual launches, MDM auto-login, shortcut launches, and `needAnotherAppLogin()` re-prompts still need it.

## Proposed Flow

When an FLW launches a learn/deliver app from any Connect page:

1. The fragment shows a non-dismissable `CustomProgressDialog`. First message: *"Setting up the app…"*.
2. `ConnectAppLauncher` runs through:
   1. `closeUserSession()`.
   2. Seat the app if needed (no `SeatAppActivity`).
   3. Resolve credentials from `ConnectLinkedAppRecord`.
   4. Local-then-remote login. Dialog updates to *"Signing you in…"*.
   5. Data pull. Dialog updates to *"Syncing data…"* with percentage where available.
   6. Post-success side-effects (analytics, notification clears, etc.).
3. On success → fragment starts the appropriate Home or Connect Opportunity Info activity. Dialog dismisses.
4. On failure → dialog dismisses, fragment routes by reason:
   - **Global token-denied handler** — for `TokenRequestDenied`. The fragment delegates to [`TokenExceptionHandler.handleTokenDeniedException()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/network/TokenExceptionHandler.kt#L19) and shows no in-fragment dialog. This matches the established Connect-API behavior for token denials.
   - **Damaged-app routing** — for seat failures. `CommCareApplication.initializeAppResources()` already marks the app `STATE_CORRUPTED` and reports the exception via `ForceCloseLogger` / `CrashUtil`. Preserve [`DispatchActivity.handleDamagedApp()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L292) by starting `DispatchActivity` with `FLAG_ACTIVITY_CLEAR_TOP`; its existing `STATE_CORRUPTED` check at [`DispatchActivity.java:211`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L211) routes to `RecoveryActivity`.
   - **Retry / Cancel error dialog** — for every other failure: sync failures (including sync-payload parsing errors [`PullTaskResult.BAD_DATA`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/DataPullTask.java#L421) / `BAD_DATA_REQUIRES_INTERVENTION` where `DataPullTask.wipeLoginIfItOccurred()` has already cleaned up partial state), network failures, and credential / linked-app-record / linkage failures. The realistic failure modes here are network and token denial; the credential-side failures are unexpected and shouldn't occur in normal use. Every case logs to `Logger` + Firebase with a specific reason string and surfaces a generic retry/cancel dialog. There is no in-fragment forget-PersonalID flow — the existing sidebar "Sign Out" option remains the recovery surface if a user genuinely gets stuck.

## Current Flow

1. [`ConnectJobsListsFragment.launchAppForJob()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java#L154) → [`ConnectAppUtils.launchApp()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/ConnectAppUtils.kt#L117) → [`closeUserSession()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/CommCareApplication.java#L390) → [`CommCareLauncher.launchCommCareForAppId(...)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/commcare-support-library/src/main/java/org/commcare/commcaresupportlibrary/CommCareLauncher.java#L20) with [`IS_LAUNCH_FROM_CONNECT=true`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/ConnectAppUtils.kt#L18).
2. [`DispatchActivity`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java) sees no session → [`launchLoginScreen()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L321).
3. [`LoginActivity.onResume()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L321) sees the Connect flag → auto-invokes [`doLogin("AUTO")`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L264) with the password from [`ConnectLinkedAppRecord`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/android/database/connect/models/ConnectLinkedAppRecord.java).
4. [`ManageKeyRecordTask`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/ManageKeyRecordTask.java) validates / unwraps the key; [`CommCareApplication.startUserSession()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/CommCareApplication.java#L374) runs sync.
5. [`LoginActivity.dataPullCompleted()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L491) packs flags ([`REDIRECT_TO_CONNECT_OPPORTUNITY_INFO`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L68), [`MANUAL_SWITCH_TO_PW_MODE`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L115), etc.) into the result Intent and finishes.
6. [`DispatchActivity.onActivityResult(LOGIN_USER)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L491) reads the flags and routes to Connect opp info or home.

The user sees `LoginActivity` flash during steps 3–5, and [`SeatAppActivity`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/SeatAppActivity.java) for seconds-to-a-minute if the app isn't seated.

## What Makes This Hard

Everything below currently lives inside `LoginActivity` or its result contract with `DispatchActivity`:

- **Auth logic is methods on the activity.** `doLogin()`, [`tryLocalLogin()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L436), [`localLoginOrPullAndLogin()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L819), `dataPullCompleted()` all reference `this` for UI / snackbar / error rendering.
- **Routing flags are produced only in [`LoginActivity.setResultAndFinish()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L538)** and consumed only in `DispatchActivity.onActivityResult(LOGIN_USER, ...)`.
- **Post-success side-effects are scattered** through the lifecycle: [`CrashUtil.registerUserData`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/utils/CrashUtil.java#L45), notification clears, multiple [`FirebaseAnalyticsUtil`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/google/services/analytics/FirebaseAnalyticsUtil.java) calls, [`ConnectJobHelper.updateJobProgress`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/ConnectJobHelper.kt#L42), [`PersonalIdManager.updateAppAccess`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/PersonalIdManager.java#L204), [`seatAppIfNeeded`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L964).

## Phases

Five phases, each a separate JIRA ticket, listed in dependency order. Classes introduced in early phases are immediately usable by existing `LoginActivity` flows; the silent path only comes together in Phase 3. Phase 5 is deferred pending the planned Connect redesign.

### Phase 1 — Headless login engine

**Goal:** A non-UI caller can perform a login. `LoginActivity` becomes a thin wrapper around the new engine with no visible behavior change.

**New files** (all under `org.commcare.login`, Kotlin):

- **`LoginController`** — single entry point:

  ```kotlin
  suspend fun performLogin(
      request: LoginRequest,
      progressSink: LoginProgressSink,
  ): LoginResult
  ```

  Constructor dependencies: `KeyRecordOperations`, `SyncOperations`, `PostLoginSideEffects`, `DemoLoginPath`. Holds no Activity reference; `progressSink` is passed per-call by the caller (activity-scoped). Internally preserves today's [`LoginActivity.localLoginOrPullAndLogin()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L819) sequence: try local first against the cached [`UserKeyRecord`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/android/database/app/models/UserKeyRecord.java), fall back to remote key-record retrieval + data pull. Post-success side-effects run inside `withContext(NonCancellable)` so analytics still emit if the caller cancels after success completes.

  `LoginController` is unlock-agnostic: callers are expected to have already unlocked PersonalID before invoking `performLogin`. The Connect silent path satisfies this via the [`ConnectActivity.onCreate`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/connect/ConnectActivity.java#L73) gate. `LoginActivity`'s manual PersonalID-managed flow continues to call [`PersonalIdUnlocker.INSTANCE.unlock(...)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L243) before invoking the controller — unchanged from today.

- **`LoginRequest`** — `appId`, `username`, `password`, `mode` (`Manual`, `AutoFromConnect`, `MdmManaged`, `Demo`), `restoreSession`.

- **`LoginProgressSink`** — single-method interface: `fun onProgress(progress: LoginProgress)`. `LoginProgress` carries a phase tag (`Seating`, `SigningIn`, `Syncing`) plus an optional percentage and message. Phase 1's `LoginActivity` refactor wires this to the activity's existing progress UI; Phase 3's `ConnectAppLauncher` wires it to a `CustomProgressDialog`.

- **`LoginResult`** — sealed class with two top-level variants. `Success` carries the data the routing layer needs; `Failed` wraps a `LoginError`. Variants do not produce destinations directly (`PostLoginRouter` does that in Phase 2):
  - `Success(loginMode: LoginMode, restoreSession: Boolean, manualSwitchToPwMode: Boolean, personalIdManagedLogin: Boolean)`
  - `Failed(error: LoginError)`

- **`LoginError`** — sealed class describing the failure mode. Splitting `Failed(LoginError)` out from the top-level result keeps callers' `when` blocks shallow when they only care about success vs failure, and lets new callers (Phase 3 fragment, Phase 4 fragments) opt into exhaustive error handling without each one re-listing every variant:
  - `BadCredentials` — both local and remote auth failed
  - `LinkedAppRecordMissing` — `ConnectCredentialResolver` returned null
  - `ConnectLinkageInvalid` — auth token unavailable; PersonalID linkage is stale
  - `TokenDenied` — server denied the SSO token request (`HttpCalloutOutcomes.TokenRequestDenied`). The fragment delegates to the existing global handler ([`TokenExceptionHandler.handleTokenDeniedException()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/network/TokenExceptionHandler.kt#L19) → [`ConnectDatabaseHelper.handleGlobalError(GlobalErrors.PERSONALID_LOST_CONFIGURATION_ERROR)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/database/ConnectDatabaseHelper.java#L84)), matching the established Connect-API behavior for this outcome — no in-fragment dialog
  - `SyncFailed(reason)` — covers all sync-side failures, including [`PullTaskResult.BAD_DATA`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/DataPullTask.java#L421) and `BAD_DATA_REQUIRES_INTERVENTION` (sync-response parsing errors); `reason` carries the underlying enum / error message for logging and dialog body text
  - `NetworkUnavailable`

  **Mapping from existing task outcomes** (use this verbatim — implementers should not invent new mappings). Source enums: [`HttpCalloutOutcomes`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/network/HttpCalloutTask.java#L37) (from [`ManageKeyRecordTask`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/ManageKeyRecordTask.java)) and [`PullTaskResult`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/DataPullTask.java#L716). Non-`Success` rows produce `Failed(<variant>)`; the `Failed(...)` wrapping is elided in the table for brevity.

  | Source | `LoginResult` |
  |---|---|
  | `HttpCalloutOutcomes.Success` (+ pull success) | `Success` |
  | `HttpCalloutOutcomes.AuthFailed`, `PullTaskResult.AUTH_FAILED` | `BadCredentials` |
  | `HttpCalloutOutcomes.NetworkFailure`, `NetworkFailureBadPassword`, `CaptivePortal` | `NetworkUnavailable` |
  | `HttpCalloutOutcomes.TokenRequestDenied` | `TokenDenied` |
  | `HttpCalloutOutcomes.TokenUnavailable`, `IncorrectPin`, `AuthOverHttp` | `ConnectLinkageInvalid` |
  | `HttpCalloutOutcomes.BadResponse`, `BadSslCertificate`, `UnknownError`, `InsufficientRolePermission` | `SyncFailed(reason)` |
  | `PullTaskResult.BAD_DATA`, `BAD_DATA_REQUIRES_INTERVENTION`, `STORAGE_FULL`, `SERVER_ERROR`, `UNREACHABLE_HOST`, `ENCRYPTION_FAILURE` | `SyncFailed(reason)` |

  `TokenRequestDenied` is carved out as its own `TokenDenied` variant because the established Connect-API behavior for this outcome is to call `TokenExceptionHandler.handleTokenDeniedException()` → `ConnectDatabaseHelper.handleGlobalError(PERSONALID_LOST_CONFIGURATION_ERROR)`. The silent path should match: delegate to the global handler instead of showing an in-fragment dialog (see Phase 3). The remaining token-adjacent outcomes (`TokenUnavailable`, `IncorrectPin`, `AuthOverHttp`) collapse into `ConnectLinkageInvalid` because PersonalID is already unlocked at this point — if the SSO token cannot be obtained for non-denial reasons, the PersonalID ↔ CommCare linkage is the broken piece.

- **`PostLoginSideEffects`** — `suspend fun runOnSuccess(context: PostLoginContext)`. Each scattered call becomes one line: `CrashUtil.registerUserData(...)`, `notificationManager.clearNotifications()`, [`setConnectJobIdForAnalytics(...)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/CommCareApplication.java#L459), `FirebaseAnalyticsUtil.report*`, `ConnectJobHelper.updateJobProgress(...)`, `PersonalIdManager.updateAppAccess(...)`.

- **`ConnectCredentialResolver`** — ports [`ConnectAppUtils.getPasswordOverride()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/ConnectAppUtils.kt#L69):

  ```kotlin
  fun resolve(appId: String, createIfNeeded: Boolean): ResolvedCredentials?
  ```

  Returns null on DB error or credential-acquisition failure (token unavailable, no network for token exchange). Existing `ConnectAppUtils` call sites delegate.

  **Note on network-vs-credential disambiguation.** The current contract collapses every non-success cause to `null`, so the controller maps the failure to `LinkedAppRecordMissing`. Realistically a no-network token exchange should surface as `NetworkUnavailable`, and a token denial should surface as `TokenDenied`. A follow-up may enrich `ResolvedCredentials` (or introduce a richer `Resolution` type) so the controller can route these correctly without the fragment having to inspect connectivity state itself. Out of scope for Phase 1.

- **`KeyRecordOperations`, `SyncOperations`** — wrap [`ManageKeyRecordTask`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/ManageKeyRecordTask.java) and [`DataPullTask`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/DataPullTask.java) in `suspendCancellableCoroutine { ... }` with `invokeOnCancellation { task.cancel(true) }`. Each receives the `LoginProgressSink` passed into `performLogin` and emits progress on it (key-record retrieval as `SigningIn`; data pull as `Syncing` with percentage where available).

  **Cancellation is best-effort.** The wrapper does not await task termination — an orphaned write to local state is possible. Phase 3's `closeUserSession()` at the start of every silent launch absorbs orphans.

- **`DemoLoginPath`** — short-circuits key-unwrap and sync for `LoginMode.Demo`.

**`LoginActivity` refactor:** [`doLogin()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L264) builds a `LoginRequest`, provides a `LoginProgressSink` wired to its existing progress UI, calls `LoginController.performLogin(...)`, and translates the `LoginResult` back into the existing `setResult(...) + finish()` contract. The pre-existing `PersonalIdUnlocker.INSTANCE.unlock(...)` call at [`LoginActivity.java:243`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L243) for manual PersonalID-managed login stays put — it runs before `performLogin` as it does today. Restore-last-user stays in the UI controller — it's a "what credentials to populate" concern, not auth.

**Acceptance:**

- All existing `LoginActivity` flows (manual, AUTO from Connect, MDM, demo, restore-last-user) keep working with no user-visible change. Biometric / PIN prompts still appear for manual PersonalID-managed login in `LoginActivity` and do not appear for AUTO-from-Connect logins.
- JVM unit tests on `LoginController`: one per `LoginResult` variant; cancellation propagates and the `NonCancellable` post-success block still runs if success arrived before cancellation.
- JVM unit tests on `PostLoginSideEffects`, `ConnectCredentialResolver`, `KeyRecordOperations`, `SyncOperations`, `DemoLoginPath`.

### Phase 2 — Routing extraction and inline seating

**Goal:** A single authority translates a `LoginResult` into a destination. Seating moves off the activity stack.

**New files** (`org.commcare.login`):

- **`PostLoginRouter`** — pure Kotlin, no Android dependencies.
  - Input: `LoginResult` + `LaunchContext` (Connect-initiated vs not, target `appId`, `restoreSession`, `jobId`).
  - Output: `PostLoginDestination`:
    - `Home(loginMode, startFromLogin, manualSwitchToPwMode, personalIdManagedLogin)`
    - `ConnectOpportunityInfo(jobId, loginMode)` — this is the existing post-login-to-Connect-opportunity routing, not new UX. Today the flag is written by [`LoginActivity.setResultAndFinish()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L540) (and also by [`HomeScreenBaseActivity`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/HomeScreenBaseActivity.java#L589) when returning from a Connect-launched session) and consumed by [`DispatchActivity.onActivityResult(LOGIN_USER)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L494). `PostLoginRouter` becomes the single producer; consumers are unchanged
    - `TerminalFailure(reason: FailureReason)`
  - `FailureReason` sealed class: `object BadCredentials`, `object LinkedAppRecordMissing`, `object ConnectLinkageInvalid`, `object TokenDenied`, `object AppSeatFailed`, `data class SyncFailed(val reason: String)`, `object NetworkUnavailable`, `object AlreadyLaunching`. (Parameterless reasons are `object`s; only `SyncFailed` carries data.)

- **`PostLoginDestination.toIntent(context, launchContext): Intent`** — extension defined only on `Home` and `ConnectOpportunityInfo`. The string extras consumed by [`RootMenuHomeActivity`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/RootMenuHomeActivity.java) / [`StandardHomeActivity`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/StandardHomeActivity.java) / [`ConnectNavHelper`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/ConnectNavHelper.kt) are unchanged; this is now the single producer.

- **`AppSeater`** — `suspend fun seatIfNeeded(appId: String, sink: LoginProgressSink): SeatResult`. Ports the body of [`SeatAppActivity.SeatAppProcess.run()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/SeatAppActivity.java#L100) into a suspend function that wraps `CommCareApplication.instance().initializeAppResources(...)` in `withContext(Dispatchers.IO)`. Preserve existing try/catch/finally and `FirebaseAnalyticsUtil` reporting verbatim — caught exceptions surface as `SeatResult.Failed(reason)`. Used by `ConnectAppLauncher` in Phase 3.

  **Damaged-state contract.** On `SeatResult.Failed`, `AppSeater` must guarantee that the seated app's resource state is `CommCareApplication.STATE_CORRUPTED`. `initializeAppResources()` already flips it on the failure paths SeatAppActivity sees today, but `AppSeater` should defensively set it via `CommCareApp.setAppResourceState(STATE_CORRUPTED)` inside the catch block as well. Phase 3's seat-failure routing assumes this state is set when it bounces back to `DispatchActivity`.

  `LoginActivity` keeps using `SeatAppActivity` via the existing `seatAppIfNeeded()` — migrating that path is out of scope.

**`DispatchActivity` refactor:** [`onActivityResult(LOGIN_USER, ...)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L491) no longer encodes routing rules inline. It calls `PostLoginRouter.route(...)` and uses `toIntent(...)`.

**Acceptance:**

- No behavior change for `LoginActivity` flows.
- All writes into Home-activity extras go through `toIntent(...)`.
- JVM unit tests on `PostLoginRouter`, `PostLoginDestination.toIntent` (table-driven), `AppSeater` (Firebase reporting preserved on failure).

### Phase 3 — Silent launch path (Connect opportunities list)

**Goal:** `ConnectAppLauncher` orchestrates the full silent path with progress UI. `ConnectJobsListsFragment` uses it end-to-end. Other Connect entry points are migrated in Phase 4.

**New file:** `org.commcare.connect.applauncher.ConnectAppLauncher` (Kotlin).

```kotlin
class ConnectAppLauncher(
    private val loginController: LoginController,
    private val postLoginRouter: PostLoginRouter,
    private val appSeater: AppSeater,
    private val credentialResolver: ConnectCredentialResolver,
) {
    suspend fun launch(
        appId: String,
        isLearning: Boolean,
        jobId: String,
        sink: LoginProgressSink,
    ): SilentLaunchOutcome
}

sealed class SilentLaunchOutcome {
    data class Success(val intent: Intent) : SilentLaunchOutcome()
    data class Failed(val reason: FailureReason) : SilentLaunchOutcome()
}
```

No unlock host parameter: PersonalID is unlocked by `ConnectActivity`'s entry gate before any caller reaches this point.

**Concurrency.** A single `Mutex` (or `AtomicBoolean`) inside `ConnectAppLauncher` rejects re-entrant `launch()` calls with `Failed(AlreadyLaunching)`. Callers do not need their own lock. Future entry points (including the eventual redesigned Opportunity Home) inherit the guard for free.

**Orchestration:**

1. Try to acquire the in-flight lock. If held → return `Failed(AlreadyLaunching)`.
2. `closeUserSession()`.
3. `appSeater.seatIfNeeded(appId, sink)`. `sink` shows *"Setting up the app…"*. On `SeatResult.Failed` → `Failed(AppSeatFailed)`. The seat failure has already flipped the app's resource state to `STATE_CORRUPTED` inside `CommCareApplication.initializeAppResources()`, which the fragment uses to drive damaged-app routing.
4. `credentialResolver.resolve(appId, createIfNeeded = true)`. Null → `Failed(LinkedAppRecordMissing)`.
5. `sink` shows *"Signing you in…"*. Call `loginController.performLogin(request, sink)`. While the data pull runs (inside `performLogin`), `SyncOperations`'s progress lambda updates the sink to *"Syncing data…"* with percentage where available.
6. Translate the `LoginResult` via `PostLoginRouter`:
   - `Success` → `Home` / `ConnectOpportunityInfo` → `toIntent(...)` → `SilentLaunchOutcome.Success(intent)`.
   - Any failure → `Failed(<corresponding reason>)`.
7. Release the in-flight lock in `finally`.

`CancellationException` propagates from `launch`; the caller's scope handles it.

**Caller wiring — [`ConnectJobsListsFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java):**

- Launch from `viewLifecycleOwner.lifecycleScope`. Backgrounding cancels cleanly; user re-taps on return.
- Show [`CustomProgressDialog`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/views/dialogs/CustomProgressDialog.java) before calling `launch()`. The fragment provides a `LoginProgressSink` that updates the dialog's message text.
- No per-fragment orientation lock is needed — `ConnectActivity` is already declared `android:screenOrientation="portrait"` in [`AndroidManifest.xml`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/AndroidManifest.xml#L192), so the in-flight coroutine state never has to survive a rotation.
- Routing per outcome:

  | Outcome | Action                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
  |---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
  | `Success(intent)` | `startActivity(intent)`, dismiss dialog                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
  | `Failed(BadCredentials)`, `Failed(LinkedAppRecordMissing)`, `Failed(ConnectLinkageInvalid)` | Log via `Logger` + Firebase with a specific reason string (`"connect_login_bad_credentials"`, `"connect_login_linked_record_missing"`, `"connect_login_linkage_invalid"`), then show the same retry/cancel dialog used for `SyncFailed` / `NetworkUnavailable`. These outcomes are not expected in normal use — `LinkedAppRecordMissing` shouldn't happen, and the realistic credential-side failure modes are either network errors or token denials, both routed through their own outcomes. If a user genuinely gets stuck after retrying, the existing "Forget PersonalID" flow is the manual recovery surface                                                                                                                                                                                                                       |
  | `Failed(TokenDenied)` | Dismiss the progress dialog and call [`TokenExceptionHandler.handleTokenDeniedException()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/network/TokenExceptionHandler.kt#L19); the existing global error handler takes over from there. No in-fragment dialog                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
  | `Failed(AppSeatFailed)` | Log via `Logger` + Firebase; dismiss the progress dialog. The seat failure has already set the app's resource state to `STATE_CORRUPTED`, so route through the existing damaged-app path: `startActivity(Intent(requireContext(), DispatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))` and call `requireActivity().finish()`. `DispatchActivity.onCreate` will detect `STATE_CORRUPTED` at [line 211](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L211) and call [`handleDamagedApp()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L292) which launches `RecoveryActivity` |
  | `Failed(SyncFailed)`, `Failed(NetworkUnavailable)` | [`StandardAlertDialog`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/views/dialogs/StandardAlertDialog.java) with Retry / Cancel. Retry replays `launch()`. `BAD_DATA` / `BAD_DATA_REQUIRES_INTERVENTION` arrive here as `SyncFailed(reason)`; `DataPullTask` has already called `wipeLoginIfItOccurred()`, so no extra cleanup is needed                                                                                                                                                                                                                                                                                                                                                                                                                               |
  | `Failed(AlreadyLaunching)` | Silent no-op                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |

**Not introduced:** `SKIP_AUTO_LOGIN`. `IS_LAUNCH_FROM_CONNECT` is left alone — the non-Connect-fragment call sites still set it. There is no manual-escalation re-entry to `LoginActivity` for Connect launches.

**Acceptance:**

- Tapping an opportunity for a healthy account: no `LoginActivity` or `SeatAppActivity`. Progress dialog visible throughout with phase-specific messages. Lands on Home or Opportunity Info as appropriate. Time-to-home drops by at least one Activity transition.
- Tapping again while a launch is in flight: silent no-op.
- No biometric / PIN prompt appears at any point in the silent launch.
- `BadCredentials` / `LinkedAppRecordMissing` / `ConnectLinkageInvalid`: each is logged via `Logger` + Firebase with the distinct reason string; the fragment shows the same retry/cancel dialog used for `SyncFailed` / `NetworkUnavailable`; no `forgetUser` call is made from the silent path.
- `TokenDenied`: progress dialog dismisses; `TokenExceptionHandler.handleTokenDeniedException()` is invoked exactly once; no in-fragment dialog is shown.
- `AppSeatFailed`: `STATE_CORRUPTED` is set on the `ApplicationRecord`, `DispatchActivity` is launched, `RecoveryActivity` appears.
- Backgrounding mid-launch cancels cleanly; re-tap on return starts fresh.
- JVM unit tests: happy path, each failure branch, in-flight lock rejection, `BAD_DATA` and `BAD_DATA_REQUIRES_INTERVENTION` both map to `SyncFailed(reason)`, `Demo` mode rejected.
- Robolectric tests: dialog show/dismiss on every termination path; `TokenExceptionHandler.handleTokenDeniedException()` invoked on the `TokenDenied` path; `DispatchActivity` start intent on the seat-failure path.

### Phase 4 — Roll out to remaining priority entry points

**Goal:** The Connect launch surfaces that are staying through the planned redesign use `ConnectAppLauncher`. Clean up dead code on those surfaces.

**Wire to `ConnectAppLauncher`** (same pattern as Phase 3):

- [`ConnectDeliveryProgressFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectDeliveryProgressFragment.java) (Delivery Progress page → Resume button)
- [`ConnectLearningProgressFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectLearningProgressFragment.java)

**Cleanup:**

- `ConnectAppUtils.launchApp(...)` becomes a thin delegate to `ConnectAppLauncher` for the migrated sites. The two remaining call sites covered in Phase 5 continue to use the existing implementation until then.
- `IS_LAUNCH_FROM_CONNECT` removal is deferred to Phase 5, since the Phase 5 call sites still set it.

**Acceptance:**

- Both Phase 4 launch surfaces use the silent path end-to-end.
- One Instrumentation smoke test per surface (tap → home).
- Manual QA: `LoginActivity` manual login, MDM auto-login, restore-last-user, demo mode all still work.

### Phase 5 — Migrate or delete the redesign-bound entry points (deferred)

**Goal:** Decide whether the two remaining Connect launch surfaces are worth migrating or are getting deleted in the planned redesign, and act accordingly.

**Candidates:**

- [`ConnectJobIntroFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectJobIntroFragment.java)
- [`ConnectDownloadingFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectDownloadingFragment.java)

**Rationale for deferral:** Both screens are slated for removal in the planned Connect redesign. Migrating them now risks throwaway work. Revisit once the redesign scope is firm — if the screens survive, repeat the Phase 4 wiring; if they're deleted, drop these entries and the leftover `ConnectAppUtils.launchApp(...)` references along with them.

**Cleanup at Phase 5 completion:**

- `ConnectAppUtils.launchApp(...)` becomes a thin delegate to `ConnectAppLauncher` for any remaining sites (or is removed entirely).
- If `IS_LAUNCH_FROM_CONNECT` has no remaining readers after this phase, delete it.

## Out Of Scope

- Deleting `LoginActivity`. Manual launches, MDM, shortcuts, and [`needAnotherAppLogin()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/DispatchActivity.java#L251) still need it.
- Reworking `RootMenuHomeActivity` / `StandardHomeActivity` to consume `PostLoginDestination` directly. They keep reading string extras; `toIntent(...)` is the single producer.
- Migrating `LoginActivity`'s seat path off `SeatAppActivity`.
- Cooperative cancellation inside `ManageKeyRecordTask` / `DataPullTask`.
- First-time-on-device flow (no `UserKeyRecord` yet — must sync with `LoginActivity` UI visible).
- Multi-user device flows where switching apps requires re-prompt.
- Auth-token refresh strategy.
- Silent-login duration telemetry.
- Switching password storage from `String` to `CharArray`.
- Silent password rotation against Connect/HQ — no rotate-password API exists. Revisit the recovery policy if one is added.

## Citation Caveat

Permalinks in this plan are pinned to master at commit `684512e2662996c9855e35af306da3e311e56c80`. Line numbers will stay stable at that pin, but when implementing a phase, also check `master` HEAD — `LoginActivity.java` in particular churns frequently and the surrounding context may have shifted.

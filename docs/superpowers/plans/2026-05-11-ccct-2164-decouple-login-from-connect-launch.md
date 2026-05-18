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
   - **Forget-PersonalID prompt** — for credential, linked-app-record, and Connect-linkage failures. These mean the PersonalID-managed credentials are no longer valid and there is no in-place recovery path. Log the incident (Logger + Firebase), then show a `StandardAlertDialog` telling the user their PersonalID account needs to be re-established, with primary action that calls [`personalIdManager.forgetUser(reason)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/PersonalIdManager.java#L165). After forgetting, the user lands back at the PersonalID intro screen, which exposes the existing account-recovery path (backup code, etc.).
   - **Retry-unlock prompt** — for PersonalID-unlock failures. The user may have just cancelled the biometric / PIN prompt, so do not auto-forget. Log, show a `StandardAlertDialog` with Retry / Cancel; Retry replays the silent launch.
   - **Existing app-recovery handling** — for seat failures and sandbox corruption. Whatever `SeatAppActivity` / `LoginActivity` do for these today is preserved; if no recovery exists, fall back to a `StandardAlertDialog` + log.
   - **Retry / Cancel error dialog** — for transient sync or network failures.

No path ever shows the user a password field for the CommCare app.

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
- **PersonalID unlock needs an activity host** for biometric / PIN prompts.
- **Routing flags are produced only in [`LoginActivity.setResultAndFinish()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L538)** and consumed only in `DispatchActivity.onActivityResult(LOGIN_USER, ...)`.
- **Post-success side-effects are scattered** through the lifecycle: [`CrashUtil.registerUserData`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/utils/CrashUtil.java#L45), notification clears, multiple [`FirebaseAnalyticsUtil`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/google/services/analytics/FirebaseAnalyticsUtil.java) calls, [`ConnectJobHelper.updateJobProgress`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/ConnectJobHelper.kt#L42), [`PersonalIdManager.updateAppAccess`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/PersonalIdManager.java#L204), [`seatAppIfNeeded`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L964).

## Phases

Four phases, each a separate JIRA ticket, listed in dependency order. Classes introduced in early phases are immediately usable by existing `LoginActivity` flows; the silent path only comes together in Phase 3.

### Phase 1 — Headless login engine

**Goal:** A non-UI caller can perform a login. `LoginActivity` becomes a thin wrapper around the new engine with no visible behavior change.

**New files** (all under `org.commcare.login`, Kotlin):

- **`LoginController`** — single entry point:

  ```kotlin
  suspend fun performLogin(
      request: LoginRequest,
      unlockHost: PersonalIdUnlockHost,
      progressSink: LoginProgressSink,
  ): LoginResult
  ```

  Constructor dependencies: `KeyRecordOperations`, `SyncOperations`, `PostLoginSideEffects`, `DemoLoginPath`. Holds no Activity reference; `unlockHost` and `progressSink` are passed per-call by the caller (both are activity-scoped). Internally preserves today's [`LoginActivity.localLoginOrPullAndLogin()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L819) sequence: try local first against the cached [`UserKeyRecord`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/android/database/app/models/UserKeyRecord.java), fall back to remote key-record retrieval + data pull. Post-success side-effects run inside `withContext(NonCancellable)` so analytics still emit if the caller cancels after success completes.

- **`LoginRequest`** — `appId`, `username`, `password`, `mode` (`Manual`, `AutoFromConnect`, `MdmManaged`, `Demo`), `restoreSession`.

- **`LoginProgressSink`** — single-method interface: `fun onProgress(progress: LoginProgress)`. `LoginProgress` carries a phase tag (`Seating`, `SigningIn`, `Syncing`) plus an optional percentage and message. Phase 1's `LoginActivity` refactor wires this to the activity's existing progress UI; Phase 3's `ConnectAppLauncher` wires it to a `CustomProgressDialog`.

- **`LoginResult`** — sealed class. Variants carry the raw data the routing layer needs; they do not produce destinations directly (`PostLoginRouter` does that in Phase 2):
  - `Success(loginMode: LoginMode, restoreSession: Boolean, manualSwitchToPwMode: Boolean, personalIdManagedLogin: Boolean)`
  - `BadCredentials` — both local and remote auth failed
  - `LinkedAppRecordMissing` — `ConnectCredentialResolver` returned null
  - `ConnectLinkageInvalid`
  - `SandboxCorrupted`
  - `SyncFailed(reason)`
  - `NetworkUnavailable`
  - `PersonalIdUnlockRequired`

  **Mapping from existing task outcomes** (use this verbatim — implementers should not invent new mappings). Source enums: [`HttpCalloutOutcomes`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/network/HttpCalloutTask.java#L37) (from [`ManageKeyRecordTask`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/ManageKeyRecordTask.java)) and [`PullTaskResult`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/DataPullTask.java#L716).

  | Source | `LoginResult` |
  |---|---|
  | `HttpCalloutOutcomes.Success` (+ pull success) | `Success` |
  | `HttpCalloutOutcomes.AuthFailed`, `PullTaskResult.AUTH_FAILED` | `BadCredentials` |
  | `HttpCalloutOutcomes.NetworkFailure`, `NetworkFailureBadPassword`, `CaptivePortal` | `NetworkUnavailable` |
  | `HttpCalloutOutcomes.TokenUnavailable`, `TokenRequestDenied`, `IncorrectPin`, `AuthOverHttp` | `PersonalIdUnlockRequired` |
  | `HttpCalloutOutcomes.BadResponse`, `BadSslCertificate`, `UnknownError`, `InsufficientRolePermission` | `SyncFailed(reason)` |
  | `PullTaskResult.BAD_DATA`, `BAD_DATA_REQUIRES_INTERVENTION` | `SandboxCorrupted` |
  | `PullTaskResult.STORAGE_FULL`, `SERVER_ERROR`, `UNREACHABLE_HOST`, `ENCRYPTION_FAILURE` | `SyncFailed(reason)` |

- **`PersonalIdUnlockHost`** — interface for biometric / PIN prompts:

  ```kotlin
  interface PersonalIdUnlockHost {
      suspend fun requestUnlock(): UnlockResult
  }
  sealed class UnlockResult { object Granted; object Cancelled; object HostUnavailable; data class Failed(val reason: String) }
  ```

  `ActivityPersonalIdUnlockHost(activity)` holds a `WeakReference<AppCompatActivity>` and calls `personalIdManager.unlockConnect()`. Returns `HostUnavailable` if the weak ref is null. `LoginController` never sees `PersonalIdManager` directly.

- **`PostLoginSideEffects`** — `suspend fun runOnSuccess(context: PostLoginContext)`. Each scattered call becomes one line: `CrashUtil.registerUserData(...)`, `notificationManager.clearNotifications()`, [`setConnectJobIdForAnalytics(...)`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/CommCareApplication.java#L459), `FirebaseAnalyticsUtil.report*`, `ConnectJobHelper.updateJobProgress(...)`, `PersonalIdManager.updateAppAccess(...)`.

- **`ConnectCredentialResolver`** — ports [`ConnectAppUtils.getPasswordOverride()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/ConnectAppUtils.kt#L69):

  ```kotlin
  fun resolve(appId: String, createIfNeeded: Boolean): ResolvedCredentials?
  ```

  Returns null on DB error or credential-acquisition failure (token unavailable, no network for token exchange). Existing `ConnectAppUtils` call sites delegate.

- **`KeyRecordOperations`, `SyncOperations`** — wrap [`ManageKeyRecordTask`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/ManageKeyRecordTask.java) and [`DataPullTask`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/tasks/DataPullTask.java) in `suspendCancellableCoroutine { ... }` with `invokeOnCancellation { task.cancel(true) }`. Each receives the `LoginProgressSink` passed into `performLogin` and emits progress on it (key-record retrieval as `SigningIn`; data pull as `Syncing` with percentage where available).

  **Cancellation is best-effort.** The wrapper does not await task termination — an orphaned write to local state is possible. Phase 3's `closeUserSession()` at the start of every silent launch absorbs orphans.

- **`DemoLoginPath`** — short-circuits key-unwrap and sync for `LoginMode.Demo`.

**`LoginActivity` refactor:** [`doLogin()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/LoginActivity.java#L264) builds a `LoginRequest`, provides an `ActivityPersonalIdUnlockHost(this)` and a `LoginProgressSink` wired to its existing progress UI, calls `LoginController.performLogin(...)`, and translates the `LoginResult` back into the existing `setResult(...) + finish()` contract. Restore-last-user stays in the UI controller — it's a "what credentials to populate" concern, not auth.

**Acceptance:**

- All existing `LoginActivity` flows (manual, AUTO from Connect, MDM, demo, restore-last-user) keep working with no user-visible change.
- JVM unit tests on `LoginController`: one per `LoginResult` variant; cancellation propagates and the `NonCancellable` post-success block still runs if success arrived before cancellation.
- JVM unit tests on `PostLoginSideEffects`, `ConnectCredentialResolver`, `KeyRecordOperations`, `SyncOperations`, `DemoLoginPath`.

### Phase 2 — Routing extraction and inline seating

**Goal:** A single authority translates a `LoginResult` into a destination. Seating moves off the activity stack.

**New files** (`org.commcare.login`):

- **`PostLoginRouter`** — pure Kotlin, no Android dependencies.
  - Input: `LoginResult` + `LaunchContext` (Connect-initiated vs not, target `appId`, `restoreSession`, `jobId`).
  - Output: `PostLoginDestination`:
    - `Home(loginMode, startFromLogin, manualSwitchToPwMode, personalIdManagedLogin)`
    - `ConnectOpportunityInfo(jobId, loginMode)`
    - `TerminalFailure(reason: FailureReason)`
    - `PersonalIdUnlockNeeded`
  - `FailureReason` enum: `BadCredentials`, `LinkedAppRecordMissing`, `ConnectLinkageInvalid`, `SandboxCorrupted`, `AppSeatFailed`, `SyncFailed`, `NetworkUnavailable`, `PersonalIdUnlockFailed`, `AlreadyLaunching`.

- **`PostLoginDestination.toIntent(context, launchContext): Intent`** — extension defined only on `Home` and `ConnectOpportunityInfo`. The string extras consumed by [`RootMenuHomeActivity`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/RootMenuHomeActivity.java) / [`StandardHomeActivity`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/StandardHomeActivity.java) / [`ConnectNavHelper`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/ConnectNavHelper.kt) are unchanged; this is now the single producer.

- **`AppSeater`** — `suspend fun seatIfNeeded(appId: String, sink: LoginProgressSink): SeatResult`. Ports the body of [`SeatAppActivity.SeatAppProcess.run()`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/activities/SeatAppActivity.java#L100) into a suspend function that wraps `CommCareApplication.instance().initializeAppResources(...)` in `withContext(Dispatchers.IO)`. Preserve existing try/catch/finally and `FirebaseAnalyticsUtil` reporting verbatim — caught exceptions surface as `SeatResult.Failed(reason)`. Used by `ConnectAppLauncher` in Phase 3.

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
        host: PersonalIdUnlockHost,
        sink: LoginProgressSink,
    ): SilentLaunchOutcome
}

sealed class SilentLaunchOutcome {
    data class StartActivity(val intent: Intent) : SilentLaunchOutcome()
    data class Failed(val reason: FailureReason) : SilentLaunchOutcome()
}
```

**Concurrency.** A single `Mutex` (or `AtomicBoolean`) inside `ConnectAppLauncher` rejects re-entrant `launch()` calls with `Failed(AlreadyLaunching)`. Callers do not need their own lock. Future entry points (including the eventual redesigned Opportunity Home) inherit the guard for free.

**Orchestration:**

1. Try to acquire the in-flight lock. If held → return `Failed(AlreadyLaunching)`.
2. `closeUserSession()`.
3. `appSeater.seatIfNeeded(appId, sink)`. `sink` shows *"Setting up the app…"*. On `SeatResult.Failed` → `Failed(AppSeatFailed)`.
4. `credentialResolver.resolve(appId, createIfNeeded = true)`. Null → `Failed(LinkedAppRecordMissing)`.
5. `sink` shows *"Signing you in…"*. Call `loginController.performLogin(request)`. While the data pull runs (inside `performLogin`), `SyncOperations`'s progress lambda updates the sink to *"Syncing data…"* with percentage where available.
6. Translate the `LoginResult` via `PostLoginRouter`:
   - `Success` / `Home` / `ConnectOpportunityInfo` → `toIntent(...)` → `StartActivity`.
   - `PersonalIdUnlockNeeded` → call `host.requestUnlock()`. On `Granted`, retry `performLogin` exactly once. If the retry also yields `PersonalIdUnlockNeeded`, or on `Cancelled` / `Failed` / `HostUnavailable` → `Failed(PersonalIdUnlockFailed)`.
   - Any other failure → `Failed(<corresponding reason>)`.
7. Release the in-flight lock in `finally`.

`CancellationException` propagates from `launch`; the caller's scope handles it.

**Caller wiring — [`ConnectJobsListsFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectJobsListsFragment.java):**

- Launch from `viewLifecycleOwner.lifecycleScope`. Backgrounding cancels cleanly; user re-taps on return.
- Show [`CustomProgressDialog`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/views/dialogs/CustomProgressDialog.java) before calling `launch()`. The fragment provides a `LoginProgressSink` that updates the dialog's message text.
- Lock orientation on entry, restore on exit via `try/finally`. The lock guards in-flight coroutine state across configuration changes; the dialog itself survives rotation via `DialogFragment`.
- Routing per outcome:

  | Outcome | Action |
  |---|---|
  | `StartActivity(intent)` | `startActivity(intent)`, dismiss dialog |
  | `Failed(BadCredentials)` | Log via `Logger` + Firebase; show `StandardAlertDialog` ("Your PersonalID account needs to be re-established") → on confirm, call [`personalIdManager.forgetUser("connect_login_bad_credentials")`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/connect/PersonalIdManager.java#L165). User lands at PersonalID intro, which exposes the existing backup-code / recovery path |
  | `Failed(LinkedAppRecordMissing)` | Same as `BadCredentials`. Should not occur in normal use — log as an unexpected state (reason `"connect_login_linked_record_missing"`) |
  | `Failed(ConnectLinkageInvalid)` | Same as `BadCredentials` (reason `"connect_login_linkage_invalid"`) |
  | `Failed(PersonalIdUnlockFailed)` | Log; show `StandardAlertDialog` with Retry / Cancel. Retry replays `launch()`. Do **not** call `forgetUser` — the failure may just be a cancelled biometric prompt |
  | `Failed(AppSeatFailed)` | Preserve today's seat-failure handling from `SeatAppActivity` (trace it during implementation; if no recovery exists, show `StandardAlertDialog` + log) |
  | `Failed(SandboxCorrupted)` | Preserve today's sandbox-corruption handling from `LoginActivity` (likewise trace; falls back to `StandardAlertDialog` + log if none) |
  | `Failed(SyncFailed)`, `Failed(NetworkUnavailable)` | [`StandardAlertDialog`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/views/dialogs/StandardAlertDialog.java) with Retry / Cancel. Retry replays `launch()` |
  | `Failed(AlreadyLaunching)` | Silent no-op |

**Not introduced:** `SKIP_AUTO_LOGIN`. `IS_LAUNCH_FROM_CONNECT` is left alone — the non-Connect-fragment call sites still set it. There is no manual-escalation re-entry to `LoginActivity` for Connect launches.

**Acceptance:**

- Tapping an opportunity for a healthy account: no `LoginActivity` or `SeatAppActivity`. Progress dialog visible throughout with phase-specific messages. Lands on Home or Opportunity Info as appropriate. Time-to-home drops by at least one Activity transition.
- Tapping again while a launch is in flight: silent no-op.
- Each failure branch routes to the correct existing recovery flow (verified manually + with Robolectric tests).
- Backgrounding mid-launch cancels cleanly; re-tap on return starts fresh.
- JVM unit tests: happy path, each failure branch, in-flight lock rejection, `PersonalIdUnlockNeeded`→granted retry, `PersonalIdUnlockNeeded`→fail (loop bounded), `Demo` mode rejected.
- Robolectric tests: dialog show/dismiss on every termination path; orientation lock/restore on every termination path.

### Phase 4 — Roll out to remaining entry points

**Goal:** All five Connect launch surfaces use `ConnectAppLauncher`. Clean up dead code.

**Wire to `ConnectAppLauncher`** (same pattern as Phase 3):

- [`ConnectDeliveryProgressFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectDeliveryProgressFragment.java) (Delivery Progress page → Resume button)
- [`ConnectDownloadingFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectDownloadingFragment.java)
- [`ConnectLearningProgressFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectLearningProgressFragment.java)
- [`ConnectJobIntroFragment`](https://github.com/dimagi/commcare-android/blob/684512e2662996c9855e35af306da3e311e56c80/app/src/org/commcare/fragments/connect/ConnectJobIntroFragment.java)

**Cleanup:**

- `ConnectAppUtils.launchApp(...)` becomes a thin delegate to `ConnectAppLauncher` for all five sites.
- If `IS_LAUNCH_FROM_CONNECT` has no remaining readers after migration, delete it. Otherwise leave alone — broader removal is out of scope.

**Acceptance:**

- All five Connect launch surfaces use the silent path end-to-end.
- One Instrumentation smoke test per surface (tap → home).
- Manual QA: `LoginActivity` manual login, MDM auto-login, restore-last-user, demo mode all still work.

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

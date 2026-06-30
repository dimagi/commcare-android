# Login Engine

The `org.commcare.login` package implements the CommCare login pipeline without an Activity dependency. A caller supplies a `LoginRequest` plus a `LoginProgressListener`, awaits a `LoginResult`, and is responsible for any post-result navigation.

## Entry point

`LoginController.performLogin(request, listener)` is the single suspend entry point. Java callers should use `LoginController.start(lifecycleOwner, request, listener, callback)`, which launches the suspend call on the owner's `lifecycleScope` and delivers the result through a SAM callback.

The controller composes:

- `ConnectCredentialResolver` — resolves the Connect-managed password for an `(appId, username)` pair when `authSource` is `PersonalId` (creating the linked-app record if missing).
- `KeyRecordOperations` — suspending wrapper around `ManageKeyRecordTask`. Yields a `KeyRecordOutcome` (`LocalLoginComplete`, `ReadyForSync`, or `Failed`).
- `SyncOperations` — suspending wrapper around `DataPullTask`. `pullData` takes the `DataPullMode` from the request and resolves a per-mode `PullPlan` (server, userId, requester, `blockRemoteKeyManagement`, payload references): `NORMAL` is the OTA pull against the data server; `CONSUMER_APP` and `CCZ_DEMO` are bundled-CCZ local restores via `LocalReferencePullResponseFactory` against a fake server.
- `PostLoginSideEffects` — the deterministic post-success chain: `CrashUtil.registerUserData`, notification clear, `setConnectJobIdForAnalytics`, `ConnectAppUtils.updateLastAccessed`, `ConnectJobHelper.updateJobProgress`. Runs inside `withContext(NonCancellable)` so analytics still fire if the caller cancels after success. Returns a `PostLoginOutcome(redirectToConnectOpportunityInfo, needsPersonalIdLinkCheck)`.
- `OutcomeMapper` — pure functions mapping `HttpCalloutOutcomes` and `PullTaskResult` to `LoginError`.

`LoginResult` is `Success(appId, username, loginMode, restoreSession, personalIdManagedLogin, linkPassword, postLoginOutcome)` or `Failed(LoginError)`. `LoginError` is a flat sealed class — each failure is its own variant (no grouping wrapper): `BadCredentials`, `TokenDenied`, `NetworkUnavailable`, `AuthOverHttpBlocked`, `BadResponse`, `BadSslCertificate`, `StorageFull`, `ServerError`, `RateLimitedServerError`, `SessionExpire`, `Cancelled`, `EmptyUrl`, `InsufficientRolePermission`, and the message-carrying `BadData`, `BadDataRequiresIntervention`, `EncryptionFailure`, `RecoveryFailure`, `ActionableFailure`, `UnknownFailure`.

## Progress events

`LoginProgress(phase, percent?, message?)` is emitted on the listener. Phases:

- `Seating` — emitted by `AppSeater` while the app is being seated (see [App seating](#app-seating)).
- `SigningIn` — `ManageKeyRecordTask` is running. `message` carries the localized status string from the underlying task.
- `Syncing` — `DataPullTask` is running. `percent` is populated when the task reports `PROGRESS_PROCESSING` or `PROGRESS_SERVER_PROCESSING`.

`LoginActivity` wires the listener to its existing `CustomProgressDialog`s (`TASK_KEY_EXCHANGE` for `SigningIn`, `DataPullTask.DATA_PULL_TASK_ID` for `Syncing`), transitioning between dialogs when the phase changes.

## Error mapping

`LoginActivity.onLoginError` translates `LoginError` to the existing `StockMessages` notifications used by the legacy `LoginActivity.handlePullTaskResult` switch:

| `LoginError` | `StockMessages` |
|---|---|
| `BadCredentials` | `Auth_BadCredentials` |
| `TokenDenied` | `TokenDenied` |
| `NetworkUnavailable` | `Remote_NoNetwork` |
| `AuthOverHttpBlocked` | `Auth_Over_HTTP` |
| `BadData(msg)` | `Remote_BadRestore` (with `msg`) |
| `BadDataRequiresIntervention(msg)` | `Remote_BadRestoreRequiresIntervention` (with `msg`) |
| `BadResponse` | `Remote_BadRestore` |
| `BadSslCertificate` | `BadSslCertificate` (with `LAUNCH_DATE_SETTINGS` button) |
| `StorageFull` | `Storage_Full` |
| `ServerError` | `Remote_ServerError` |
| `RateLimitedServerError` | `Remote_RateLimitedServerError` |
| `EncryptionFailure(msg)` | `Encryption_Error` (with `msg`) |
| `RecoveryFailure(msg)` | `Recovery_Error` (with `msg`) |
| `ActionableFailure(msg)` | Raw `NotificationMessage(msg)` |
| `SessionExpire` | `Session_Expire` |
| `Cancelled` | `Cancelled` |
| `EmptyUrl` | `Empty_Url` |
| `InsufficientRolePermission` | `Auth_InsufficientRolePermission` |
| `UnknownFailure(msg?)` | `Restore_Unknown` (with `msg` if present) |

Two `HttpCalloutOutcomes` values are coalesced into `NetworkUnavailable` per the Phase 1 plan: `NetworkFailureBadPassword` (legacy: `Remote_NoNetwork_BadPass`) and `CaptivePortal` (legacy: `Sync_CaptivePortal`). `IncorrectPin` (legacy: `Auth_InvalidPin`) is coalesced into `BadCredentials` per the plan. These are intentional regressions and will be revisited if user reports warrant it.

## Data pull modes

All `LoginActivity` flows now route through `LoginController`; the legacy `tryLocalLogin` / `startDataPull` / `dataPullCompleted` / `handlePullTaskResult` plumbing has been removed. The flow is selected by the `LoginRequest.dataPullMode`:

- **`NORMAL`** — manual, Connect, and PersonalID logins; OTA restore against the data server.
- **`CONSUMER_APP`** — consumer-app launches; bundled-CCZ local restore via `LocalReferencePullResponseFactory` with `SingleAppInstallation.LOCAL_RESTORE_REFERENCE` (`userId="unused"`).
- **`CCZ_DEMO`** — demo/practice menu (`LoginActivity.loginDemoUser()`) when a bundled `OfflineUserRestore` is present; local restore from the demo reference (`userId="demo_id"`). The non-bundled demo sub-case calls `DemoUserBuilder.build(...)` and then logs in with `NORMAL` (no sync).

## App seating

`AppSeater.seatIfNeeded(appId, listener)` seats an app into `CommCareApplication` off the UI thread, emitting the `Seating` phase, and returns a `Success`/`Failed` `SeatResult`. `SeatAppActivity` and the Connect launch path both seat through it rather than on the activity stack. A seat failure is not propagated as a result code; instead a corrupted app is left at `STATE_CORRUPTED` for `DispatchActivity` to detect downstream and route to recovery.

## Launching from Connect

`ConnectAppLauncher` opens a Connect opportunity's app without showing `LoginActivity`. If the worker is already signed into the target app (e.g. after a back-out that left the session alive) it routes straight to Home; otherwise it closes any open session, seats the app, resolves the worker's PersonalID-managed credentials, and runs `LoginController.performLogin`. It reports a `LaunchOutcome` (`Launched`, `AppSeatFailed`, `Retryable(error)`). Two failures are not modelled as outcomes: a missing Connect username is an invariant violation on this path and throws (`check`), and a token denial propagates as `LoginInvalidatedException` (via `GlobalErrorUtil.triggerGlobalError`) to the global `CommCareExceptionHandler`. Java callers use `start(lifecycleOwner, context, appId, isLearning, listener, callback)`.

`LaunchOutcomeRouter.dispatch(outcome, failedAttempts, actions)` maps each outcome to a `LaunchActions` call — a seam so the mapping is unit-tested without a fragment. It always dismisses progress first, then: launch Home; route a seat failure through `DispatchActivity` (which reaches recovery for a corrupted app); or, for a retryable failure, show a Retry/Cancel dialog whose Retry replays the launch. Once `failedAttempts` reaches `MAX_LAUNCH_ATTEMPTS` (3 — i.e. the worker has retried twice), a retryable failure instead shows a dismiss-only "still couldn't open the app" message rather than offering another retry. Every failure outcome is reported to `Logger` + Firebase.

`ConnectAppLaunchController` is the fragment-side orchestration for every Connect launch surface (`ConnectJobsListsFragment`, `ConnectDeliveryProgressFragment`, `ConnectLearningProgressFragment`, `ConnectJobIntroFragment`, `ConnectDownloadingFragment`): it shows a modal `CustomProgressDialog`, runs `ConnectAppLauncher` on the fragment's `viewLifecycleOwner` scope, and dispatches the outcome through `LaunchOutcomeRouter`. On success it starts Home on top of the surviving `ConnectActivity` — `FLAG_ACTIVITY_REORDER_TO_FRONT` to resume an existing Home when already signed into the target app, otherwise a fresh Home. The session stays alive on back-out, ending only on the app's own logout or session expiry. Once Home starts it invokes an optional `onLaunchSucceeded`; every surface except the opportunities list passes `BaseConnectFragment.popSelfOnceHidden()`, which pops the launch surface (or finishes the activity when it is the start destination) so back skips it and lands on the opportunities list. The dialog auto-dismisses with the fragment's view.

### Back navigation and logout

- **Back from Home** → the opportunities list (`ConnectActivity` stays in the stack).
- **Back from the opportunities list after a launch** → exits the app: `ConnectActivity` carries a per-instance flag set at launch (`markAppLaunchedFromConnect`), and a back press on the opportunities-list destination calls `finishAffinity()`. Without a launch (plain browsing) back returns to whatever opened Connect.
- **View Job Status** (`HomeScreenBaseActivity.userPressedOpportunityStatus`) opens the job-status page in a fresh `ConnectActivity` over the live Home; resuming from it finishes that activity, so the backstack stays stable.
- **Logout** (`userTriggeredLogout`) uses Home's standard `setResult(RESULT_OK) + finish`: a non-Connect app returns to the login screen; a Connect-launched app reveals the opportunities list with the session closed.

## Adding a new caller

1. Construct a `LoginController` with a `Context`.
2. Build a `LoginRequest`. For PersonalID-managed login (launched from Connect or manual) set `authSource = PersonalId`; the controller replaces `passwordOrPin` with the resolver's value. App navigation (e.g. returning to Connect on back-out) is driven by launch context at the call site, not by `authSource`.
3. Implement `LoginProgressListener` to render `LoginProgress(phase, percent?, message?)` events.
4. Handle the returned `LoginResult`. `Success` carries the resolved `appId`/`username`, routing fields (`loginMode`, `restoreSession`, `personalIdManagedLogin`), the `linkPassword` for a PersonalID link check, and `postLoginOutcome` (`redirectToConnectOpportunityInfo`, `needsPersonalIdLinkCheck`). Failures: handle the variants you care about and funnel the rest in an `else`/default branch.

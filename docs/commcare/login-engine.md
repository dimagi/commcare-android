# Login Engine

The `org.commcare.login` package implements the CommCare login pipeline without an Activity dependency. A caller supplies a `LoginRequest` plus a `LoginProgressSink`, awaits a `LoginResult`, and is responsible for any post-result navigation.

## Entry point

`LoginController.performLogin(request, sink)` is the single suspend entry point. Java callers should use `LoginController.start(lifecycleOwner, request, sink, callback)`, which launches the suspend call on the owner's `lifecycleScope` and delivers the result through a SAM callback.

The controller composes:

- `ConnectCredentialResolver` — resolves the Connect-managed password for an `(appId, username)` pair when `authSource = AutoFromConnect`.
- `KeyRecordOperations` — suspending wrapper around `ManageKeyRecordTask`. Yields a `KeyRecordOutcome` (`LocalLoginComplete`, `ReadyForSync`, or `Failed`).
- `SyncOperations` — suspending wrapper around `DataPullTask` for `DataPullMode.NORMAL`.
- `PostLoginSideEffects` — the deterministic post-success chain: `CrashUtil.registerUserData`, notification clear, `setConnectJobIdForAnalytics`, `PersonalIdManager.updateAppAccess`, `ConnectJobHelper.updateJobProgress`. Runs inside `withContext(NonCancellable)` so analytics still fire if the caller cancels after success.
- `OutcomeMapper` — pure functions mapping `HttpCalloutOutcomes` and `PullTaskResult` to `LoginError`.

`LoginResult` is `Success(loginMode, restoreSession, personalIdManagedLogin, connectManagedLogin, postLoginOutcome)` or `Failed(LoginError)`. `LoginError` variants: `BadCredentials`, `TokenDenied`, `NetworkUnavailable`, `AuthOverHttpBlocked`, `SyncFailed(reason: SyncFailureReason, message?)`.

## Progress events

`LoginProgress(phase, percent?, message?)` is emitted on the sink. Phases:

- `Seating` — reserved; emitted starting in Phase 2 by `AppSeater`. Not produced by Phase 1.
- `SigningIn` — `ManageKeyRecordTask` is running. `message` carries the localized status string from the underlying task.
- `Syncing` — `DataPullTask` is running. `percent` is populated when the task reports `PROGRESS_PROCESSING` or `PROGRESS_SERVER_PROCESSING`.

`LoginActivity` wires the sink to its existing `CustomProgressDialog`s (`TASK_KEY_EXCHANGE` for `SigningIn`, `DataPullTask.DATA_PULL_TASK_ID` for `Syncing`), transitioning between dialogs when the phase changes.

## Error mapping

`LoginActivity.onLoginError` translates `LoginError` to the existing `StockMessages` notifications used by the legacy `LoginActivity.handlePullTaskResult` switch:

| `LoginError` | `StockMessages` |
|---|---|
| `BadCredentials` | `Auth_BadCredentials` |
| `TokenDenied` | `TokenDenied` |
| `NetworkUnavailable` | `Remote_NoNetwork` |
| `AuthOverHttpBlocked` | `Auth_Over_HTTP` |
| `SyncFailed(BAD_DATA, msg)` | `Remote_BadRestore` (with `msg`) |
| `SyncFailed(BAD_DATA_REQUIRES_INTERVENTION, msg)` | `Remote_BadRestoreRequiresIntervention` (with `msg`) |
| `SyncFailed(BAD_RESPONSE)` | `Remote_BadRestore` |
| `SyncFailed(BAD_SSL_CERTIFICATE)` | `BadSslCertificate` (with `LAUNCH_DATE_SETTINGS` button) |
| `SyncFailed(STORAGE_FULL)` | `Storage_Full` |
| `SyncFailed(SERVER_ERROR)` | `Remote_ServerError` |
| `SyncFailed(RATE_LIMITED_SERVER_ERROR)` | `Remote_RateLimitedServerError` |
| `SyncFailed(ENCRYPTION_FAILURE, msg)` | `Encryption_Error` (with `msg`) |
| `SyncFailed(RECOVERY_FAILURE, msg)` | `Recovery_Error` (with `msg`) |
| `SyncFailed(ACTIONABLE_FAILURE, msg)` | Raw `NotificationMessage(msg)` |
| `SyncFailed(SESSION_EXPIRE)` | `Session_Expire` |
| `SyncFailed(CANCELLED)` | `Cancelled` |
| `SyncFailed(EMPTY_URL)` | `Empty_Url` |
| `SyncFailed(INSUFFICIENT_ROLE_PERMISSION)` | `Auth_InsufficientRolePermission` |
| `SyncFailed(UNKNOWN, msg?)` | `Restore_Unknown` (with `msg` if present) |

Two `HttpCalloutOutcomes` values are coalesced into `NetworkUnavailable` per the Phase 1 plan: `NetworkFailureBadPassword` (legacy: `Remote_NoNetwork_BadPass`) and `CaptivePortal` (legacy: `Sync_CaptivePortal`). `IncorrectPin` (legacy: `Auth_InvalidPin`) is coalesced into `BadCredentials` per the plan. These are intentional regressions and will be revisited if user reports warrant it.

## What stays on the legacy path

Two flows still use the original `tryLocalLogin` / `startDataPull` / `dataPullCompleted` / `handlePullTaskResult` plumbing on `LoginActivity`:

- **Consumer-app launches** (`DataPullMode.CONSUMER_APP`) — bundled-CCZ restore via `LocalReferencePullResponseFactory` with `SingleAppInstallation.LOCAL_RESTORE_REFERENCE`. `SyncOperations` only handles `NORMAL`.
- **Demo-user practice menu** — `LoginActivity.loginDemoUser()` drives `tryLocalLogin` directly with the bundled demo CCZ.

## Adding a new caller

1. Construct a `LoginController` with a `Context`.
2. Build a `LoginRequest`. For Connect-managed launches, set `authSource = AutoFromConnect`; the controller replaces `passwordOrPin` with the resolver's value.
3. Implement `LoginProgressSink` to render `LoginProgress(phase, percent?, message?)` events.
4. Handle the returned `LoginResult`. `Success` carries routing fields (`loginMode`, `restoreSession`, `personalIdManagedLogin`, `connectManagedLogin`) plus `postLoginOutcome.redirectToConnectOpportunityInfo`. Failures: switch on `Failed.error`.

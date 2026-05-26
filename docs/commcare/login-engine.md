# Login Engine

The `org.commcare.login` package implements the CommCare login pipeline without an Activity dependency. A caller supplies a `LoginRequest` plus a `LoginProgressSink`, awaits a `LoginResult`, and is responsible for any post-result navigation.

## Entry point

`LoginController.performLogin(activity, request, sink)` is the single suspend entry point. Java callers should use `LoginController.start(activity, request, sink, callback)`, which launches the suspend call on the activity's `lifecycleScope` and delivers the result through a SAM callback.

The controller composes:

- `ConnectCredentialResolver` — resolves the Connect-managed password for an `(appId, username)` pair when `authSource = AutoFromConnect`.
- `KeyRecordOperations` — suspending wrapper around `ManageKeyRecordTask`. Yields a `KeyRecordOutcome` (`LocalLoginComplete`, `ReadyForSync`, or `Failed`).
- `SyncOperations` — suspending wrapper around `DataPullTask` for `DataPullMode.NORMAL`.
- `DemoLoginPath` — short-circuit for `LoginMode.Demo`; pulls the bundled CCZ restore via `LocalReferencePullResponseFactory`.
- `PostLoginSideEffects` — the deterministic post-success chain: `CrashUtil.registerUserData`, notification clear, `setConnectJobIdForAnalytics`, `PersonalIdManager.updateAppAccess`, `ConnectJobHelper.updateJobProgress`. Runs inside `withContext(NonCancellable)` so analytics still fire if the caller cancels after success.
- `OutcomeMapper` — pure functions mapping `HttpCalloutOutcomes` and `PullTaskResult` to `LoginError`.

`LoginResult` is `Success` (carries routing fields plus a `PostLoginOutcome`) or `Failed(LoginError)`. `LoginError` variants: `BadCredentials`, `TokenDenied`, `NetworkUnavailable`, `AuthOverHttpBlocked`, `SyncFailed(reason, message?)`.

## What stays on the legacy path

Two flows still use the original `tryLocalLogin` / `startDataPull` / `dataPullCompleted` / `handlePullTaskResult` plumbing on `LoginActivity`:

- **Consumer-app launches** (`DataPullMode.CONSUMER_APP`) — bundled-CCZ restore via `LocalReferencePullResponseFactory` with `SingleAppInstallation.LOCAL_RESTORE_REFERENCE`. `SyncOperations` only handles `NORMAL`.
- **Demo-user practice menu** — `LoginActivity.loginDemoUser()` drives `tryLocalLogin` directly with the bundled demo CCZ. `DemoLoginPath` is for the separate `AuthSource.Demo` case.

## Adding a new caller

1. Construct a `LoginController` with a `Context`.
2. Build a `LoginRequest`. For Connect-managed launches, set `authSource = AutoFromConnect`; the controller replaces `passwordOrPin` with the resolver's value.
3. Implement `LoginProgressSink` to render `LoginProgress(phase, percent?, message?)` events. `LoginActivity` currently no-ops the sink.
4. Handle the returned `LoginResult`. `Success` carries everything the existing `setResult(...) + finish()` intent extras need; switch on `Failed.error`.

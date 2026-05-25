# Headless Login Engine

The `org.commcare.login` package contains a non-UI implementation of the CommCare login pipeline. A caller supplies a `LoginRequest` and a `LoginProgressSink`, awaits a `LoginResult`, and is responsible for the post-result navigation. The engine itself holds no Activity reference, so future non-Activity callers (for example, the Connect silent-launch fragment planned for Phase 3) can drive the same pipeline without going through `LoginActivity`.

## Package contents

| File | Responsibility |
|---|---|
| `LoginRequest.kt` | Input data class — `appId`, `username`, `passwordOrPin`, `credentialType` (existing `LoginMode`), `authSource` (`Manual` / `AutoFromConnect` / `MdmManaged` / `Demo`), `restoreSession`, `pullMode`, `triggerMultipleUsersWarning`, `blockRemoteKeyManagement`. |
| `LoginProgress.kt` | `LoginProgress` (phase / percent / message), `LoginPhase` enum (`Seating`, `SigningIn`, `Syncing`), and the SAM `LoginProgressSink`. |
| `LoginResult.kt` | Sealed `LoginResult` with `Success` and `Failed`. `Success` carries the routing-relevant fields plus a `PostLoginOutcome`. |
| `LoginError.kt` | Sealed failure variants — `BadCredentials`, `TokenDenied`, `NetworkUnavailable`, `AuthOverHttpBlocked`, `SyncFailed(reason, message?)`. |
| `OutcomeMapper.kt` | Pure functions mapping `HttpCalloutOutcomes` (from `ManageKeyRecordTask`) and `PullTaskResult` (from `DataPullTask`) to `LoginError`. Mapping is taken verbatim from the parent investigation plan. |
| `ConnectCredentialResolver.kt` | Resolves the Connect-managed password for an `(appId, username)` pair. Ported from `ConnectAppUtils.getPasswordOverride`, which now delegates to it. |
| `KeyRecordOperations.kt` | Suspending wrapper around `ManageKeyRecordTask`. Captures `startDataPull` / `dataPullCompleted` / `keysDoneOther` on a no-op `DataPullController` receiver and resumes the caller with a `KeyRecordOutcome`. |
| `SyncOperations.kt` | Suspending wrapper around `DataPullTask` for the OTA-restore path (`DataPullMode.NORMAL` only). Mirrors `FormAndDataSyncer.syncData`'s construction. |
| `DemoLoginPath.kt` | Short-circuit for demo logins. Sets the `LocalReferencePullResponseFactory` payload and runs `DataPullTask` against the CCZ-bundled `OfflineUserRestore`. |
| `PostLoginSideEffects.kt` | The deterministic post-success chain: `CrashUtil.registerUserData`, notification clear, `setConnectJobIdForAnalytics`, `PersonalIdManager.updateAppAccess`, `ConnectJobHelper.updateJobProgress`. Returns `PostLoginOutcome` carrying `redirectToConnectOpportunityInfo`. |
| `LoginController.kt` | The single `performLogin(activity, request, sink)` entry point. Composes everything above. Post-success side-effects run inside `withContext(NonCancellable)` so analytics still fire if the caller cancels after success. |
| `LoginCoordinator.kt` | Java-friendly bridge that launches `performLogin` on the activity's `lifecycleScope` and delivers the result through a `ResultCallback` SAM. |

## Current caller

[`LoginActivity`](https://github.com/dimagi/commcare-android/blob/master/app/src/org/commcare/activities/LoginActivity.java) is the only caller as of Phase 1. Its `doLogin(...)` method builds a `LoginRequest`, calls `LoginCoordinator.start(...)`, and translates the returned `LoginResult` back into the existing `setResult(RESULT_OK, intent) + finish()` contract that `DispatchActivity.onActivityResult(LOGIN_USER)` consumes.

The `personalIdManager.checkPersonalIdLink(...)` UI-prompt branch (fired when PersonalID is logged in but no Connect job is associated with the seated app) stays in `LoginActivity` because it can prompt the user. The deterministic post-success work it used to wrap moved into `PostLoginSideEffects`.

## What stays on the legacy path

Two paths still use the original `tryLocalLogin` / `startDataPull` / `dataPullCompleted` / `handlePullTaskResult` plumbing:

- **Consumer-app launches.** `DataPullMode.CONSUMER_APP` uses `LocalReferencePullResponseFactory` with `SingleAppInstallation.LOCAL_RESTORE_REFERENCE`. `SyncOperations` only handles `NORMAL`, so consumer apps continue through `LoginActivity.localLoginOrPullAndLogin`.
- **Demo-user practice menu.** `LoginActivity.loginDemoUser()` invokes the 7-arg `tryLocalLogin` directly and relies on `DataPullTask` callbacks landing back on the activity. Note that `DemoLoginPath` is for a different case — the demo `AuthSource` used by `LoginActivity` when launching the standalone demo CCZ.

These will be revisited if and when the engine grows support for those `DataPullMode` variants.

## Phase context

This package is the output of **Phase 1** of [CCCT-2164](https://dimagi.atlassian.net/browse/CCCT-2164) — *Decouple Login From `LoginActivity` for Connect App Launches*. The full multi-phase investigation lives at [`docs/superpowers/plans/2026-05-25-ccct-2437-phase-1-headless-login-engine.md`](https://github.com/dimagi/commcare-android/blob/master/docs/superpowers/plans/2026-05-25-ccct-2437-phase-1-headless-login-engine.md) (Phase 1 plan) and references the parent CCCT-2164 plan that lays out Phases 2–5. Phase 2 introduces `PostLoginRouter` + `AppSeater`. Phase 3 wires `ConnectAppLauncher`, the headless Connect silent-launch caller this engine was extracted to support.

## Adding a new caller

A new caller needs:

1. A `Context` (for the `LoginController` constructor) and a hosting `CommCareActivity<*>` (passed to `performLogin` so `PostLoginSideEffects` can reach `PersonalIdManager.updateAppAccess` and `ConnectJobHelper.updateJobProgress`).
2. A `LoginRequest`. For Connect-managed launches, set `authSource = AutoFromConnect` and supply any `passwordOrPin` — the controller will replace it with the resolver's value.
3. A `LoginProgressSink`. Render the `LoginProgress(phase, percent, message)` events however the caller's UI prefers (today `LoginActivity` no-ops the sink; Phase 3 will surface a `CustomProgressDialog`).
4. A handler for the returned `LoginResult`. `Success` carries everything the existing `setResult(...) + finish()` intent extras need; `Failed.error` is a sealed `LoginError` to switch on.

Java callers should use `LoginCoordinator.start(activity, request, sink, callback)` rather than calling `performLogin` directly — the coordinator handles the suspend bridge and lifecycle scoping.

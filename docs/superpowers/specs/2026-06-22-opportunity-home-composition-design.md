# Opportunity Home: Activity Composition Design

**Date:** 2026-06-22
**Status:** Draft — pending review

## Summary

Add a new `OpportunityHomeActivity` that serves as the per-opportunity landing page for Connect users. The activity must support five operating situations within one lifecycle:

1. New opportunity, not accepted yet (no app installed or required).
2. Learning state, learn app not installed / seated / logged in.
3. Learning state, learn app ready for use.
4. Delivery state, deliver app not installed / seated / logged in.
5. Delivery state, deliver app ready for use.

The defining requirement is that the activity loads with **no app seated and no session**, and can later seat an app and establish a session **without leaving the activity**, gaining session-dependent capabilities (sync, form entry, app updates, language switching) as that context becomes available — and losing them again on logout or session expiration.

This spec covers only the activity's composition, lifecycle, and the refactor of two existing base classes that today bake the "session must exist" assumption into inheritance. State modeling, UI, entry points/routing, inline silent login mechanics, and rollout are out of scope and handled in parallel designs.

## Problem

`SessionAwareCommCareActivity.onCreate()` calls `SessionAwareHelper.onCreateHelper()`, which calls `CommCareApplication.instance().getSession()` and redirects to `LoginActivity` on `SessionUnavailableException`. This is the session gate. `SyncCapableCommCareActivity` and `HomeScreenBaseActivity` build on that gate and assume an app is seated for sync, form entry, app-update prompts, and post-login launch checks.

`OpportunityHomeActivity` cannot extend `SessionAwareCommCareActivity` — situation (1) above has no session. But it must still perform the work currently inherited from `SyncCapableCommCareActivity` and `HomeScreenBaseActivity` once a session does exist, and it must transition into and out of that capable state without recreating the activity.

The fix is to move the session-dependent behavior out of inheritance and into a small set of composable delegates that any activity — including the existing chain — can use, and that can be bound and unbound to a live session at runtime.

## Non-goals

- Modifying `SessionAwareCommCareActivity`. Every other session-gated activity in the app keeps the existing redirect-to-login behavior.
- Replacing `StandardHomeActivity` for non-Connect users.
- Designing the state model that picks which UI to show inside Opportunity Home (handled separately).
- Designing entry points, routing through `DispatchActivity`, or persistence of "last accessed opportunity".
- Designing the inline silent app-login flow (in-flight work). This spec assumes that capability is available and exposes a session to the activity when it completes.
- Implementing `WithUIController` on the new activity.

## Approach

`OpportunityHomeActivity` extends `BaseDrawerActivity` — the layer immediately below the session gate that still provides the drawer and the underlying `CommCareActivity` capabilities.

Five behaviors are extracted from the existing inheritance chain into delegates that the activity composes:

| Delegate | Source today | Responsibilities |
|---|---|---|
| `SessionExpirationDelegate` | `SessionAwareHelper.onResumeHelper`, `onActivityResultHelper` | On resume / activity-result, check session expiration. Exposes a listener interface that the host activity registers; reports session-lost events via that listener rather than redirecting to login. |
| `SyncDelegate` | `SyncCapableCommCareActivity` | Owns `FormAndDataSyncer`, sync state, and the `PullTaskResultReceiver` implementation. Exposes `sendFormsOrSync()` and related entry points. The host activity remains the `CommCareTaskConnector` (see below). |
| `AppUpdateDelegate` | `HomeScreenBaseActivity` (`AppUpdateController` and drift checks) | App update prompts and drift warnings. **Session-independent** (see below): constructed once and registered on the host lifecycle like `CrashRecoveryDelegate`; only the surfacing of prompts is gated on a session existing. |
| `SessionLaunchDelegate` | `HomeScreenBaseActivity.doLoginLaunchChecksInOrder`, `SessionNavigator` usage, `onSaveInstanceState`/`loadInstanceState` | Form restoration, "Start" → form entry navigation, post-login launch checks, and form-result handling. Must preserve the ordering and early-return semantics of `doLoginLaunchChecksInOrder` (see risks). Also owns the launch/nav instance-state keys — `WAS_EXTERNAL_KEY` (`wasExternal`), `EXTRA_CONSUMED_KEY` (`loginExtraWasConsumed`), and `KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC` (`pendingEndpointNavigationAfterSync`) — persisting them across recreation (see note below on save/restore vs. attach). |
| `CrashRecoveryDelegate` | `HomeScreenBaseActivity` crash-data registration | Registers app/crash data (`CrashUtil.registerAppData()`) on the host lifecycle. Active regardless of session. |

Each delegate is a Kotlin class implementing `DefaultLifecycleObserver`, registered on the host's `lifecycle` so it receives `onResume` / `onPause` / `onDestroy` directly. The host activity forwards `onActivityResult` and intent handling to the delegates that need them.

Session-dependent delegates — `SessionExpirationDelegate`, `SyncDelegate`, and `SessionLaunchDelegate` — expose two explicit methods:

```
attachSession(session: SeatedAppSession)
detachSession()
```

When attached, the delegate has a live session and operates as the existing chain does today. When not attached, every public entry point is a clean no-op (callers receive a documented "no session" result; nothing throws `SessionUnavailableException`).

This requires the delegates to take their session as a parameter rather than reading `CommCareApplication.instance().getCurrentSession()` ambiently. Today's code reads the ambient global; the refactor threads it through `attachSession` so "no session" is a representable, safe state rather than an exception.

**Instance-state save/restore is not gated on attach.** `SessionLaunchDelegate` is session-dependent for its *behavioral* entry points (the no-op-when-detached rule above), but the three launch/nav keys it owns (`wasExternal`, `loginExtraWasConsumed`, `pendingEndpointNavigationAfterSync`) describe how the activity was launched and what navigation is pending — state that exists *before* a session is attached and must survive recreation that happens while detached. The host therefore forwards `onSaveInstanceState(outState)` and the restore in `onCreate`/`loadInstanceState` to `SessionLaunchDelegate` unconditionally, independent of attach state. These two paths (save/restore vs. the session-gated entry points) are deliberately separate within the delegate; only the latter no-ops when detached.

`CrashRecoveryDelegate` and `AppUpdateDelegate` do not implement `attachSession`/`detachSession`. `CrashRecoveryDelegate` is session-independent by nature. `AppUpdateDelegate` is treated as session-independent because the underlying `AppUpdateController` cannot be safely reconstructed per session: `AppUpdateControllerFactory.create(...)` needs only a `Context` and a callback (not a session), and `register()` attaches an `InstallStateUpdatedListener` to the Google Play Core `AppUpdateManager` and kicks off an async info fetch. Reconstructing on each `attachSession` would leak listeners (duplicate callbacks), orphan any in-progress download from the Play Store state machine, and re-fetch update info needlessly. App-binary updates are not opportunity- or seated-app-scoped, so the delegate is constructed once on the host lifecycle (register on `onResume`, unregister on `onDestroy`); only the *surfacing* of an update prompt is gated on a session existing, re-evaluating `shouldShowInAppUpdate()` at prompt time rather than at construction.

### Refactor of existing base classes

To avoid two implementations of the same behaviors, `SyncCapableCommCareActivity` and `HomeScreenBaseActivity` are rebased onto the new delegates. Their public API stays the same; internally they instantiate the delegates, register them with the activity's lifecycle, and forward calls. Because those base classes still extend `SessionAwareCommCareActivity`, they call `attachSession(...)` once in `onCreateSessionSafe` and never detach (they cannot reach a session-less state). `StandardHomeActivity` is unchanged externally.

`SessionAwareCommCareActivity` itself is not modified. The "redirect to login on missing session" behavior continues to apply to every other activity that extends it.

### `OpportunityHomeActivity`

```
class OpportunityHomeActivity : BaseDrawerActivity<OpportunityHomeActivity>() {
    private val sessionExpirationDelegate = SessionExpirationDelegate(...)
    private val syncDelegate              = SyncDelegate(...)
    private val appUpdateDelegate         = AppUpdateDelegate(...)
    private val sessionLaunchDelegate     = SessionLaunchDelegate(...)
    private val crashRecoveryDelegate     = CrashRecoveryDelegate(...)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(sessionExpirationDelegate)
        lifecycle.addObserver(syncDelegate)
        lifecycle.addObserver(appUpdateDelegate)
        lifecycle.addObserver(sessionLaunchDelegate)
        lifecycle.addObserver(crashRecoveryDelegate)
        // No session lookup here.
        sessionLaunchDelegate.loadInstanceState(savedInstanceState)  // unconditional; not gated on attach
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sessionLaunchDelegate.onSaveInstanceState(outState)          // unconditional; not gated on attach
    }

    fun onSessionAvailable(session: SeatedAppSession) {
        sessionExpirationDelegate.attachSession(session)
        syncDelegate.attachSession(session)
        sessionLaunchDelegate.attachSession(session)
        // appUpdateDelegate is session-independent; it self-gates prompt surfacing.
    }

    fun onSessionLost() {
        sessionExpirationDelegate.detachSession()
        syncDelegate.detachSession()
        sessionLaunchDelegate.detachSession()
    }

    override fun onActivityResult(...) {
        sessionExpirationDelegate.onActivityResult(...)
        syncDelegate.onActivityResult(...)
        sessionLaunchDelegate.onActivityResult(...)
        super.onActivityResult(...)
    }
}
```

`onSessionAvailable(...)` is invoked from two places inside the activity: the completion path for inline silent login (driving the situation 2 → 3 and 4 → 5 transitions), and an `onResume` check that calls it whenever a session exists but no delegate is currently attached. `onSessionLost()` is invoked from two distinct mechanisms, which today reach the chain through different paths:

- **Manual logout** — `userTriggeredLogout()` → `CommCareApplication.closeUserSession()` (no broadcast).
- **Automatic expiration** — `CommCareApplication.expireUserSession()`, which fires the `USER_SESSION_EXPIRED` broadcast; today `SessionRegistrationHelper`'s receiver responds by calling `redirectToLogin()` + `finish()`, and `SessionAwareHelper` does the same on a caught `SessionUnavailableException`.

`SessionExpirationDelegate` must intercept both and route them to the host's session-lost listener **instead of** the default redirect-and-finish — that default behavior is what every other `SessionAwareCommCareActivity` keeps, but it is exactly what `OpportunityHomeActivity` must not do. Crucially, the activity itself does **not** finish or redirect on session loss — it simply detaches and the underlying state-resolution logic (out of scope) renders the appropriate "needs login" surface, which re-triggers inline silent login.

The activity does not implement `WithUIController`. UI is delegated to fragments under the drawer host.

### Capabilities the activity gains when attached

All except app updates are routed through delegates and gated on `attachSession(...)` having been called:

- **Sync** — `SyncDelegate.sendFormsOrSync()`. `SyncDelegate` *is* the `PullTaskResultReceiver`; the host activity stays the `CommCareTaskConnector` passed to `DataPullTask.connect(...)` and its `getReceiver()` returns the delegate (see risks). This keeps the in-activity blocking/spinner UI while moving the result-handling implementation into the delegate.
- **Form entry** — `SessionLaunchDelegate.startForm(...)`; result handling fans out to `SyncDelegate` (post-form sync prompt) and `SessionExpirationDelegate` (mid-form expiration check), matching the current `HomeScreenBaseActivity` pipeline.
- **App updates** — `AppUpdateDelegate` is always registered; it self-gates whether to surface a prompt based on whether a session currently exists (re-evaluating `shouldShowInAppUpdate()`), rather than being attached/detached.
- **Language switching** — app-based, therefore session-gated. The activity exposes language switching only while attached; routed through whichever existing component owns the change (verified during implementation).

## Risks and mitigations

- **Behavior drift between the chain and the new activity.** Mitigated by making the rebased base classes use the same delegates the new activity composes — one source of truth. Forwarding-only changes in the base classes minimize the chance of semantic divergence.
- **Delegates currently read `CommCareApplication.instance().getCurrentSession()` ambiently.** Required change: thread the session explicitly through `attachSession(...)`. Audit each migrated callsite for ambient reads and replace.
- **`PullTaskResultReceiver` and similar Java interfaces are implemented by the current base classes.** Resolved: the task framework (`DataPullTask<R>` → `CommCareTask<…, R>` → `CommCareTaskConnector<R>`) places no `Activity` bound on the receiver type — it calls `receiver.handlePullTaskResult(...)` with no cast, and `SyncOperations.kt` already runs sync with a non-Activity receiver via `HeadlessTaskConnector`. The only `Activity` coupling is `FormAndDataSyncer.syncData(<I extends CommCareActivity & PullTaskResultReceiver>)`, where the bound exists because the argument doubles as the **connector** passed to `.connect(...)`. So the delegate owns the `PullTaskResultReceiver` implementation while the **host activity remains the `CommCareTaskConnector`** (its `getReceiver()` returns the delegate). This preserves the activity's task-transition/blocking UI hooks, which a `HeadlessTaskConnector` would no-op away.
- **Existing Robolectric tests may assert against the base classes' internal state.** Verify during implementation that tests against `HomeScreenBaseActivity` / `SyncCapableCommCareActivity` still pass with the internal forwarding.

## Open questions for implementation

1. **`PullTaskResultReceiver` ownership — resolved.** The framework imposes no `Activity` bound on the receiver. `SyncDelegate` owns the `PullTaskResultReceiver` implementation; the host activity stays the `CommCareTaskConnector` and returns the delegate from `getReceiver()`. See Approach and Risks.
2. **Session-loss UX surface — resolved (no existing dialog to preserve).** Session loss today is silent: both `SessionAwareHelper` (on caught `SessionUnavailableException`) and the `USER_SESSION_EXPIRED` broadcast receiver just call `redirectToLogin()` + `finish()`; the only special case is `FormEntryActivity` returning `WAS_INTERRUPTED`. There is no expiration dialog. `OpportunityHomeActivity` therefore defines new behavior rather than preserving any — and `SessionExpirationDelegate` must suppress the default redirect-and-finish in favor of the host's session-lost listener. Any dialog before the fragment swap is a new UX decision; defer to product/UX or the state-design spec.
3. **`AppUpdateController` lifecycle — resolved (do not rebind per session).** Reconstructing the controller on each `attachSession` would leak Play Core listeners, orphan in-progress downloads, and re-fetch needlessly. `AppUpdateDelegate` is therefore session-independent: constructed once on the host lifecycle, self-gating only prompt surfacing on a session existing. See Approach.

Remaining verify-during-implementation items:

4. **Preserve `doLoginLaunchChecksInOrder` semantics.** This method is a strict 9-step ordered pipeline (demo-mode early return → update-info form → form restoration → session restoration → post-update sync → pending FCM sync → update prompt → PIN check → drift check). When `SessionLaunchDelegate` and `AppUpdateDelegate` absorb pieces of it, the ordering and early-return behavior must be preserved, or demo users could start receiving prompts they currently skip.
5. **Demo mode and other StandardHome-specific concerns** are non-goals here, but they sit on the same chain. Demo handling is just inline `isDemoUser()` checks (the `doLoginLaunchChecksInOrder` early return and `StandardHomeActivity.onPrepareOptionsMenu` menu gating), not a separate subsystem. Verify the rebase doesn't subtly change those semantics in `StandardHomeActivity`. `WithUIController` is implemented only on `StandardHomeActivity`, so the rebase does not touch it.

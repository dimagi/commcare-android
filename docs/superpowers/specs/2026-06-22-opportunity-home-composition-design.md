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
| `SyncDelegate` | `SyncCapableCommCareActivity` | Owns `FormAndDataSyncer`, sync state, and `PullTaskResultReceiver` plumbing. Exposes `sendFormsOrSync()` and related entry points. |
| `AppUpdateDelegate` | `HomeScreenBaseActivity` (`AppUpdateController` and drift checks) | App update prompts and drift warnings. |
| `SessionLaunchDelegate` | `HomeScreenBaseActivity.doLoginLaunchChecksInOrder`, `SessionNavigator` usage | Form restoration, "Start" → form entry navigation, post-login launch checks, and form-result handling. |
| `CrashRecoveryDelegate` | `HomeScreenBaseActivity` instance-state and crash-data registration | Persists instance state across recreation. Active regardless of session. |

Each delegate is a Kotlin class implementing `DefaultLifecycleObserver`, registered on the host's `lifecycle` so it receives `onResume` / `onPause` / `onDestroy` directly. The host activity forwards `onActivityResult` and intent handling to the delegates that need them.

Session-dependent delegates (all except `CrashRecoveryDelegate`) expose two explicit methods:

```
attachSession(session: SeatedAppSession)
detachSession()
```

When attached, the delegate has a live session and operates as the existing chain does today. When not attached, every public entry point is a clean no-op (callers receive a documented "no session" result; nothing throws `SessionUnavailableException`).

This requires the delegates to take their session as a parameter rather than reading `CommCareApplication.instance().getCurrentSession()` ambiently. Today's code reads the ambient global; the refactor threads it through `attachSession` so "no session" is a representable, safe state rather than an exception.

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
    }

    fun onSessionAvailable(session: SeatedAppSession) {
        sessionExpirationDelegate.attachSession(session)
        syncDelegate.attachSession(session)
        appUpdateDelegate.attachSession(session)
        sessionLaunchDelegate.attachSession(session)
    }

    fun onSessionLost() {
        sessionExpirationDelegate.detachSession()
        syncDelegate.detachSession()
        appUpdateDelegate.detachSession()
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

`onSessionAvailable(...)` is invoked from two places inside the activity: the completion path for inline silent login (driving the situation 2 → 3 and 4 → 5 transitions), and an `onResume` check that calls it whenever a session exists but no delegate is currently attached. `onSessionLost()` is invoked from a session-lost callback that the activity registers with `SessionExpirationDelegate` (mechanism: the delegate exposes a listener interface), and from the explicit logout path. Crucially, the activity itself does **not** finish or redirect on session loss — it simply detaches and the underlying state-resolution logic (out of scope) renders the appropriate "needs login" surface, which re-triggers inline silent login.

The activity does not implement `WithUIController`. UI is delegated to fragments under the drawer host.

### Capabilities the activity gains when attached

All routed through delegates and gated on `attachSession(...)` having been called:

- **Sync** — `SyncDelegate.sendFormsOrSync()`; result handling via `PullTaskResultReceiver` callbacks forwarded back through the delegate.
- **Form entry** — `SessionLaunchDelegate.startForm(...)`; result handling fans out to `SyncDelegate` (post-form sync prompt) and `SessionExpirationDelegate` (mid-form expiration check), matching the current `HomeScreenBaseActivity` pipeline.
- **App updates** — `AppUpdateDelegate` raises prompts when attached; no-op otherwise.
- **Language switching** — app-based, therefore session-gated. The activity exposes language switching only while attached; routed through whichever existing component owns the change (verified during implementation).

## Risks and mitigations

- **Behavior drift between the chain and the new activity.** Mitigated by making the rebased base classes use the same delegates the new activity composes — one source of truth. Forwarding-only changes in the base classes minimize the chance of semantic divergence.
- **Delegates currently read `CommCareApplication.instance().getCurrentSession()` ambiently.** Required change: thread the session explicitly through `attachSession(...)`. Audit each migrated callsite for ambient reads and replace.
- **`PullTaskResultReceiver` and similar Java interfaces are implemented by the current base classes.** The refactor moves implementations into the delegates; the rebased base classes (and `OpportunityHomeActivity`) register the delegate as the receiver, or forward through the activity if framework code requires the activity to implement the interface directly.
- **Existing Robolectric tests may assert against the base classes' internal state.** Verify during implementation that tests against `HomeScreenBaseActivity` / `SyncCapableCommCareActivity` still pass with the internal forwarding.

## Open questions for implementation

1. **`PullTaskResultReceiver` ownership.** Does framework code (form entry, sync tasks) require the *activity* to implement the receiver interface, or is registering a delegate instance acceptable? If the former, the activity implements it and forwards to `SyncDelegate`.
2. **Session-loss UX surface.** When `SessionExpirationDelegate` reports a session-lost event mid-activity, does the existing expiration dialog still appear before the state-resolution layer swaps the fragment? (UX detail; defer to product/UX or to the state-design spec.)
3. **`AppUpdateController` lifecycle.** Today the controller is created in `onCreateSessionSafe` of `HomeScreenBaseActivity` with session context. The delegate must re-construct or rebind the controller on each `attachSession`; confirm the controller tolerates rebinding rather than requiring a single instantiation per process.
4. **Demo mode and other StandardHome-specific concerns** are non-goals here, but they sit on the same chain. Verify the rebase doesn't subtly change their semantics in `StandardHomeActivity`.

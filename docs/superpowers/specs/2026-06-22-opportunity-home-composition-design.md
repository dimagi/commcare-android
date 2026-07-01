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

Five behaviors are extracted from the existing inheritance chain into delegates, and a single `HomeActivityCoordinator` owns those delegates and exposes the home screen's capabilities as host-agnostic actions (see [`HomeActivityCoordinator`](#homeactivitycoordinator) below). A host activity holds one coordinator rather than five delegates directly. The five extracted delegates are:

| Delegate | Source today | Responsibilities |
|---|---|---|
| `SessionExpirationDelegate` | `SessionAwareHelper.onResumeHelper`, `onActivityResultHelper` | On resume / activity-result, check session expiration. Exposes a listener interface that the host activity registers; reports session-lost events via that listener rather than redirecting to login. |
| `SyncDelegate` | `SyncCapableCommCareActivity` | Owns `FormAndDataSyncer`, sync state, and the `PullTaskResultReceiver` implementation. Exposes `sendFormsOrSync()` and related entry points. The host activity remains the `CommCareTaskConnector` (see below). |
| `AppUpdateDelegate` | `HomeScreenBaseActivity` (`AppUpdateController` and drift checks) | App update prompts and drift warnings. **Session-independent** (see below): constructed once and registered on the host lifecycle like `CrashRecoveryDelegate`; only the surfacing of prompts is gated on a session existing. |
| `SessionLaunchDelegate` | `HomeScreenBaseActivity.doLoginLaunchChecksInOrder`, `SessionNavigator` usage, `onSaveInstanceState`/`loadInstanceState` | Form restoration, "Start" → form entry navigation, the step bodies it contributes to the launch pipeline (update-info form, form restoration, session restoration), and form-result handling. The *ordering* and early-return semantics of `doLoginLaunchChecksInOrder` are owned by the coordinator's `runLaunchChecks` (see [`HomeActivityCoordinator`](#homeactivitycoordinator)), not by this delegate. Also owns the launch/nav instance-state keys — `WAS_EXTERNAL_KEY` (`wasExternal`), `EXTRA_CONSUMED_KEY` (`loginExtraWasConsumed`), and `KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC` (`pendingEndpointNavigationAfterSync`) — persisting them across recreation (see note below on save/restore vs. attach). |
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

### `HomeActivityCoordinator`

The delegates hold the session-dependent *behavior*, but two further concerns are shared across every home activity and would otherwise be duplicated in each host: wiring the delegates to the lifecycle, and deciding what a given home capability *does* independently of how it is surfaced. `HomeActivityCoordinator` owns both.

**Role 1 — composition root and lifecycle/session coordinator.** The coordinator constructs the five delegates, registers them as lifecycle observers, and fans out the cross-cutting host callbacks to them: `onActivityResult`, `onSaveInstanceState`/instance-state restore, and the session transitions. It exposes `attachSession(session)` / `detachSession()` that fan out to the session-dependent delegates (`SessionExpirationDelegate`, `SyncDelegate`, `SessionLaunchDelegate`); `CrashRecoveryDelegate` and `AppUpdateDelegate` are session-independent and simply ride the lifecycle. This replaces the per-host `onSessionAvailable`/`onSessionLost`/`onActivityResult` forwarding that each home activity would otherwise hand-write.

**Role 2 — action facade.** The coordinator exposes the home screen's capabilities as host-agnostic **actions** — `sync()`, `viewSavedForms()`, `changeLanguage()`, `openSettings()`, `openAdvanced()`, `showAbout()`, `setPin()`, `updateApp()`, `updateCommCare()` — each either delegating to a delegate (`sync()` → `SyncDelegate`, `updateApp()`/`updateCommCare()` → `AppUpdateDelegate`) or performing a simple launch/dialog. Alongside each action it exposes an **availability query** (`canViewSavedForms()`, etc.). Crucially, the coordinator does **not** know whether an action is surfaced as an overflow-menu item, an on-screen button, or a drawer entry — that binding is the host's job. This is what lets one home screen route a capability through the menu while another promotes it to the main UI, with no duplicated action logic. See [Capabilities](#capabilities-the-activity-exposes-when-attached) below for the full action list and gating.

**Role 3 — post-login launch pipeline (single owner of the 9-step order).** The coordinator owns `runLaunchChecks(session): Boolean`, the one place the `doLoginLaunchChecksInOrder` ordering lives. Both hosts call it (`StandardHomeActivity` via the rebased base, `OpportunityHomeActivity` directly); neither re-expresses the sequence, and no delegate owns it. The delegates contribute the *bodies* of individual steps — `SessionLaunchDelegate` (update-info form, form restoration, session restoration), `SyncDelegate` (post-update sync, pending FCM sync), `AppUpdateDelegate` (update prompt) — while the coordinator hard-codes the order and the early-return semantics as a literal sequence reviewable line-for-line against today's `HomeScreenBaseActivity.doLoginLaunchChecksInOrder`:

```kotlin
fun runLaunchChecks(session: SeatedAppSession): Boolean {
    if (gating.isDemo()) { showDemoModeWarning(); return false }   // step 1: halt, unclaimed
    if (sessionLaunch.showUpdateInfoForm(session)) return true     // step 2
    if (sessionLaunch.tryRestoringFormFromExpiration(session)) return true  // step 3
    if (sessionLaunch.tryRestoringSession(session)) return true    // step 4
    if (sync.runPostUpdateSyncIfNeeded()) return true              // step 5
    if (sync.runPendingFcmSyncIfNeeded(session)) return true       // step 6
    if (appUpdate.promptForUpdateIfNeeded()) return true           // step 7
    pin.checkForPinLaunchConditions()                              // step 8: side-effect only
    drift.checkForDrift()                                          // step 9: side-effect only
    return false
}
```

The distinct return values matter and must be preserved: the demo branch (step 1) halts the pipeline returning `false`; steps 2–7 short-circuit returning `true` when they claim the launch; steps 8–9 are non-claiming side-effects after which the method returns `false`. Each step body takes the session explicitly rather than reading `CommCareApplication.instance().getCurrentSession()` ambiently (see Risk on ambient reads) — in particular step 5's post-update-sync check and step 6's username lookup, which read the global today.

**Host interface, not concrete activity.** The coordinator and its delegates need a handful of host capabilities — `Context`, `lifecycle`, `startActivityForResult`, `showAlertDialog`, `rebuildOptionsMenu`, and an optional UI-refresh hook (used by `changeLanguage()`; `OpportunityHomeActivity` has no `WithUIController`). These are exposed through a small `HomeActivityHost` interface that both `OpportunityHomeActivity` and the rebased base classes implement, keeping the coordinator unit-testable and free of any concrete-activity coupling.

**Facade, not god object.** The real behavior stays in the delegates and in small per-action launches; the coordinator wires and exposes. It has a natural internal seam — the lifecycle/session-coordination half and the action half — and if either accumulates real logic beyond wiring, that is the signal to split it. The action list is intentionally a set of typed methods plus availability queries rather than a first-class `HomeAction` registry; a registry (a list both the menu and the UI iterate to render generically) is only worth introducing if a host needs to render a dynamic, enumerated set, which neither known host requires today.

### Refactor of existing base classes

To avoid two implementations of the same behaviors, `SyncCapableCommCareActivity` and `HomeScreenBaseActivity` are rebased onto the same `HomeActivityCoordinator` the new activity composes — one source of truth. Their public API stays the same; internally they hold a coordinator, implement `HomeActivityHost`, and forward calls. Because those base classes still extend `SessionAwareCommCareActivity`, they call `attachSession(...)` once in `onCreateSessionSafe` and never detach (they cannot reach a session-less state). `StandardHomeActivity` is unchanged externally — its `onOptionsItemSelected` becomes a thin dispatch to coordinator actions (`coordinator.viewSavedForms()`, etc.) and its `onPrepareOptionsMenu` gating becomes per-action availability queries (`coordinator.canViewSavedForms()`, etc.), but the menu it presents is the same.

`SessionAwareCommCareActivity` itself is not modified. The "redirect to login on missing session" behavior continues to apply to every other activity that extends it.

### `OpportunityHomeActivity`

```
class OpportunityHomeActivity : BaseDrawerActivity<OpportunityHomeActivity>(), HomeActivityHost {
    // One coordinator owns and wires the five delegates; the activity holds no delegate directly.
    private val coordinator = HomeActivityCoordinator(host = this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coordinator.onCreate(savedInstanceState)  // registers delegates as observers; restores instance state
        // No session lookup here.
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        coordinator.onSaveInstanceState(outState)  // unconditional; not gated on attach
    }

    fun onSessionAvailable(session: SeatedAppSession) {
        coordinator.attachSession(session)  // fans out to the session-dependent delegates
    }

    fun onSessionLost() {
        coordinator.detachSession()
    }

    override fun onActivityResult(...) {
        coordinator.onActivityResult(...)  // fans out to delegates that need it
        super.onActivityResult(...)
    }
}
```

The coordinator's `onCreate(...)` is what registers the five delegates as lifecycle observers and unconditionally restores `SessionLaunchDelegate`'s instance state; the activity no longer enumerates delegates itself. `onSessionAvailable(...)` is invoked from two places inside the activity: the completion path for inline silent login (driving the situation 2 → 3 and 4 → 5 transitions), and an `onResume` check that calls it whenever a session exists but no session is currently attached. `onSessionLost()` is invoked from two distinct mechanisms, which today reach the chain through different paths:

- **Manual logout** — `userTriggeredLogout()` → `CommCareApplication.closeUserSession()` (no broadcast).
- **Automatic expiration** — `CommCareApplication.expireUserSession()`, which fires the `USER_SESSION_EXPIRED` broadcast; today `SessionRegistrationHelper`'s receiver responds by calling `redirectToLogin()` + `finish()`, and `SessionAwareHelper` does the same on a caught `SessionUnavailableException`.

`SessionExpirationDelegate` must intercept both and route them to the host's session-lost listener **instead of** the default redirect-and-finish — that default behavior is what every other `SessionAwareCommCareActivity` keeps, but it is exactly what `OpportunityHomeActivity` must not do. Crucially, the activity itself does **not** finish or redirect on session loss — it simply detaches and the underlying state-resolution logic (out of scope) renders the appropriate "needs login" surface, which re-triggers inline silent login.

The activity does not implement `WithUIController`. UI is delegated to fragments under the drawer host.

### Capabilities the activity exposes when attached

The coordinator exposes these capabilities as host-agnostic **actions**, each paired with an **availability query** that the host consults to decide whether (and where) to surface it. All require a seated session; the host supplies the gating predicate (`StandardHomeActivity` passes `!isDemoUser()`, which itself requires a session; `OpportunityHomeActivity` passes "session attached"), so the coordinator does not hard-code `isDemoUser()`. **How** each action is surfaced — overflow menu, on-screen button, drawer entry — is the host's decision and is not encoded here; this is precisely the seam that lets different home screens place the same capability differently.

A few actions are richer than a launch and route into a delegate:

- **`sync()`** → `SyncDelegate.sendFormsOrSync()`. `SyncDelegate` *is* the `PullTaskResultReceiver`; the host activity stays the `CommCareTaskConnector` passed to `DataPullTask.connect(...)` and its `getReceiver()` returns the delegate (see risks). This keeps the in-activity blocking/spinner UI while moving the result-handling implementation into the delegate.
- **Form entry** → `SessionLaunchDelegate.startForm(...)`; result handling fans out to `SyncDelegate` (post-form sync prompt) and `SessionExpirationDelegate` (mid-form expiration check), matching the current `HomeScreenBaseActivity` pipeline. (Not a menu action — triggered by the "Start" surface, but it is a coordinator-routed capability.)
- **`updateApp()` / `updateCommCare()`** → the app-update flow owned by `AppUpdateDelegate`, which is always registered and self-gates prompt *surfacing* on whether a session currently exists (re-evaluating `shouldShowInAppUpdate()`), rather than being attached/detached.

The remaining actions are plain launches or dialogs; the coordinator owns the body (moved out of `HomeScreenBaseActivity`) and any small state they carry (e.g. the developer-mode click counter behind `showAbout()`). Their availability mirrors `StandardHomeActivity`'s current `menu_app_home` gating:

| Action | Today's handler | Availability |
|---|---|---|
| `viewSavedForms()` | `goToFormArchive(...)` | menus enabled |
| `openSettings()` | `createPreferencesMenu(...)` | menus enabled |
| `openAdvanced()` | `showAdvancedActionsPreferences()` | menus enabled |
| `showAbout()` | `showAboutCommCareDialog()` | menus enabled |
| `updateApp()` | `launchUpdateActivity(...)` | menus enabled |
| `setPin()` | `launchPinAuthentication()` | menus enabled **and** `DeveloperPreferences.shouldOfferPinForLogin()` |
| `updateCommCare()` | `startCommCareUpdate()` | menus enabled **and** `showCommCareUpdateMenu` |
| `changeLanguage()` | `showLocaleChangeMenu(...)` | acts on a seated app (session-gated), but currently always *visible* in the menu; routed through the host's optional UI-refresh hook since `OpportunityHomeActivity` has no `WithUIController` (verified during implementation) |

## Risks and mitigations

- **Behavior drift between the chain and the new activity.** Mitigated by making the rebased base classes hold the same `HomeActivityCoordinator` (and therefore the same delegates and action bodies) the new activity composes — one source of truth. Forwarding-only changes in the base classes minimize the chance of semantic divergence.
- **Delegates currently read `CommCareApplication.instance().getCurrentSession()` ambiently.** Required change: thread the session explicitly through `attachSession(...)`. Audit each migrated callsite for ambient reads and replace.
- **`PullTaskResultReceiver` and similar Java interfaces are implemented by the current base classes.** Resolved: the task framework (`DataPullTask<R>` → `CommCareTask<…, R>` → `CommCareTaskConnector<R>`) places no `Activity` bound on the receiver type — it calls `receiver.handlePullTaskResult(...)` with no cast, and `SyncOperations.kt` already runs sync with a non-Activity receiver via `HeadlessTaskConnector`. The only `Activity` coupling is `FormAndDataSyncer.syncData(<I extends CommCareActivity & PullTaskResultReceiver>)`, where the bound exists because the argument doubles as the **connector** passed to `.connect(...)`. So the delegate owns the `PullTaskResultReceiver` implementation while the **host activity remains the `CommCareTaskConnector`** (its `getReceiver()` returns the delegate). This preserves the activity's task-transition/blocking UI hooks, which a `HeadlessTaskConnector` would no-op away.
- **Existing Robolectric tests may assert against the base classes' internal state.** Verify during implementation that tests against `HomeScreenBaseActivity` / `SyncCapableCommCareActivity` still pass with the internal forwarding.

## Open questions for implementation

1. **`PullTaskResultReceiver` ownership — resolved.** The framework imposes no `Activity` bound on the receiver. `SyncDelegate` owns the `PullTaskResultReceiver` implementation; the host activity stays the `CommCareTaskConnector` and returns the delegate from `getReceiver()`. See Approach and Risks.
2. **Session-loss UX surface — resolved (no existing dialog to preserve).** Session loss today is silent: both `SessionAwareHelper` (on caught `SessionUnavailableException`) and the `USER_SESSION_EXPIRED` broadcast receiver just call `redirectToLogin()` + `finish()`; the only special case is `FormEntryActivity` returning `WAS_INTERRUPTED`. There is no expiration dialog. `OpportunityHomeActivity` therefore defines new behavior rather than preserving any — and `SessionExpirationDelegate` must suppress the default redirect-and-finish in favor of the host's session-lost listener. Any dialog before the fragment swap is a new UX decision; defer to product/UX or the state-design spec.
3. **`AppUpdateController` lifecycle — resolved (do not rebind per session).** Reconstructing the controller on each `attachSession` would leak Play Core listeners, orphan in-progress downloads, and re-fetch needlessly. `AppUpdateDelegate` is therefore session-independent: constructed once on the host lifecycle, self-gating only prompt surfacing on a session existing. See Approach.

Remaining verify-during-implementation items:

4. **Preserve `doLoginLaunchChecksInOrder` semantics — resolved (single owner).** This method is a strict 9-step ordered pipeline (demo-mode early return → update-info form → form restoration → session restoration → post-update sync → pending FCM sync → update prompt → PIN check → drift check). Rather than letting the order be re-expressed per host or scattered across the delegates that absorb its pieces, the order lives in exactly one place: `HomeActivityCoordinator.runLaunchChecks(session)` (see [`HomeActivityCoordinator`](#homeactivitycoordinator)). The delegates contribute step bodies; the coordinator hard-codes the sequence and early-return behavior, called by both hosts. Verify during implementation that the bodies preserve their original guards (e.g. demo users still skip steps 2–9) so demo users don't start receiving prompts they currently skip.
5. **Demo mode and other StandardHome-specific concerns** are non-goals here, but they sit on the same chain. Demo handling is just inline `isDemoUser()` checks (the `doLoginLaunchChecksInOrder` early return and `StandardHomeActivity.onPrepareOptionsMenu` menu gating), not a separate subsystem. Verify the rebase doesn't subtly change those semantics in `StandardHomeActivity`. Note that the menu-gating half of this relocates: the per-item `!isDemoUser()` visibility checks become the host-supplied gating predicate feeding the coordinator's per-action availability queries (`StandardHomeActivity` passes `!isDemoUser()`; see [`HomeActivityCoordinator`](#homeactivitycoordinator)). Preserve the demo semantics there rather than in a hand-written `onPrepareOptionsMenu`. The `doLoginLaunchChecksInOrder` early return is unaffected by this relocation. `WithUIController` is implemented only on `StandardHomeActivity`, so the rebase does not touch it.

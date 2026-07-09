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

This spec covers the activity's composition, lifecycle, the refactor of two existing base classes that today bake the "session must exist" assumption into inheritance, and the state resolution / session-management policy that decides which status fragment to show and when to establish a session (see [State resolution and session management](#opportunity-home-state-resolution-and-session-management)). Entry points/routing, the inline silent-login mechanics themselves, and rollout remain out of scope and handled in parallel designs.

## Design approach: inheritance vs. composition

The behavior this activity needs — sync, form entry, app updates, launch checks — lives today in an inheritance chain (`SessionAwareCommCareActivity` → `SyncCapableCommCareActivity` → `HomeScreenBaseActivity` → `StandardHomeActivity`), where the session gate is the common ancestor `SessionAwareCommCareActivity` and the behaviors layer on top of it. Two approaches can give `OpportunityHomeActivity` those behaviors: an inheritance approach that keeps the existing chain, and the composition approach the rest of the spec adopts.

They are not variations on one axis. They divide on a single question — **must the activity seat an app and establish a session without leaving the activity** (the defining requirement in the Summary)? The inheritance approach answers no; composition answers yes.

### Inheritance — two activities split by session state

Host the Opportunity Home UI in **two** activities and transition between them on session change:

- **Session present** → `OpportunityHomeActivity extends HomeScreenBaseActivity`. The gate at `SessionAwareCommCareActivity` is **satisfied, not bypassed** — the activity is only launched once a session exists — so it inherits sync/updates/launch-checks/PIN/drift unchanged, as a sibling of `StandardHomeActivity`.
- **Session absent** → `ConnectActivity` (already ungated, on the separate `NavigationHostCommCareActivity` branch) hosts the session-less states (Available / download CTA / loading) with home functionality hidden.

**Advantages:**

- **No refactor at all.** This is the decisive difference. The coordinator, delegates, `HomeActivityHost`, `attachSession`/`detachSession`, and `SavedStateRegistry` plumbing all exist *only* to let one activity gain and lose session capability at runtime. Accept two activities and that entire problem — and this whole spec's machinery — dissolves.
- **Cleanest inheritance semantics.** Each activity's is-a is fixed for its whole life, so there is no runtime has-a to model, no single-inheritance conflict (the two activities are on different branches), and `SessionAwareCommCareActivity` is untouched.
- **Less owed by the parallel inline-login flow.** The Dependency item 2 hand-off ("redirect completion into the *running* activity") disappears — the existing `LaunchOutcomeRouter.launchHome()` already launches a fresh home activity on login success; point it at `OpportunityHomeActivity`. Session loss can reuse the existing redirect pattern (→ `ConnectActivity` instead of `LoginActivity`) rather than new suppress-and-detach behavior.

**Disadvantages — all product/UX and state-machine coherence, not engineering contortion:**

- **It breaks the defining requirement.** Every session establishment or loss is an activity transition — recreation, animation, back-stack, state handoff — not the seamless single surface the Summary requires.
- **The resolution table splits across two hosts.** The no-session rows live in `ConnectActivity`; the attached/ready rows live in `OpportunityHomeActivity`. One state machine, two owners — harder to reason about than the single [`OpportunityHomeStateController`](#opportunityhomestatecontroller) table.
- **The expiration loop becomes activity churn.** The spec's "expire → detach → re-resolve to loading in place → next resume silently re-logs-in" collapses into finish/relaunch cycles, and back-button semantics after an expiration bounce get murky.
- **Concern-blur risk.** `ConnectActivity` is *Connect Home* (the jobs list); overloading it as the per-opportunity session-less host mixes it with *Opportunity Home*, a separation the product otherwise keeps distinct.

### Composition (chosen)

Move the five session-dependent behaviors into composable delegates owned by a `HomeActivityCoordinator` that any activity can hold, with explicit `attachSession` / `detachSession` transitions.

**Advantages, specific to this problem:**

- **Models the runtime capability precisely.** `attachSession` / `detachSession` *is* a has-a that comes and goes; "no session" becomes a representable, safe state rather than an exception — while keeping one continuous surface, which the inheritance approach cannot.
- **Sidesteps single inheritance entirely.** The `BaseDrawerActivity`-rooted new activity and the `SessionAwareCommCareActivity`-rooted existing chain hold the *same* coordinator — one source of truth — without a shared superclass and without touching `SessionAwareCommCareActivity` (non-goal preserved).
- **Separates the five concerns and makes each independently unit-testable.** Delegates are plain Kotlin classes that take the session as a parameter; they can be tested without standing up an activity. This is a significant advantage given the near-total absence of home-page unit tests today — see [Testability the refactor unlocks](#testability-the-refactor-unlocks).

**Disadvantages:**

- **More wiring ceremony** — coordinator, `HomeActivityHost` interface, delegate/lifecycle registration, `SavedStateProvider` plumbing, residual `onActivityResult` forwarding.
- **A pattern novel to this codebase**, so a learning curve against the inheritance idiom used everywhere else in the stack.
- **Indirection**: a capability call hops activity → coordinator → delegate, and protected `Activity` members must be surfaced through the host interface.

### Verdict

The real axis is not inheritance vs. composition but **single continuous surface vs. activity swap on session change.**

- **If the seamless single surface is a hard requirement** (as the Summary states), the inheritance approach is disqualified on that ground alone, and composition is the way to honor it: it is the only approach that keeps one activity *and* models the runtime session lifecycle honestly, at the cost of wiring ceremony.
- **If that requirement could flex** to accept an activity transition on every session change, the inheritance approach is the lowest-effort path by a wide margin — it needs none of this spec's machinery — and would beat composition on simplicity.

Composition is therefore chosen, but it rests on one load-bearing assumption made explicit here: **the seamless single surface is a real requirement, not a nice-to-have.** If that assumption ever softens, the fallback is the two-activity inheritance approach.

## Problem

`SessionAwareCommCareActivity.onCreate()` calls `SessionAwareHelper.onCreateHelper()`, which calls `CommCareApplication.instance().getSession()` and redirects to `LoginActivity` on `SessionUnavailableException`. This is the session gate. `SyncCapableCommCareActivity` and `HomeScreenBaseActivity` build on that gate and assume an app is seated for sync, form entry, app-update prompts, and post-login launch checks.

`OpportunityHomeActivity` cannot extend `SessionAwareCommCareActivity` — situation (1) above has no session. But it must still perform the work currently inherited from `SyncCapableCommCareActivity` and `HomeScreenBaseActivity` once a session does exist, and it must transition into and out of that capable state without recreating the activity.

The fix is to move the session-dependent behavior out of inheritance and into a small set of composable delegates that any activity — including the existing chain — can use, and that can be bound and unbound to a live session at runtime.

## Non-goals

- Modifying `SessionAwareCommCareActivity`. Every other session-gated activity in the app keeps the existing redirect-to-login behavior.
- Replacing `StandardHomeActivity` for non-Connect users.
- Designing the visual contents/layout of each status fragment. This spec decides *which* fragment shows for a given state and the session action that state implies, not the fragment's internal UI.
- Designing entry points, routing through `DispatchActivity`, or persistence of "last accessed opportunity".
- Designing the inline silent app-login flow (in-flight work). This spec assumes that capability is available and exposes a session to the activity when it completes. See the dependency note below for the two pieces of new logic that flow owes this activity.
- Implementing `WithUIController` on the new activity.

## Dependency: what the inline silent-login flow owes this activity

The mechanics that acquire a session already exist and are host-agnostic. Download/install is `ConnectAppUtils.downloadApp(installUrl, listener)`, which wraps `ResourceInstallUtils.startAppInstallAsync` behind a self-contained headless `CommCareTaskConnector` and reports through a `ResourceEngineListener`. Seat → sign-in → sync is the `ConnectAppLaunchController` → `ConnectAppLauncher` chain (`AppSeater.seatIfNeeded`, `LoginController.performLogin`, and the `LoginPhase.Syncing` progress phase). This activity's coordinator and delegates never invoke either — they only react to a completed session via `onSessionAvailable(session)`. The new work owed by the parallel inline-login flow is to stitch these existing stages into one "acquire a session without leaving the activity" flow (the *policy* for when this activity triggers that flow lives in [State resolution and session management](#opportunity-home-state-resolution-and-session-management); the *mechanics* below do not):

1. **Chain install into seat+login.** For situations 2 and 4 ("app not installed / seated / logged in"), `downloadApp` runs first and installs the `ApplicationRecord`; `AppSeater.seatIfNeeded` (which today returns `SeatResult.Failed` when `MultipleAppsUtil.getAppById` finds no record) then finds it and the existing seat+login chain proceeds. Opp Home supplies its own `ResourceEngineListener` — most naturally routing progress into the same `CustomProgressDialog` the launch chain already drives — rather than reusing `ConnectDownloadingFragment`'s listener, which is coupled to `ConnectActivity` and a `CommCareVerificationActivity` hop. Whether that verification step is required for a fresh install, and whether it can run without leaving the activity, is to be resolved by that flow.
2. **Redirect the completion hand-off into the running activity.** Both existing terminal paths leave the screen: `ConnectDownloadingFragment` ends in `ConnectAppUtils.launchApp()` (`closeUserSession()` + `CommCareLauncher.launchCommCareForAppId()` + `finish()`), and `ConnectAppLaunchController` success routes through `LaunchOutcomeRouter.launchHome()`, which fires a fresh `HomeScreenBaseActivity` intent. `OpportunityHomeActivity` must instead gain the session in place, so the success path needs a branch that calls `onSessionAvailable(session)` on the running activity rather than launching a new home. This is the "exposes a session to the activity when it completes" contract this spec assumes.

Both belong to the inline-login flow; the coordinator and delegates require no new launch logic.

## Approach

`OpportunityHomeActivity` extends `BaseDrawerActivity` — the layer immediately below the session gate that still provides the drawer and the underlying `CommCareActivity` capabilities.

Five behaviors are extracted from the existing inheritance chain into delegates, and a single `HomeActivityCoordinator` owns those delegates and exposes the home screen's capabilities as host-agnostic actions (see [`HomeActivityCoordinator`](#homeactivitycoordinator) below). A host activity holds one coordinator rather than five delegates directly. The five extracted delegates are:

| Delegate | Source today | Responsibilities |
|---|---|---|
| `SessionExpirationDelegate` | `SessionAwareHelper.onResumeHelper`, `onActivityResultHelper` | On resume / activity-result, check session expiration. Exposes a listener interface that the host activity registers; reports session-lost events via that listener rather than redirecting to login. |
| `SyncDelegate` | `SyncCapableCommCareActivity` | Owns `FormAndDataSyncer`, sync state, and the `PullTaskResultReceiver` implementation. Exposes `sendFormsOrSync()` and related entry points. The host activity remains the `CommCareTaskConnector` (see below). Also owns its own instance-state key — `KEY_LAST_ICON_TRIGGER` (`lastIconTrigger`), the sync-icon animation trigger — which is session-scoped and stays with this delegate rather than moving to the coordinator (see note below on save/restore vs. attach). |
| `AppUpdateDelegate` | `HomeScreenBaseActivity` (`AppUpdateController`, `UpdatePromptHelper`) | Two distinct mechanisms: the Play Core **binary** in-app update (`AppUpdateController` → `updateCommCare()`) and the CommCare **content/CCZ** update (`UpdatePromptHelper` → launch step 7; `launchUpdateActivity` → `updateApp()`). Both are registered on the host lifecycle, not attached/detached (session-independent; see Approach); prompt *surfacing* is session-gated by a different predicate per mechanism (see [Capabilities](#capabilities-the-activity-exposes-when-attached)). |
| `SessionLaunchDelegate` | `HomeScreenBaseActivity.doLoginLaunchChecksInOrder`, `SessionNavigator` usage | Form restoration, "Start" → form entry navigation, the step bodies it contributes to the launch pipeline (update-info form, form restoration, session restoration), and form-result handling. The *ordering* and early-return semantics of `doLoginLaunchChecksInOrder` are owned by the coordinator's `runLaunchChecks` (see [`HomeActivityCoordinator`](#homeactivitycoordinator)), not by this delegate. The launch/nav state its step bodies read — `wasExternal`, `loginExtraWasConsumed`, `pendingEndpointNavigationAfterSync` — is owned and persisted by the coordinator and passed into those bodies (see note below on save/restore vs. attach). |
| `CrashRecoveryDelegate` | `HomeScreenBaseActivity` crash-data registration | Registers app/crash data (`CrashUtil.registerAppData()`) on the host lifecycle. Active regardless of session. |

Each delegate is a Kotlin class implementing `DefaultLifecycleObserver`, registered on the host's `lifecycle` so it receives `onResume` / `onPause` / `onDestroy` directly — as is the coordinator itself (see [`HomeActivityCoordinator`](#homeactivitycoordinator)). Standard lifecycle callbacks therefore arrive through the observer mechanism with no forwarding. Only Activity callbacks that have no `Lifecycle` observer hook — `onActivityResult` and intent handling — are forwarded by the host to the delegates that need them.

Session-dependent delegates — `SessionExpirationDelegate`, `SyncDelegate`, and `SessionLaunchDelegate` — expose two explicit methods:

```
attachSession(session: SeatedAppSession)
detachSession()
```

When attached, the delegate has a live session and operates as the existing chain does today. When not attached, every public entry point is a clean no-op (callers receive a documented "no session" result; nothing throws `SessionUnavailableException`).

This requires threading the session through `attachSession` rather than reading `CommCareApplication.instance().getCurrentSession()` ambiently as the code does today (see [Risks](#risks-and-mitigations)) — which is what makes "no session" a representable, safe state rather than an exception.

**Instance-state ownership splits by nature.** Each key stays with the concern that owns its lifecycle, and both ride the `SavedStateRegistry` the base `ComponentActivity` already provides (not a forwarded `onSaveInstanceState`, since the `ON_CREATE` observer callback carries no `savedInstanceState` Bundle).

- **Coordinator** owns the three launch/nav keys — `wasExternal`, `loginExtraWasConsumed`, `pendingEndpointNavigationAfterSync` — because they describe how the activity was launched and must survive recreation that happens *before* a session is attached. It registers a `SavedStateProvider`, restores them in `onCreate(owner)` independent of attach state, and passes their values into `SessionLaunchDelegate`'s step bodies. `SessionLaunchDelegate` therefore holds no instance-state of its own.
- **`SyncDelegate`** owns `lastIconTrigger` (the sync-icon animation trigger), which is only meaningful once a session exists. It registers its own `SavedStateProvider`; the coordinator does not forward save/restore for it.

`CrashRecoveryDelegate` and `AppUpdateDelegate` do not implement `attachSession`/`detachSession`. `CrashRecoveryDelegate` is session-independent by nature. `AppUpdateDelegate` is treated as session-independent because the underlying `AppUpdateController` cannot be safely reconstructed per session: `register()` attaches an `InstallStateUpdatedListener` to the Google Play Core `AppUpdateManager` and kicks off an async info fetch, so reconstructing on each `attachSession` would leak listeners (duplicate callbacks), orphan any in-progress download from the Play Store state machine, and re-fetch update info needlessly. App-binary updates are not opportunity- or seated-app-scoped, so the delegate is constructed once on the host lifecycle (register on `onResume`, unregister on `onDestroy`).

One subtlety: `AppUpdateControllerFactory.create()` chooses between the real `CommcareFlexibleAppUpdateManager` and a no-op `DummyFlexibleAppUpdateManager` by reading `getSession().shouldShowInAppUpdate()` at construction (defaulting to real on `SessionUnavailableException`). Since `OpportunityHomeActivity` normally builds the delegate while session-less, that construction-time choice cannot be relied on — so the delegate re-evaluates `shouldShowInAppUpdate()` at prompt time to gate the Play Core prompt's *surfacing*. (The content/CCZ prompt is gated differently — see [Capabilities](#capabilities-the-activity-exposes-when-attached).)

### `HomeActivityCoordinator`

The delegates hold the session-dependent *behavior*, but two further concerns are shared across every home activity and would otherwise be duplicated in each host: wiring the delegates to the lifecycle, and deciding what a given home capability *does* independently of how it is surfaced. `HomeActivityCoordinator` owns both.

**Role 1 — composition root and lifecycle/session coordinator.** The coordinator is itself a `DefaultLifecycleObserver`. Constructed as a host field, it registers itself on the host `lifecycle` in its `init` block — before `ON_CREATE` is dispatched — so it receives `onCreate`/`onResume`/`onPause`/`onDestroy` directly rather than through forwarded calls. In its `onCreate(owner)` callback it constructs the five delegates and registers each as its own lifecycle observer. The only cross-cutting host callback still forwarded is `onActivityResult` (with intent handling) — it has no `Lifecycle` hook — which the coordinator fans out to the delegates that need it, alongside the session transitions.

Instance-state is owned, not forwarded: the coordinator and `SyncDelegate` each register their own `SavedStateProvider` on the host's `SavedStateRegistry` (see the instance-state note above), so no host `onSaveInstanceState` forward is needed.

It exposes `attachSession(session)` / `detachSession()` that fan out to the session-dependent delegates (`SessionExpirationDelegate`, `SyncDelegate`, `SessionLaunchDelegate`); `CrashRecoveryDelegate` and `AppUpdateDelegate` are session-independent and simply ride the lifecycle. This replaces the per-host `onSessionAvailable`/`onSessionLost`/`onActivityResult` forwarding that each home activity would otherwise hand-write.

**Role 2 — action facade.** The coordinator exposes the home screen's capabilities as host-agnostic **actions** — `sync()`, `viewSavedForms()`, `changeLanguage()`, `openSettings()`, `openAdvanced()`, `showAbout()`, `setPin()`, `updateApp()`, `updateCommCare()` — each either delegating to a delegate (`sync()` → `SyncDelegate`, `updateApp()`/`updateCommCare()` → `AppUpdateDelegate`) or performing a simple launch/dialog. Alongside each action it exposes an **availability query** (`canViewSavedForms()`, etc.). Crucially, the coordinator does **not** know whether an action is surfaced as an overflow-menu item, an on-screen button, or a drawer entry — that binding is the host's job. This is what lets one home screen route a capability through the menu while another promotes it to the main UI, with no duplicated action logic. See [Capabilities](#capabilities-the-activity-exposes-when-attached) below for the full action list and gating.

**Role 3 — post-login launch pipeline (single owner of the 9-step order).** The coordinator owns `runLaunchChecks(session): Boolean`, the one place the `doLoginLaunchChecksInOrder` ordering lives. Both hosts call it (`StandardHomeActivity` via the rebased base, `OpportunityHomeActivity` directly); neither re-expresses the sequence, and no delegate owns it. The delegates contribute the *bodies* of individual steps — `SessionLaunchDelegate` (update-info form, form restoration, session restoration), `SyncDelegate` (post-update sync, pending FCM sync), `AppUpdateDelegate` (update prompt) — while the coordinator hard-codes the order and the early-return semantics as a literal sequence reviewable line-for-line against today's `HomeScreenBaseActivity.doLoginLaunchChecksInOrder`:

```kotlin
fun runLaunchChecks(session: SeatedAppSession): Boolean {
    if (gating.isDemoUser()) { showDemoModeWarning(); return false }  // step 1: halt, unclaimed
    if (sessionLaunch.showUpdateInfoForm(session)) return true     // step 2
    if (sessionLaunch.tryRestoringFormFromExpiration(session)) return true  // step 3
    if (sessionLaunch.tryRestoringSession(session)) return true    // step 4
    if (sync.runPostUpdateSyncIfNeeded()) return true              // step 5
    if (sync.runPendingFcmSyncIfNeeded(session)) return true       // step 6
    if (appUpdate.promptForUpdateIfNeeded()) return true           // step 7
    checkForPinLaunchConditions(session)                          // step 8: side-effect only (coordinator-owned; see below)
    checkForDrift()                                               // step 9: side-effect only (coordinator-owned; see below)
    return false
}
```

The distinct return values must be preserved: step 1 halts returning `false`; steps 2–7 short-circuit returning `true` when they claim the launch; steps 8–9 are non-claiming side-effects after which the method returns `false`. Step bodies that need the session take it explicitly (the threaded-not-ambient change; see [Risks](#risks-and-mitigations)): steps 2–4's form/session restoration and step 6's username lookup read the global today. Step 5 reads a different global (`isPostUpdateSyncNeeded()` / `isUpdateBlockedOnSync()`), not the session, so it alone takes no session parameter above.

**Steps 8–9 (PIN and drift) are coordinator-owned, not delegates.** Both are one-shot, side-effect-only checks with no listener, lifecycle callback, or persistent state — the same category as the coordinator's small dialog/launch bodies (`showAbout()`, etc.), so a `DefaultLifecycleObserver` delegate would be ceremony without benefit. Step 8 (`checkForPinLaunchConditions(session)`) reads launch-intent state the coordinator already owns plus the seated app's preferences and user record — hence the `session` — and is the automatic counterpart to the user-invoked `setPin()` action, so both PIN entry points live together. Step 9 (`checkForDrift()`) is a stateless `DriftHelper` dialog trigger needing only `Context` and `showAlertDialog`, no session — which is why it takes no session parameter above, and which keeps drift out of `AppUpdateDelegate` (bundled with it only by shared origin in `HomeScreenBaseActivity`).

**Host interface, not concrete activity.** The coordinator and its delegates need a handful of host capabilities — `Context`, `lifecycle`, `savedStateRegistry`, `startActivityForResult`, `showAlertDialog`, `rebuildOptionsMenu`, an optional UI-refresh hook (used by `changeLanguage()`; `OpportunityHomeActivity` has no `WithUIController`), and the `gating` abstraction described next. These are exposed through a small `HomeActivityHost` interface that both `OpportunityHomeActivity` and the rebased base classes implement, keeping the coordinator unit-testable and free of any concrete-activity coupling.

**`gating` is two distinct host-supplied queries, not one:**

- `isDemoUser()` — consumed only by launch step 1's demo halt. `StandardHomeActivity` returns the existing `isDemoUser()`; `OpportunityHomeActivity` returns `false` (Connect users are never demo users).
- `areActionsAvailable()` — the per-action availability predicate the `canViewSavedForms()`-style queries consult. `StandardHomeActivity` returns `!isDemoUser()`; `OpportunityHomeActivity` returns "session attached."

They must stay separate because they coincide for `StandardHomeActivity` (`!isDemoUser()`) but are independent for `OpportunityHomeActivity`, where `isDemoUser()` is always `false` while `areActionsAvailable()` tracks attach/detach.

**Facade, not god object.** Real behavior stays in the delegates and small per-action launches; the coordinator only wires and exposes, with a natural seam between its coordination half and its action half if either later grows. The action list is deliberately typed methods plus availability queries, not a `HomeAction` registry — a registry is only worth it if a host must render a dynamic, enumerated set, which neither host needs today.

### Refactor of existing base classes

To avoid two implementations of the same behaviors, `SyncCapableCommCareActivity` and `HomeScreenBaseActivity` are rebased onto the same `HomeActivityCoordinator` the new activity composes — one source of truth. Their public API stays the same; internally they hold a coordinator, implement `HomeActivityHost`, register it on their lifecycle so it observes directly, and forward the residual non-lifecycle callbacks (`onActivityResult`). Because those base classes still extend `SessionAwareCommCareActivity`, they call `attachSession(...)` once in `onCreateSessionSafe` and never detach (they cannot reach a session-less state). `StandardHomeActivity` is unchanged externally — its `onOptionsItemSelected` becomes a thin dispatch to coordinator actions (`coordinator.viewSavedForms()`, etc.) and its `onPrepareOptionsMenu` gating becomes per-action availability queries (`coordinator.canViewSavedForms()`, etc.), but the menu it presents is the same.

`SessionAwareCommCareActivity` itself is not modified. The "redirect to login on missing session" behavior continues to apply to every other activity that extends it.

### `OpportunityHomeActivity`

```
class OpportunityHomeActivity : BaseDrawerActivity<OpportunityHomeActivity>(), HomeActivityHost {
    // One coordinator owns and wires the five delegates; the activity holds no delegate directly.
    // Constructed as a field and self-registered on the lifecycle in its init block, before
    // ON_CREATE is dispatched, so it receives onCreate through the observer mechanism.
    private val coordinator = HomeActivityCoordinator(host = this)

    // Opportunity-aware state resolver + fragment router; owned by the activity and distinct from
    // the coordinator (see State resolution and session management). Decides which status fragment
    // shows and when to trigger auto silent-login. Reads "session attached?" from the coordinator.
    private val controller = OpportunityHomeStateController(host = this, coordinator = coordinator)

    // No onCreate/onSaveInstanceState overrides for the coordinator: it observes the lifecycle
    // directly and owns its instance state via the host's SavedStateRegistry. No session lookup here.

    // Fan-out, not chaining: both session hooks notify the coordinator (attach/detach capabilities)
    // AND the controller (re-resolve the UI). See State resolution and session management.
    fun onSessionAvailable(session: SeatedAppSession) {
        coordinator.attachSession(session)  // fans out to the session-dependent delegates
        controller.reResolveState()         // now attached → resolves to the phase's "ready" surface
    }

    fun onSessionLost() {
        coordinator.detachSession()
        controller.reResolveState()         // now detached → resolves to loading/CTA for the phase
    }

    override fun onActivityResult(...) {
        coordinator.onActivityResult(...)  // no Lifecycle hook — still forwarded
        super.onActivityResult(...)
    }
}
```

The coordinator's `onCreate(owner)` observer callback is what registers the five delegates as lifecycle observers and unconditionally restores its own launch/nav instance state from the `SavedStateRegistry`; the activity no longer enumerates delegates or forwards lifecycle/instance-state calls itself. `onSessionAvailable(...)` is invoked from the completion path for inline silent login (driving the situation 2 → 3 and 4 → 5 transitions). That login is initiated by `OpportunityHomeStateController` on resume whenever the phase's app is installed and no session is attached (see [State resolution and session management](#opportunity-home-state-resolution-and-session-management)); because `ConnectAppLauncher` short-circuits to `Launched` when a session for that app already exists, the same resume trigger also covers the "session exists but not yet attached" case. `onSessionLost()` is invoked from two distinct mechanisms, which today reach the chain through different paths:

- **Manual logout** — `userTriggeredLogout()` → `CommCareApplication.closeUserSession()` (no broadcast).
- **Automatic expiration** — `CommCareApplication.expireUserSession()`, which fires the `USER_SESSION_EXPIRED` broadcast; today `SessionRegistrationHelper`'s receiver responds by calling `redirectToLogin()` + `finish()`, and `SessionAwareHelper` does the same on a caught `SessionUnavailableException`.

`SessionExpirationDelegate` must intercept both and route them to the host's session-lost listener **instead of** the default redirect-and-finish — that default behavior is what every other `SessionAwareCommCareActivity` keeps, but it is exactly what `OpportunityHomeActivity` must not do. Crucially, the activity itself does **not** finish or redirect on session loss — it simply detaches and `OpportunityHomeStateController` re-resolves to the loading/CTA surface for the phase, from which the next resume re-triggers inline silent login (see [State resolution and session management](#opportunity-home-state-resolution-and-session-management)).

The activity does not implement `WithUIController`. UI is delegated to fragments under the drawer host.

### Capabilities the activity exposes when attached

The coordinator exposes these capabilities as host-agnostic **actions**, each paired with an **availability query** that the host consults to decide whether (and where) to surface it. All require a seated session; each action's availability query consults the host-supplied `gating.areActionsAvailable()` predicate (`StandardHomeActivity` → `!isDemoUser()`, which itself requires a session; `OpportunityHomeActivity` → "session attached"), so the coordinator does not hard-code `isDemoUser()`. That predicate is distinct from `gating.isDemoUser()`, which only launch step 1 consumes (see [`HomeActivityCoordinator`](#homeactivitycoordinator)). **How** each action is surfaced — overflow menu, on-screen button, drawer entry — is the host's decision and is not encoded here; this is precisely the seam that lets different home screens place the same capability differently.

A few actions are richer than a launch and route into a delegate:

- **`sync()`** → `SyncDelegate.sendFormsOrSync()`. `SyncDelegate` *is* the `PullTaskResultReceiver`; the host activity stays the `CommCareTaskConnector` passed to `DataPullTask.connect(...)` and its `getReceiver()` returns the delegate (see risks). This keeps the in-activity blocking/spinner UI while moving the result-handling implementation into the delegate.
- **Form entry** → `SessionLaunchDelegate.startForm(...)`; result handling fans out to `SyncDelegate` (post-form sync prompt) and `SessionExpirationDelegate` (mid-form expiration check), matching the current `HomeScreenBaseActivity` pipeline. (Not a menu action — triggered by the "Start" surface, but it is a coordinator-routed capability.)
- **`updateApp()` / `updateCommCare()`** → the two distinct app-update flows owned by `AppUpdateDelegate`, always registered rather than attached/detached, and gated differently:
  - `updateCommCare()` → `startCommCareUpdate()` drives the Play Core **binary** in-app update. The delegate gates its prompt surfacing by re-evaluating the session-derived `shouldShowInAppUpdate()` at prompt time (see the construction-time subtlety above).
  - `updateApp()` → `launchUpdateActivity()`, and launch step 7's `promptForUpdateIfNeeded()`, drive the CommCare **content/CCZ** update. This is seated-app-dependent: its guard is that a seated app exists for `UpdatePromptHelper` to inspect (plus the helper's own internal availability check), *not* `shouldShowInAppUpdate()`.

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

## Opportunity Home state resolution and session management

The coordinator and delegates deliberately know nothing but session-present/absent. Something still has to decide, for a given opportunity, *which* status fragment to render and *when* to establish a session. That opportunity-aware policy lives here, in a component distinct from the coordinator so the coordinator's session-agnostic design (see [`HomeActivityCoordinator`](#homeactivitycoordinator)) is preserved and the shared base-class path never inherits opportunity logic.

### Resolution inputs

The resolved state is a pure function of three inputs:

1. **Opportunity phase** — Available (not yet accepted), Learning, or Delivery, read from the opportunity being displayed.
2. **App installed?** — `MultipleAppsUtil.getAppById(appId) != null` for the phase's app (learn app in Learning, deliver app in Delivery). "Installed" means an `ApplicationRecord` exists locally and is therefore seatable; `AppSeater.seatIfNeeded` returns `SeatResult.Failed` when it does not.
3. **Session attached?** — a session is currently attached to the coordinator for that app.

### Resolution table

| Phase | Installed? | Attached? | Fragment | Session action |
|---|---|---|---|---|
| Available (not accepted) | — | no | Job intro / accept | None. Never auto-download. |
| Learning | no | no | Learning status, **download CTA** | None automatic. CTA → user-initiated download → existing seat+login chain. |
| Learning | yes | no | Learning status, **loading** (→ error+retry on failure) | Auto silent-login on every resume. |
| Learning | yes | yes | Learning status, **ready** | Already attached. |
| Delivery | no | no | Delivery status, **download CTA** | None automatic. CTA → user-initiated download. |
| Delivery | yes | no | Delivery status, **loading** (→ error+retry) | Auto silent-login on every resume. |
| Delivery | yes | yes | Delivery status, **ready** | Already attached. |

The defining rule across the table: the app is **never downloaded automatically** — a missing app always resolves to a download CTA the user drives — but a present-but-unseated/unauthenticated app resolves to an automatic silent login, so a returning user reaches the "session attached" state without touching anything.

### `OpportunityHomeStateController`

A new component owned by `OpportunityHomeActivity`, distinct from `HomeActivityCoordinator`. Its responsibilities:

1. Compute the resolved state from the three inputs.
2. Swap the drawer-host fragment to match the resolved state.
3. When the state calls for it, trigger auto silent-login through the inline-login flow.

It holds no session-capability logic — sync, launch checks, app updates, and the rest stay in the coordinator's delegates. It is a state resolver and fragment router, nothing more.

**Lifecycle wiring.** Like the coordinator, the controller is a `DefaultLifecycleObserver` constructed as an `OpportunityHomeActivity` field and self-registered on the host `lifecycle` in its `init` block, so it receives `onResume` directly through the observer mechanism with no forwarded call. Its `onResume(owner)` callback runs `reResolveState()` and then, when the resolved state is a loading state (app installed, no session attached), fires the auto silent-login trigger (see [Auto-login trigger](#auto-login-trigger--every-resume-unconditional)). `reResolveState()` itself only recomputes the three inputs and swaps the fragment; it does **not** trigger login. That separation is what lets the session-loss path call `reResolveState()` (via the activity's `onSessionLost`; see **Fan-out, not chaining** below) to drop back to the loading/CTA surface *without* immediately re-authenticating — the next resume owns the re-login.

**Host needs.** The controller's collaborators are Opportunity-specific and deliberately kept off the shared `HomeActivityHost` (which serves `StandardHomeActivity` too): the `coordinator` (for the "session attached?" query), the current opportunity (source of the phase input), the drawer-host `FragmentManager`/container it swaps fragments in, and the `CustomProgressDialog` surface the login flow drives. These are supplied by `OpportunityHomeActivity` directly — the `host` argument in the sketch above is the activity in this Opportunity-specific role, *not* the coordinator's `HomeActivityHost` — so the shared interface stays lean.

**Fan-out, not chaining.** The activity's existing `onSessionAvailable(session)` / `onSessionLost()` hooks (see [`OpportunityHomeActivity`](#opportunityhomeactivity)) fan out to *both* the coordinator (`attachSession` / `detachSession` — attach/detach capabilities, unchanged) and the controller (`reResolveState()` — recompute which status fragment to show). The coordinator stays session-agnostic and the controller never touches delegates; they meet only at these two hooks.

### Auto-login trigger — every resume, unconditional

On each `onResume`, if the phase's app is installed and no session is attached, the controller initiates silent login. It reuses `ConnectAppLauncher`'s seat → silent PersonalID login → sync chain (empty password, `AuthSource.PersonalId`), which short-circuits to `Launched` when a session for that app already exists. The controller decides only *when* to trigger and consumes the resulting `onSessionAvailable`; the mechanics of routing that chain's completion back into the running activity rather than launching a fresh home remain the parallel inline-login flow's dependency (see [Dependency](#dependency-what-the-inline-silent-login-flow-owes-this-activity), item 2). Progress surfaces through the same `CustomProgressDialog` the launch chain already drives.

**No in-page logout.** Because auto-login re-fires on every resume, Opportunity Home must not expose an in-page logout action — logout must navigate away from the page, or it would immediately log the user back in. Automatic expiration is not a problem: it detaches, the controller re-resolves to loading, and the next resume silently re-establishes the session — precisely the seamless behavior this design targets.

### Auto-login failure — inline error and retry

Token-denied already routes to a global error inside `ConnectAppLauncher` (`GlobalErrors.PERSONALID_LOST_CONFIGURATION_ERROR`) and is not this controller's concern. Other retryable failures (network, sync, transient) render the status fragment with an inline error banner and a Retry button that re-runs the login. Because the resume trigger is unconditional, the controller also silently re-attempts on the next resume; the error surface exists so a user staring at the page has an explicit action rather than a stalled spinner.

## Risks and mitigations

- **Behavior drift between the chain and the new activity.** Mitigated by making the rebased base classes hold the same `HomeActivityCoordinator` (and therefore the same delegates and action bodies) the new activity composes — one source of truth. Forwarding-only changes in the base classes minimize the chance of semantic divergence.
- **Delegates currently read `CommCareApplication.instance().getCurrentSession()` ambiently.** Required change: thread the session explicitly through `attachSession(...)`. Audit each migrated callsite for ambient reads and replace.
- **`PullTaskResultReceiver` and similar Java interfaces are implemented by the current base classes.** Resolved: the task framework (`DataPullTask<R>` → `CommCareTask<…, R>` → `CommCareTaskConnector<R>`) places no `Activity` bound on the receiver type — it calls `receiver.handlePullTaskResult(...)` with no cast, and `SyncOperations.kt` already runs sync with a non-Activity receiver via `HeadlessTaskConnector`. The only `Activity` coupling is `FormAndDataSyncer.syncData(<I extends CommCareActivity & PullTaskResultReceiver>)`, where the bound exists because the argument doubles as the **connector** passed to `.connect(...)`. So the delegate owns the `PullTaskResultReceiver` implementation while the **host activity remains the `CommCareTaskConnector`** (its `getReceiver()` returns the delegate). This preserves the activity's task-transition/blocking UI hooks, which a `HeadlessTaskConnector` would no-op away.
- **Existing Robolectric tests may assert against the base classes' internal state.** Verify during implementation that tests against `HomeScreenBaseActivity` / `SyncCapableCommCareActivity` still pass with the internal forwarding.

## Open questions for implementation

The design questions are settled in the sections above (`PullTaskResultReceiver` ownership → Approach/Risks; session-loss behavior → [`OpportunityHomeActivity`](#opportunityhomeactivity); `AppUpdateController` lifecycle → Approach; launch-pipeline ownership → [`HomeActivityCoordinator`](#homeactivitycoordinator)). What remains are verify-during-implementation items:

1. **Launch-pipeline guards.** Confirm each moved step body keeps its original guard — in particular that demo users still skip steps 2–9, so they don't start receiving prompts they skip today.
2. **Demo semantics in `StandardHomeActivity`.** The per-item `!isDemoUser()` menu gating relocates to the coordinator's availability predicate (`StandardHomeActivity` passes `!isDemoUser()`); confirm the rebase preserves both it and the unchanged `doLoginLaunchChecksInOrder` early return. `WithUIController` stays `StandardHomeActivity`-only and is untouched.
3. **Session-loss interstitial (product/UX).** Session loss is silent today (`redirectToLogin()` + `finish()`, no dialog); this spec routes it to the phase's loading/CTA surface instead. Whether to show any dialog before the fragment swap is a new, still-open UX decision.

## Pre-refactor unit testing (regression safety net)

This refactor moves roughly 2,300 lines of session-dependent behavior out of `HomeScreenBaseActivity` (1,828 lines) and `SyncCapableCommCareActivity` (495 lines) into a coordinator and five delegates. The §Risks section already flags the central hazard — behavior drift between the current chain and the rebased classes. Today there is almost nothing to catch that drift. **Before any production code changes, a characterization ("pinning") test suite should be written against the current, observable behavior of the home activities**, so that the same tests, run unchanged against the rebased classes, prove the refactor is behavior-preserving.

### Guiding principle

Because the rebase keeps the base classes' **public API unchanged** (see [Refactor of existing base classes](#refactor-of-existing-base-classes)) and both `StandardHomeActivity` and `RootMenuHomeActivity` keep extending them, tests written against the **observable behavior** of `StandardHomeActivity` — next-started intents, dialogs shown, menu-item visibility, fragments swapped, saved/restored instance state — survive the refactor and become its contract. The corollary is a hard rule, and it is the same hazard §Risks names under "Existing Robolectric tests may assert against the base classes' internal state": **pin behavior, never internal fields or private methods.** Tests coupled to internal structure will break on the rebase and produce false regressions instead of catching real ones. Any such existing assertions should be identified and reworked into behavioral assertions first.

### Current state

There is **no dedicated unit test for `StandardHomeActivity`, `RootMenuHomeActivity`, `HomeScreenBaseActivity`, or `SyncCapableCommCareActivity` as a unit.** The home activity is exercised only *incidentally*, as scaffolding in tests whose real subject is something else:

- **`DemoUserRestoreTest.java`** — its `checkOptionsMenuVisibility()` is the **only** assertion guarding demo-mode menu gating (change-language visible; update / saved-forms / preferences / advanced / about / set-pin hidden). This maps directly to the riskiest relocation in this spec (the demo-semantics verify item: per-item `!isDemoUser()` → coordinator availability queries), but it is buried in a heavyweight end-to-end test (`LEGACY` looper, an analytics-disabled workaround for an infinite loop) whose purpose is demo restore/update.
- **`ExternalLaunchTests.kt`** — drives `StandardHomeActivity` through `DispatchActivity` for session-endpoint and app-id launches. The closest thing to launch-path coverage; it exercises the `wasExternal` / endpoint-navigation state the coordinator will own, but asserts only on the next started intent, not the pipeline.
- **`PersonalIdDrawerVisibilityTest.kt`** — covers only nav-drawer visibility, a `BaseDrawerActivity` concern the refactor does not touch. No overlap with the moving parts.
- **`RecoveryMeasuresTest.java`** — drives `StandardHomeActivity` through recovery scenarios; a partial guard on recovery-measure execution around home launch, not on the extracted behaviors.
- **`FormRecordListActivityTest.java`** — uses the home activity purely as a scaffold for `GET_INCOMPLETE_FORM` result routing.
- **`HeartbeatAndPromptedUpdateTests.java`** — tests `UpdatePromptHelper` / `UpdateToPrompt` in isolation and never launches a home activity, so it does not cover the step-7 wiring.
- **`ActivityLaunchUtils.buildHomeActivity` + `FormAndDataSyncerFake`** — the shared harness many form/entity tests depend on; builds `StandardHomeActivity`, injects a fake syncer via `setFormAndDataSyncer(...)`, and drives the `SessionNavigator`.

Consequently, the behaviors this spec extracts are essentially uncovered at the home-activity level: the 9-step `doLoginLaunchChecksInOrder` pipeline (ordering and early-return semantics), sync (`sendFormsOrSync()`, `PullTaskResultReceiver.handlePullTaskResult`, the `lastIconTrigger` save/restore), session-expiration handling, PIN launch (step 8), drift check (step 9), in-home update prompting (step 7), and save/restore of the three launch/nav instance-state keys across recreation.

### Prioritized pre-refactor tests

1. **Pin the launch pipeline (highest value — zero coverage today).** Characterization tests on `StandardHomeActivity` observing side effects, not internals: demo user → pipeline halts and no update/PIN prompts appear; update-info form present → short-circuits (nothing further launches); form restoration on expiration; session restoration; PIN launch when the `LoginActivity.LOGIN_MODE` / `shouldOfferPinForLogin()` conditions hold; drift dialog when `DriftHelper` says so. Each becomes the line-for-line contract for `runLaunchChecks`, and covers the launch-pipeline-guards verify item.
2. **Promote the demo menu-gating guard** out of `DemoUserRestoreTest` into a focused `StandardHomeActivity` menu test: non-demo shows the full action set; demo shows only change-language; set-pin gated by `shouldOfferPinForLogin()`; commcare-update gated by `showCommCareUpdateMenu`. Directly protects the `onPrepareOptionsMenu` → availability-query relocation (the demo-semantics verify item).
3. **Pin the sync path** through the existing `FormAndDataSyncerFake` seam — `sendFormsOrSync()` and result handling observable behavior (sync-icon state, blocking/spinner dialog).
4. **Add a recreation round-trip test** proving launch/nav state (`wasExternal`, pending endpoint navigation) survives `recreate()` — this guards the instance-state ownership move from the activity to the coordinator's `SavedStateRegistry` provider.
5. **Pin session expiration** = the current redirect-to-login behavior for `StandardHomeActivity`, so the rebase demonstrably preserves it (and, by contrast, documents the behavior `OpportunityHomeActivity` deliberately diverges from).

Coverage should include `StandardHomeActivity` at minimum; note that `RootMenuHomeActivity` is a second host of `HomeScreenBaseActivity` with no coverage at all, so the rebase silently affects that path too.

### Harness prerequisites

Two harness concerns should be settled first, because they otherwise force churn across many unrelated tests during the refactor:

- **Preserve the `setFormAndDataSyncer(...)` injection seam.** `ActivityLaunchUtils` and every form/entity test depend on it. When `FormAndDataSyncer` moves into `SyncDelegate`, keep a host-level setter that forwards to the delegate, or the fake cannot attach.
- **Decide `FormAndDataSyncerFake`'s signature migration.** Its overrides take `SyncCapableCommCareActivity`; if `SyncDelegate` changes those signatures, migrate the fake in the same commit as the seam.

### Testability the refactor unlocks

The characterization tests above are a regression net, written through Robolectric against the *current* activity. Beyond protecting the refactor, the composition target is chosen partly because it makes the home page **unit-testable in a way it is not today** — directly serving the goal of adding real coverage to a page that has almost none (see [Current state](#current-state)). This is a first-class reason to prefer composition over the two-activity inheritance approach, which would leave testability at the status quo: `OpportunityHomeActivity` inheriting `HomeScreenBaseActivity` is still only exercisable by standing up the full gated activity in Robolectric.

Three properties of the target design do the work:

1. **Session is injected, not ambient.** Delegates receive the session through `attachSession(session)` instead of reading `CommCareApplication.instance().getCurrentSession()` (see [Risks](#risks-and-mitigations)). A test supplies a fake/mock `SeatedAppSession` — no seated `ApplicationRecord`, no session singleton to construct.
2. **The units are plain Kotlin classes behind `HomeActivityHost`.** The coordinator and each delegate can be built against a fake host and driven on the JVM without Robolectric. In particular, `runLaunchChecks(session)` — the 9-step ordering that carries the most risk and has zero coverage today — becomes a direct assertion on step order and early-return semantics against delegate fakes, exactly the line-for-line contract [`HomeActivityCoordinator`](#homeactivitycoordinator) describes.
3. **Coordination logic is separated from framework edges.** The [state resolution table](#resolution-table) (`OpportunityHomeStateController`, a pure function of phase × installed × attached), the two gating predicates (`isDemoUser()` / `areActionsAvailable()`), and the attach/detach transitions are pure enough to test as truth tables.

Honest boundary: the framework-touching edges still require Robolectric or mocking — `AppUpdateDelegate`'s Play Core `AppUpdateManager`, `SyncDelegate`'s `FormAndDataSyncer` / `PullTaskResultReceiver`, and dialog / fragment-swap surfaces. What composition newly makes unit-testable is the *coordination* (pipeline ordering, gating, state resolution, session lifecycle) — which is precisely the behavior uncovered today and most at risk in the refactor. The sequencing is therefore: pin the coarse observable behavior through Robolectric first (so the refactor is provably behavior-preserving), then add JVM unit tests on the extracted units as the seams appear.

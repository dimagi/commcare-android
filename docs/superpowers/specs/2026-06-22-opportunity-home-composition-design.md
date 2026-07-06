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
| `AppUpdateDelegate` | `HomeScreenBaseActivity` (`AppUpdateController`, `UpdatePromptHelper`) | Both app-update mechanisms, which are distinct: the Play Core **binary** in-app update (`AppUpdateController` → `startCommCareUpdate()` / `updateCommCare()`) and the CommCare **content/CCZ** update (`UpdatePromptHelper.promptForUpdateIfNeeded` → launch step 7; `launchUpdateActivity` → `updateApp()`). Both are registered on the host lifecycle rather than attached/detached, but for different reasons: the `AppUpdateController` half is **session-independent** because its Play Core listener/download state cannot be safely rebound (see below); the content half simply carries no listener or lifecycle state to rebind. Prompt *surfacing* is session-gated for both, but by different predicates (see [Capabilities](#capabilities-the-activity-exposes-when-attached)). (Drift checks are *not* here — they carry no state or lifecycle and stay in the coordinator; see the drift note under [`HomeActivityCoordinator`](#homeactivitycoordinator).) |
| `SessionLaunchDelegate` | `HomeScreenBaseActivity.doLoginLaunchChecksInOrder`, `SessionNavigator` usage | Form restoration, "Start" → form entry navigation, the step bodies it contributes to the launch pipeline (update-info form, form restoration, session restoration), and form-result handling. The *ordering* and early-return semantics of `doLoginLaunchChecksInOrder` are owned by the coordinator's `runLaunchChecks` (see [`HomeActivityCoordinator`](#homeactivitycoordinator)), not by this delegate. The launch/nav state its step bodies read — `wasExternal`, `loginExtraWasConsumed`, `pendingEndpointNavigationAfterSync` — is owned and persisted by the coordinator and passed into those bodies (see note below on save/restore vs. attach). |
| `CrashRecoveryDelegate` | `HomeScreenBaseActivity` crash-data registration | Registers app/crash data (`CrashUtil.registerAppData()`) on the host lifecycle. Active regardless of session. |

Each delegate is a Kotlin class implementing `DefaultLifecycleObserver`, registered on the host's `lifecycle` so it receives `onResume` / `onPause` / `onDestroy` directly — as is the coordinator itself (see [`HomeActivityCoordinator`](#homeactivitycoordinator)). Standard lifecycle callbacks therefore arrive through the observer mechanism with no forwarding. Only Activity callbacks that have no `Lifecycle` observer hook — `onActivityResult` and intent handling — are forwarded by the host to the delegates that need them.

Session-dependent delegates — `SessionExpirationDelegate`, `SyncDelegate`, and `SessionLaunchDelegate` — expose two explicit methods:

```
attachSession(session: SeatedAppSession)
detachSession()
```

When attached, the delegate has a live session and operates as the existing chain does today. When not attached, every public entry point is a clean no-op (callers receive a documented "no session" result; nothing throws `SessionUnavailableException`).

This requires the delegates to take their session as a parameter rather than reading `CommCareApplication.instance().getCurrentSession()` ambiently. Today's code reads the ambient global; the refactor threads it through `attachSession` so "no session" is a representable, safe state rather than an exception.

**Instance-state ownership splits by nature.** Two kinds of instance state coexist, and each stays with the concern that understands it.

The three launch/nav keys — `WAS_EXTERNAL_KEY` (`wasExternal`), `EXTRA_CONSUMED_KEY` (`loginExtraWasConsumed`), and `KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC` (`pendingEndpointNavigationAfterSync`) — describe how the activity was launched and what navigation is pending. That state exists *before* a session is attached and must survive recreation that happens while detached, so it does not belong to a session-dependent delegate. **The coordinator owns these three fields**, persisting them through a `SavedStateProvider` it registers with the host's `SavedStateRegistry` and restoring them when its `onCreate(owner)` observer callback fires, both unconditionally and independent of attach state; it then passes their current values into `SessionLaunchDelegate`'s launch step bodies, the same way it threads the session (see Risk on ambient reads). Save/restore rides the `SavedStateRegistry` rather than a forwarded `onSaveInstanceState` because the coordinator observes the lifecycle directly and the `ON_CREATE` observer callback carries no `savedInstanceState` Bundle (see [`HomeActivityCoordinator`](#homeactivitycoordinator)). `SessionLaunchDelegate` therefore holds no instance-state or lifecycle plumbing of its own: its behavioral entry points are session-dependent and no-op when detached, while the launch/nav state they consume is supplied by the coordinator.

The one remaining key, `KEY_LAST_ICON_TRIGGER` (`lastIconTrigger`), is different: it is the sync-icon animation trigger, computed today in `onCreateSessionSafe` and thus only meaningful once a session exists. Because it is genuinely session-scoped, **it stays with `SyncDelegate`** rather than moving to the coordinator. `SyncDelegate` registers its own `SavedStateProvider` for this key and reads it back on restore; the coordinator does not forward save/restore for it. This keeps each key with the concern that owns its lifecycle: pre-session launch/nav state on the coordinator, session-scoped sync state on the delegate.

`CrashRecoveryDelegate` and `AppUpdateDelegate` do not implement `attachSession`/`detachSession`. `CrashRecoveryDelegate` is session-independent by nature. `AppUpdateDelegate` is treated as session-independent because the underlying `AppUpdateController` cannot be safely reconstructed per session: `register()` attaches an `InstallStateUpdatedListener` to the Google Play Core `AppUpdateManager` and kicks off an async info fetch, so reconstructing on each `attachSession` would leak listeners (duplicate callbacks), orphan any in-progress download from the Play Store state machine, and re-fetch update info needlessly. App-binary updates are not opportunity- or seated-app-scoped, so the delegate is constructed once on the host lifecycle (register on `onResume`, unregister on `onDestroy`).

One subtlety: `AppUpdateControllerFactory.create(callback, context)` takes only a `Context` and a callback in its *signature*, but its body reads `getSession().shouldShowInAppUpdate()` to choose between the real `CommcareFlexibleAppUpdateManager` and a no-op `DummyFlexibleAppUpdateManager`, catching `SessionUnavailableException` and defaulting to the real manager when no session exists. Constructed once on the host lifecycle, `OpportunityHomeActivity` will normally build the delegate while session-less (situations 1/2/4), so it always gets the real manager — the construction-time `shouldShowInAppUpdate()` choice cannot be relied on. The delegate therefore gates the *surfacing* of the Play Core update prompt on a session existing by re-evaluating `shouldShowInAppUpdate()` itself at prompt time, independent of which manager the factory picked at construction. (The content/CCZ update prompt is a separate mechanism gated differently — see [Capabilities](#capabilities-the-activity-exposes-when-attached).)

### `HomeActivityCoordinator`

The delegates hold the session-dependent *behavior*, but two further concerns are shared across every home activity and would otherwise be duplicated in each host: wiring the delegates to the lifecycle, and deciding what a given home capability *does* independently of how it is surfaced. `HomeActivityCoordinator` owns both.

**Role 1 — composition root and lifecycle/session coordinator.** The coordinator is itself a `DefaultLifecycleObserver`. Constructed as a host field, it registers itself on the host `lifecycle` in its `init` block — before `ON_CREATE` is dispatched — so it receives `onCreate`/`onResume`/`onPause`/`onDestroy` directly rather than through forwarded calls. In its `onCreate(owner)` callback it constructs the five delegates and registers each as its own lifecycle observer. The only cross-cutting host callback still forwarded is `onActivityResult` (with intent handling) — it has no `Lifecycle` hook — which the coordinator fans out to the delegates that need it, alongside the session transitions.

Instance-state is owned, not forwarded (see note above on save/restore vs. attach). Because the `ON_CREATE` observer callback carries no `savedInstanceState` Bundle, the coordinator owns its three launch/nav keys through the host's `SavedStateRegistry`: it registers a `SavedStateProvider` for them and consumes the restored values in its `onCreate(owner)` callback. `SyncDelegate` likewise registers its own `SavedStateProvider` for its one session-scoped key (`lastIconTrigger`). No host `onSaveInstanceState` forward is needed — save and restore ride the `SavedStateRegistry` that the base `ComponentActivity` already provides.

It exposes `attachSession(session)` / `detachSession()` that fan out to the session-dependent delegates (`SessionExpirationDelegate`, `SyncDelegate`, `SessionLaunchDelegate`); `CrashRecoveryDelegate` and `AppUpdateDelegate` are session-independent and simply ride the lifecycle. This replaces the per-host `onSessionAvailable`/`onSessionLost`/`onActivityResult` forwarding that each home activity would otherwise hand-write.

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
    checkForDrift()                                                // step 9: side-effect only (coordinator-owned; see below)
    return false
}
```

The distinct return values matter and must be preserved: the demo branch (step 1) halts the pipeline returning `false`; steps 2–7 short-circuit returning `true` when they claim the launch; steps 8–9 are non-claiming side-effects after which the method returns `false`. Each step body that needs the session takes it explicitly rather than reading `CommCareApplication.instance().getCurrentSession()` ambiently (see Risk on ambient reads) — in particular steps 2–4's form/session restoration (`getCurrentSession()` / `getCurrentSessionWrapper()`) and step 6's username lookup (`getSession().getLoggedInUser()`), which read the global today. Step 5's post-update-sync check reads a different global (`isPostUpdateSyncNeeded()` / `isUpdateBlockedOnSync()`), not the session, which is why it alone takes no session parameter above.

**Drift check is coordinator-owned, not a delegate.** Unlike the other step bodies, step 9's `checkForDrift()` is a private coordinator method rather than a delegate call. `DriftHelper` already holds all the logic as stateless static methods; the check is a small dialog trigger (`shouldShowDriftWarning()` + `getCurrentDrift() != 0` → `showAlertDialog(getDriftDialog(...))` + `updateLastDriftWarningTime()`) needing only the host's `Context` and `showAlertDialog` — no session, no lifecycle listener, no instance state. It is the same category as the coordinator's other small dialog bodies (`showAbout()`, etc.), so it lives with them rather than forcing a `DefaultLifecycleObserver` delegate for a stateless five-line call. This keeps it out of `AppUpdateDelegate`, which is bundled only by shared origin in `HomeScreenBaseActivity` and otherwise shares nothing with the stateful, Play-Core-lifecycle-bound `AppUpdateController`.

**Host interface, not concrete activity.** The coordinator and its delegates need a handful of host capabilities — `Context`, `lifecycle`, `savedStateRegistry`, `startActivityForResult`, `showAlertDialog`, `rebuildOptionsMenu`, and an optional UI-refresh hook (used by `changeLanguage()`; `OpportunityHomeActivity` has no `WithUIController`). These are exposed through a small `HomeActivityHost` interface that both `OpportunityHomeActivity` and the rebased base classes implement, keeping the coordinator unit-testable and free of any concrete-activity coupling.

**Facade, not god object.** The real behavior stays in the delegates and in small per-action launches; the coordinator wires and exposes. It has a natural internal seam — the lifecycle/session-coordination half and the action half — and if either accumulates real logic beyond wiring, that is the signal to split it. The action list is intentionally a set of typed methods plus availability queries rather than a first-class `HomeAction` registry; a registry (a list both the menu and the UI iterate to render generically) is only worth introducing if a host needs to render a dynamic, enumerated set, which neither known host requires today.

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

    // No onCreate/onSaveInstanceState overrides for the coordinator: it observes the lifecycle
    // directly and owns its instance state via the host's SavedStateRegistry. No session lookup here.

    fun onSessionAvailable(session: SeatedAppSession) {
        coordinator.attachSession(session)  // fans out to the session-dependent delegates
    }

    fun onSessionLost() {
        coordinator.detachSession()
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

The coordinator exposes these capabilities as host-agnostic **actions**, each paired with an **availability query** that the host consults to decide whether (and where) to surface it. All require a seated session; the host supplies the gating predicate (`StandardHomeActivity` passes `!isDemoUser()`, which itself requires a session; `OpportunityHomeActivity` passes "session attached"), so the coordinator does not hard-code `isDemoUser()`. **How** each action is surfaced — overflow menu, on-screen button, drawer entry — is the host's decision and is not encoded here; this is precisely the seam that lets different home screens place the same capability differently.

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

**Fan-out, not chaining.** The activity's existing `onSessionAvailable(session)` / `onSessionLost()` hooks (see [`OpportunityHomeActivity`](#opportunityhomeactivity)) fan out to *both* the coordinator (attach/detach capabilities — unchanged) and the controller (re-resolve the UI). The coordinator stays session-agnostic and the controller never touches delegates; they meet only at these two hooks.

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

1. **`PullTaskResultReceiver` ownership — resolved.** The framework imposes no `Activity` bound on the receiver. `SyncDelegate` owns the `PullTaskResultReceiver` implementation; the host activity stays the `CommCareTaskConnector` and returns the delegate from `getReceiver()`. See Approach and Risks.
2. **Session-loss UX surface — resolved (no existing dialog to preserve).** Session loss today is silent: both `SessionAwareHelper` (on caught `SessionUnavailableException`) and the `USER_SESSION_EXPIRED` broadcast receiver just call `redirectToLogin()` + `finish()`; the only special case is `FormEntryActivity` returning `WAS_INTERRUPTED`. There is no expiration dialog. `OpportunityHomeActivity` therefore defines new behavior rather than preserving any — and `SessionExpirationDelegate` must suppress the default redirect-and-finish in favor of the host's session-lost listener. Any dialog before the fragment swap is a new UX decision; the state resolution in this spec routes session loss to the phase's loading/CTA surface (see [State resolution and session management](#opportunity-home-state-resolution-and-session-management)) but does not define an interstitial dialog — that is fragment-visual and deferred to product/UX.
3. **`AppUpdateController` lifecycle — resolved (do not rebind per session).** Reconstructing the controller on each `attachSession` would leak Play Core listeners, orphan in-progress downloads, and re-fetch needlessly. `AppUpdateDelegate` is therefore session-independent: constructed once on the host lifecycle, self-gating only prompt surfacing on a session existing. See Approach.

Remaining verify-during-implementation items:

4. **Preserve `doLoginLaunchChecksInOrder` semantics — resolved (single owner).** This method is a strict 9-step ordered pipeline (demo-mode early return → update-info form → form restoration → session restoration → post-update sync → pending FCM sync → update prompt → PIN check → drift check). Rather than letting the order be re-expressed per host or scattered across the delegates that absorb its pieces, the order lives in exactly one place: `HomeActivityCoordinator.runLaunchChecks(session)` (see [`HomeActivityCoordinator`](#homeactivitycoordinator)). The delegates contribute step bodies; the coordinator hard-codes the sequence and early-return behavior, called by both hosts. Verify during implementation that the bodies preserve their original guards (e.g. demo users still skip steps 2–9) so demo users don't start receiving prompts they currently skip.
5. **Demo mode and other StandardHome-specific concerns** are non-goals here, but they sit on the same chain. Demo handling is just inline `isDemoUser()` checks (the `doLoginLaunchChecksInOrder` early return and `StandardHomeActivity.onPrepareOptionsMenu` menu gating), not a separate subsystem. Verify the rebase doesn't subtly change those semantics in `StandardHomeActivity`. Note that the menu-gating half of this relocates: the per-item `!isDemoUser()` visibility checks become the host-supplied gating predicate feeding the coordinator's per-action availability queries (`StandardHomeActivity` passes `!isDemoUser()`; see [`HomeActivityCoordinator`](#homeactivitycoordinator)). Preserve the demo semantics there rather than in a hand-written `onPrepareOptionsMenu`. The `doLoginLaunchChecksInOrder` early return is unaffected by this relocation. `WithUIController` is implemented only on `StandardHomeActivity`, so the rebase does not touch it.

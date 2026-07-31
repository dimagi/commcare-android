# App Navigation Changes: Start-of-App Routing & App-Wide Back Navigation

**Design doc:** [Mobile App Redesign — UX](https://docs.google.com/document/d/1rntc16FW2Jfr6CbzNcZ9RDOxIRrmfcWNU6_d5WL1rA8/edit?tab=t.qf5nvzne52l2)

## Summary

Two coupled navigation changes for the Connect UI redesign:

1. **Start-of-app routing** — a run-once resolver that lands each user on the correct surface for their configuration and session state, with no login/setup screens flashing through.
2. **App-wide back navigation** — one consistent, flag-free back model spanning the Intro page, the top-level navigation drawer, and the app-home surfaces.

The spec defines a **north-star** target (a two-tier navigation shell with a single startup router) and a **pragmatic interim** that reaches the target behavior within today's activity topology without disrupting traditional CommCare users.

## Terminology

- **Traditional CommCare user** — A user with a manual login (username/password).
- **PersonalID user** — A user with PersonalID linked to an app, but **not** linked to an opportunity.
- **Connect user** — A user with a PersonalID linked to an app **and** linked to an opportunity ("carries Connect features").

An app is not intrinsically "Connect" or "traditional"; the same CommCare app can be used either way. What differs is the per-(app, user) linkage/auth state ([`PersonalIdManager.evaluateAppState`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/connect/PersonalIdManager.java#L520) → `Connect` / `PersonalId` / `Unmanaged`), which is single-valued at any moment.

## Background & problem

[`DispatchActivity`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/activities/DispatchActivity.java) is the sole `MAIN`/`LAUNCHER` activity ([`AndroidManifest.xml:149`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/AndroidManifest.xml#L149)) and the task-root router. Its routing runs in [`onResume()`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/activities/DispatchActivity.java#L140) → [`dispatch()`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/activities/DispatchActivity.java#L163), so it **re-evaluates on every return to the foreground** — the root cause of the "back into Dispatch → it re-dispatches → loop" defects. Its decision tree is essentially *login vs CommCare home*; Connect is reached only via deep link, push, or the post-login `redirectToConnectOpportunityInfo` flag — there is **no first-class "route to Connect home" branch** at cold start.

Combined with a multi-activity topology (`DispatchActivity` / [`LoginActivity`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/activities/LoginActivity.java) / [`ConnectActivity`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/activities/connect/ConnectActivity.java) / [`StandardHomeActivity`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/activities/StandardHomeActivity.java)) and flag-based back handling ([`ConnectActivity.appLaunchedFromConnect`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/activities/connect/ConnectActivity.java#L54) + `finishAffinity`, `FLAG_ACTIVITY_CLEAR_TOP`, `FLAG_ACTIVITY_REORDER_TO_FRONT`), this produces the navigation defects catalogued in the [CCCT-2441 edge-cases doc](https://docs.google.com/document/d/1jiVEbljnR8abPwJnKzqULTEqbAxU_PAt9sRWkPjTB9k/edit?usp=sharing) and [PR #3765](https://github.com/dimagi/commcare-android/pull/3765): back loops, stale screens, stack growth, and inconsistent exit behavior.

Because start-of-app routing may change what the ~200,000 traditional CommCare users see at cold start, the routing model requires **CommCare-division sign-off**, not just Connect/Product approval.

## Goals, non-goals, constraints

**Goals**
- Route each user persona to the correct landing surface at startup; skip login/setup when a session is already active.
- One consistent, temporally-correct back model with no hidden state flags, covering Intro, the drawer top-level sections, and the app-home surfaces.
- Preserve the seamless silent app launch (no `LoginActivity` / `SeatAppActivity` flashing).

**Non-goals**
- Rebuilding the CommCare app runtime (`StandardHomeActivity` + form entry) into a navigation graph.
- The internal composition of the new `OpportunityHomeActivity` (owned by [CCCT-2522](https://dimagi.atlassian.net/browse/CCCT-2522) / [#3776](https://github.com/dimagi/commcare-android/pull/3776)).
- The inline-silent-login mechanics themselves (parallel in-flight work; this spec consumes their completion hand-off).

**Constraints**
- Protect traditional CommCare users; their cold-start behavior may only change behind the redesign flag and after division sign-off.
- Keep the session alive on back-navigation: periodic tasks (sync, heartbeat, app-update) and the ~24h auto-logout depend on it; re-tapping a launched app resumes quickly.
- No duplicate Home instances left in the back stack.
- Ship dark to `master` behind the redesign feature flag, as with other redesign components.

## Architecture: two-tier shell + startup router

**North-star.** Two tiers with one clean seam:

- **Tier 1 — the Shell.** A navigation-host, drawer-hosting surface owning Intro, the CommCare Apps list, Opportunity List, Opportunity Home, and the drawer top-level sections. "Back" within Tier 1 is pure Navigation-component semantics (start destination + top-level destinations + temporal back). Today the Shell is realized as a family of `BaseDrawerActivity`-rooted activities (`ConnectActivity` is the seed; `OpportunityHomeActivity` per [#3776](https://github.com/dimagi/commcare-android/pull/3776)); the north-star collapses them into a single `NavHost`.
- **Tier 2 — the CommCare app runtime.** `StandardHomeActivity` + form entry, launched as a **separate activity, for-result**, unchanged internally.
- **The seam.** The Shell is the for-result parent Tier 2 returns to (the CCCT-2441 edge-cases doc's [Option D](https://docs.google.com/document/d/1jiVEbljnR8abPwJnKzqULTEqbAxU_PAt9sRWkPjTB9k/edit?usp=sharing), generalized to also own Intro and the drawer). Backing out of Tier 2 reveals the Shell surface that launched it; logout finishing Tier 2 reveals the Shell beneath with the session closed.

**The Connect path collapses Tier 2 in place.** Per [#3776](https://github.com/dimagi/commcare-android/pull/3776), `OpportunityHomeActivity` is itself a drawer-host surface that **attaches an app session in place** (no separate home activity, no for-result hop). So the for-result seam describes the *traditional/PersonalID* path; the *Connect* path gains app-home capability within the Opp Home surface itself. #3776's `HomeActivityCoordinator` + delegates + `attachSession`/`detachSession` decouple app-home behavior from the session gate and from any one activity — which is precisely the mechanism that makes the single-`NavHost` north-star reachable later. This work is a stepping stone toward the Shell, not a detour from it.

**The startup router** is a run-once resolver (not a resident activity) that replaces `DispatchActivity`'s routing role. `DispatchActivity` remains the manifest launcher in the interim (it still owns DB-bad-state, recovery, and external/session-endpoint launches), but the landing decision moves into the resolver and stops re-evaluating in `onResume()` for redesign paths.

## Start-of-app routing model

**Discriminator (authoritative): how the current session was established.** A session established via PersonalID auth on an opportunity-linked app → Connect experience → resume Opp Home; a session established via manual login, or PersonalID auth on a non-opportunity-linked app → resume Standard Home. At implementation, verify the live-session signal agrees with `evaluateAppState`.

**Inputs** (all already available):

| Input | Source |
|---|---|
| Active user session alive? (~24h) | [`CommCareApplication.getSession().isActive()`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/CommCareApplication.java#L969) |
| Seated app linkage/auth state | [`PersonalIdManager.evaluateAppState`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/connect/PersonalIdManager.java#L520) / [`ConnectJobHelper.getJobForSeatedApp`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/connect/ConnectJobHelper.kt#L23) |
| PersonalID status | [`PersonalIdManager.isloggedIn()`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/connect/PersonalIdManager.java#L149) |
| Connect access + opportunities | [`ConnectUserDatabaseUtil.hasConnectAccess()`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/connect/database/ConnectUserDatabaseUtil.java#L44) + opportunity records |
| Installed apps present | [`MultipleAppsUtil.usableAppsPresent()`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/utils/MultipleAppsUtil.java#L50) || Last accessed opportunity | **new persistence — see below** |
| Last session context | **new persistence — see below** |

**Precedence (first match wins):**

1. **Active session alive → resume its home, no login shown.** Session on an opportunity-linked app → `OpportunityHomeActivity` for that opportunity; otherwise → `StandardHomeActivity` / `RootMenuHomeActivity`.
2. **No active session → resolve by configuration** (table below). Entering a Connect/PersonalID surface may require a **PersonalID unlock** (biometric/PIN via [`PersonalIdUnlocker`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/personalId/PersonalIdUnlocker.kt)); on cancel or failure the prompt dismisses and the underlying screen is left unchanged (both route through the same `connectActivityComplete(false)` path today), rather than falling back to Intro. Traditional CommCare users never hit this gate — they have no PersonalID. The router only *lands* on `OpportunityHomeActivity`; [#3776](https://github.com/dimagi/commcare-android/pull/3776)'s controller performs the in-place silent app-login on resume.

**Scenario → landing.** The design doc's 10 startup scenarios fold to five once login-sheet state is out of scope — the variants that differed only by password-field visibility collapse:

| Config | Landing |
|---|---|
| Nothing configured / first launch | Intro |
| New PersonalID user, no opportunities or apps | Intro (app-install section) |
| Has installed app(s), not a Connect user | CommCare Apps list |
| Connect user | Opp Home of the **last-accessed opportunity**; if none accessed, its Opp Home when it's the sole opportunity, else Opp List |
| Both Connect and non-Connect usage | Route by **last session context**: Connect → Opp Home; otherwise → CommCare Apps list |

Under the redesign, the standalone Login page is replaced by a **bottom-sheet overlay on the CommCare Apps list** (a parallel track — see Dependencies), so the CommCare Apps list is where login happens. The bottom sheet's internal state — including password-field visibility — is out of scope, handled by that redesign.

**New persistence (owned by this spec; punted from [#3776](https://github.com/dimagi/commcare-android/pull/3776)):**

- **Last-accessed opportunity** — the `jobUUID` of the Opp Home most recently opened, stored per PersonalID user, written on entering `OpportunityHomeActivity`. "Accessed" = *opened Opp Home at least once* (not merely invited), so a Connect user with only un-opened invites routes to the Opp List — unless it is their sole opportunity, in which case they land on its Opp Home.
- **Last session context** — a marker (`manual` / `PersonalId-non-opportunity` / `PersonalId-on-opportunity-X`) that lets the **"Both Connect and non-Connect usage"** scenario (above) route deterministically when no session is live, instead of guessing. This replaces the design doc's vague "last track used."
- **Terminal-state acknowledgment** — a per-opportunity flag recording that the user has already seen an opportunity's terminal state since it transitioned. **Rule:** if the last-accessed opportunity is now terminal — expired, cancelled, **or completed** — land on its Opp Home **once** (the first open since the transition, so the user sees the ended/completed state), set the flag, and on later launches fall back to the Opp List. This is based on the *locally-known* status: if connectivity hasn't yet surfaced the transition, routing proceeds to Opp Home as normal and the terminal state appears after the next sync.

These are stored in a new dedicated shared-preferences store.

## App-wide back-navigation model

**Governing principle: Back is temporal and flag-free.** Back reverses the last navigation step and is never conditioned on a hidden state flag (`appLaunchedFromConnect` is removed in the north-star). Five rules make it deterministic:

1. **Start destination per host.** Back from the start destination finishes the host, revealing whatever launched it — or exiting if the host is the task root. This is the correct-stack replacement for the exit flag.
2. **Top-level sections don't stack.** Navigating among the drawer's main sections pops to the start destination; Back from a section → start destination.
3. **Detail screens stack temporally.** Pushed screens return to their opener on Back.
4. **Login validity.** The Intro entry and the app-login bottom sheet are valid only with no active session for the target app; they are never revealed beneath a live or just-closed session. Session transitions (logout, expiry) route **explicitly** — they are not "Back."
5. **Notification deep links synthesize a logical parent stack** so Back from a deep-linked destination reaches its parent, not the app exit.

**Dynamic start destination (per persona, set once by the router):**

| Persona                                | Start destination (Back exits from here) | Its app-home child (synthesized beneath at startup) |
|----------------------------------------|---|-----------------------------------------------------|
| Connect user                           | Opp List | Opp Home (in-place session, [#3776](https://github.com/dimagi/commcare-android/pull/3776))              |
| PersonalID / traditional CommCare user | CommCare Apps list | Standard Home (separate activity, for-result)       |

Each persona exits only from its own start destination and never Backs through a surface meant for another persona (a traditional user never sees a Connect page). Intro is *not* a persistent back-stack member for configured users — it is the unconfigured cold-start root and the explicit **forget-PersonalID** reset target.

**Hierarchy:**

| Tier | Destinations | Back behavior |
|---|---|---|
| Universal entry | Intro | Cold-start root for unconfigured users; forget-PersonalID target. Back → exit. |
| Top-level sections (drawer sections shown only to PersonalID/Connect users) | Opportunities (Opp List), CommCare Apps, Work History, Messaging, Profile, Notifications | Back → the persona's start destination; Back from the start destination → exit |
| App-home children | Opp Home (under Opp List); Standard Home (under CommCare Apps list) | Back → parent list; logout ends the app session → parent list |
| Detail (stacked) | Settings, About | Back → opener |
| Overlays | App-login bottom sheet | Dismiss → CommCare Apps list |

The sidebar is available to all users, but its **contents depend on the user**. A traditional CommCare user (no PersonalID) sees only a **Register PersonalID** option and an **About CommCare** button — none of the PersonalID top-level sections, and no tap-to-signup gating. Those sections appear only once the user has registered a PersonalID account; consequently a traditional CommCare user never encounters the PersonalID-unlock gate unless they register. (A traditional user still lands on the CommCare Apps list as their home screen — it's the drawer full of PersonalID sections they don't see.) The sidebar also opens from the PersonalID Profile page (hamburger), not via a back button.

**How this dissolves the old cases-2-vs-3 inconsistency, flag-free.** Opp List is the Connect start destination. Back from it finishes the shell: if the shell was cold-launched (Connect startup, shell is task root) → the app exits; if the shell was opened atop a Standard Home → finishing it reveals that Standard Home. Same rule, different stack — no `appLaunchedFromConnect` flag.

**Logout vs forget-PersonalID (distinct concepts):**
- **App-session logout** (all personas) ends only that app session; the PersonalID account stays registered. It routes to the launcher's parent list: traditional/PersonalID → CommCare Apps list; Connect → Opp List. For Opp Home, logout must **navigate away** to Opp List as it ends the session, since Opp Home's auto-login would otherwise immediately re-establish it.
- **Forget PersonalID** de-registers the account (re-registration required) and always returns to Intro (via [`forgetUser()`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/connect/PersonalIdManager.java#L175) → `DispatchActivity` with `CLEAR_TASK`). It lives in Profile and is out of scope beyond noting the distinction. Note it does **not** currently close an active CommCare app session — it clears PersonalID data only; whether it should is an open question.

## User flows

End-to-end journeys per persona, with Back annotated at each step. These double as the back-navigation regression scenarios (see Testing & rollout). Notation: `→` forward navigation, `⇐` Back. Back exits only from the persona's start destination (CommCare Apps list for traditional/PersonalID; Opp List for Connect); Intro sits beneath only during first-run onboarding, not for configured users.

1. **Traditional CommCare, first run (unconfigured).** Intro → *download app* → CommCare Apps list → *tap app* → login bottom sheet → *sign in* → Standard Home.
   Back: Standard Home ⇐ CommCare Apps list ⇐ Intro ⇐ exit. *Exception:* while the login sheet is open, Back dismisses it → CommCare Apps list.
2. **Traditional CommCare / PersonalID, returning with active session.** Cold start → Standard Home (resumed; CommCare Apps list synthesized beneath), no Intro or login shown.
   Back: Standard Home ⇐ CommCare Apps list ⇐ exit.
3. **PersonalID, app switch (edge cases 4–5).** CommCare Apps list → *tap app A* → Standard Home A → drawer → CommCare Apps → *tap app B* → (session A closed) Standard Home B; stale Home A cleared.
   Back: Standard Home B ⇐ CommCare Apps list ⇐ exit. Relaunching a still-running app reuses its Home (no duplicate).
4. **Connect, cold-start, sole opportunity.** Cold start → PersonalID-unlock → Opp Home (the sole opportunity; Opp List synthesized beneath).
   Back: Opp Home ⇐ Opp List ⇐ exit.
5. **Connect, cold-start, multiple opportunities none accessed.** Cold start → PersonalID-unlock → Opp List → *tap opp* → Opp Home.
   Back: Opp Home ⇐ Opp List ⇐ exit.
6. **Connect, returning to last-accessed opportunity.** Active session → Opp Home (resumed in place); or no session → PersonalID-unlock → Opp Home (last-accessed; Opp List synthesized).
   Back: Opp Home ⇐ Opp List ⇐ exit.
7. **Connect, launch an opportunity from the list.** Opp List → *tap opp* → Opp Home (app session attaches in place; the redesign drops the separate "View Job Status"/"Resume" buttons, so Opp Home is a single surface with no toggle).
   Back: Opp Home ⇐ Opp List ⇐ exit.
8. **Notification deep-link into an opportunity — cold start (edge case 8).** Notification → deep link (app not already running) → PersonalID-unlock → Opp Home (Opp List synthesized beneath).
   Back: Opp Home ⇐ Opp List ⇐ exit. *Exception vs. today:* does not exit on the first Back.
9. **Notification tap while the app is already running (mid-session).** The deep link navigates within the existing task to the target Opp Home; the live back stack is preserved — no parent is synthesized, because a real one already exists.
   Back: Opp Home ⇐ the screen the user was on when they tapped the notification.
10. **Logout & forget-PersonalID.** Traditional/PersonalID: Standard Home → *logout* → (session ends) CommCare Apps list. Connect: Opp Home → *logout* → (session ends, navigates away) Opp List — leaving Opp Home prevents auto-login from re-firing. Forget-PersonalID (distinct): Profile → *forget* → Intro (re-register).

## Edge-case reconciliation

The 8 CCCT-2441 edge cases, re-derived against this model:

| # | Original scenario | Behavior under this model | Status |
|---|---|---|---|
| 1 | Launch opp app from Opp List → Home; back → Opp List | Opp Home is Opp List's child; session attaches in place. Back → Opp List. | Preserved, cleaner |
| 2 | Back from Opp List after a launch → exit | Opp List is the Connect start destination → Back exits. No flag, no stale-login risk (no Login page), no Dispatch re-dispatch (router runs once). | Preserved, flag-free |
| 3 | Browse Opp List, no launch → back → "whatever opened Connect" | **Changed.** Only Connect users see Opp List content; for them it is the start destination → Back exits. The old "return to the launching app" was an artifact of Opp List sitting atop a Standard Home. | Changed — resolves the case-2/3 inconsistency |
| 4 | PersonalID user relaunches **same** app from a list → resume, no duplicate | Re-homed to the CommCare Apps list (a non-Connect user launches apps there, not Opp List). Relaunching a running app reuses its Standard Home. | Preserved, re-homed |
| 5 | PersonalID user launches a **different** app → new Home, stale Home unreachable | Same re-homing; switching apps closes the prior session and launches the new Standard Home as the list's child; stale Home cleared. | Preserved, re-homed |
| 6 | Toggle Overview ↔ Home repeatedly → stack must not grow | **Eliminated.** The redesign removes the "View Job Status"/"Resume" buttons, so the Overview↔Home toggle no longer exists — Opp Home is a single surface whose state changes in place. | Eliminated |
| 7 | Log out from Home | Traditional/PersonalID → CommCare Apps list (replaces the now-removed Login page); Connect → Opp List (navigates away so auto-login can't re-fire). Ends only the app session. | Preserved, updated destination |
| 8 | Notification deep-link into Overview → Resume → back | **Deliberate override.** Synthesize Opp List beneath the deep-linked Opp Home → Back → Opp List → exit, instead of exiting immediately. Consistent with all other Opp Home entries. PersonalID-unlock gate applies first. | Best-practice override |

Root causes the edge-cases doc fought (`CLEAR_TOP` collapsing the stack, Dispatch re-dispatch loops, Home restacking) disappear under a run-once router + start-destination-exit + in-place Opp Home. Solution A's mechanisms (`finishAffinity`, the instance flag, pop hacks) survive only in the interim.

## Technical gap analysis & interim plan

| Gap (today) | Interim | North-star |
|---|---|---|
| No run-once router; `dispatch()` re-evaluates on resume; no route-to-Connect branch | Router resolver invoked once at cold start; `DispatchActivity` stays the launcher for DB/recovery/external launches but the landing decision moves out and stops re-dispatching on resume | Resolver sets the Shell `NavHost` start destination; Dispatch routing retires |
| No last-accessed-opportunity / last-session-context persistence | Persist both per PersonalID user; resolver reads them | Same, folded into the resolver |
| Opp Home not a first-class landing (`ConnectActivity` starts at the jobs list) | Router launches `OpportunityHomeActivity` for the last-accessed opp — **depends on #3776** + the inline-login completion hand-off | Opp Home is a Shell destination |
| Flag-based back (`appLaunchedFromConnect` + `finishAffinity` + fragment self-pop + `REORDER_TO_FRONT`) | Keep Solution A, re-pointed at the Shell seam (`ConnectActivity` as for-result parent, Option D) rather than Dispatch coupling | Start-destination-exit + synthesized parents; delete the flag/pop/affinity hacks |
| Multi-activity topology | Grow the Shell incrementally (`ConnectActivity` + the `BaseDrawerActivity` family) | Single `NavHost` Shell |

**Dependencies (prerequisites, done before implementation):**
- CCCT-2522/#3776 (`OpportunityHomeActivity` composition) landed.
- The parallel inline-silent-login completion hand-off (routes login success into the running Opp Home).

**Parallel, not blocking:** the standalone Login page redesign (→ CommCare Apps list + login bottom sheet) does **not** gate the navigation work. The router, back-nav model, and active-session short-circuit ship independently; only the *specific* traditional/PersonalID landing targets and the cases-4/5 re-homing require it. Until it lands, traditional users keep the current login page under the same routing decisions.

## Testing & rollout

- **Router** — unit-test the resolver as a pure function (inputs → landing) across the folded scenarios + the active-session short-circuit + last-accessed-opportunity present/absent/terminal (JVM-testable if inputs are parameters).
- **Back navigation** — Robolectric tests asserting observable back-stack behavior per surface; the 8 edge cases become regression scenarios (pin behavior, not internals). Key assertions: Back from the persona's start destination exits; Opp Home → Opp List; deep-link synthesizes the parent; logout → parent list.
- **E2E/instrumentation** — cold-start → landing for each persona; launch/back/logout on-device (the Android-bound paths #3765 and #3776 note are not unit-covered).
- **Safety net** — rides on #3776's pre-refactor characterization tests for the home activities.

**Rollout** — gated behind a new feature flag in [`PersonalIdFeatureFlagChecker`](https://github.com/dimagi/commcare-android/blob/dc7697645fefd99de4e234be569bd8447fb6e0ba/app/src/org/commcare/personalId/PersonalIdFeatureFlagChecker.kt) (the mechanism used to dark-launch redesign work), shipped dark to `master` and revealed with the redesign. **CommCare-division sign-off on the routing model** before it is enabled for traditional users. Stage: Connect/PersonalID cohort first. The navigation changes for traditional users can roll out independently of the separate login/intro redesign.
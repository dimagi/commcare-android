# Common Edge Cases

A curated list of **non-obvious, codebase-specific traps** that have caused real defects or review
churn on CommCare Android. It is deliberately **not** a general design checklist — it captures only
things a competent engineer wouldn't reliably anticipate on their own, so keep it short: a long
list dilutes the signal.

**When planning or writing a spec for a new feature, skim this list and address any trap the
feature could hit.** These are design/spec-time concerns, not implementation-style advice.

Human-curated: add an entry when a real incident surfaces a trap likely to recur, keep each one
concrete and tied to why it bit us, and prune entries that become obsolete.

## Navigation & task stack

- **Back behavior differs by entry point.** The same screen is often reachable several ways (nav
  drawer / "browse", a forward action like Resume, a notification, a cold app open), and the correct
  back behavior can differ per path. Enumerate the entry points and decide back behavior for each; history-dependent back is a frequent bug source.
- **Activity re-launch loops.** Finishing one Activity while another re-launches the same
  destination (e.g. `DispatchActivity` re-opening an app's Home) produces a back-press loop. Trace
  the whole task stack, not just the visible screen.
- **`FLAG_ACTIVITY_REORDER_TO_FRONT` on a shared Activity class.** Home (`StandardHomeActivity` /
  `RootMenuHomeActivity`) is one class shared across all apps, so an unconditional reorder can
  surface a *different* app's stale Home. Only reorder when you know this app's instance is the one
  in the task. ("No-op if no instance exists" is not true for shared classes.) Verify cross-app task
  scenarios on-device.
- **Pop the back stack on `onStop`, not immediately.** Popping a fragment / back-stack entry while
  the host Activity is still foregrounded briefly shows the popped screen (a visible flash). Defer
  the pop to an `onStop` lifecycle callback.

## Session & authentication

- **Don't close the user session to fix navigation.** Periodic CommCare tasks (sync, heartbeat, app
  update) and the normal ~24h auto-logout depend on a live session, and re-entering a launched app
  must resume instantly. Solve navigation another way.
- **`LoginActivity` is only valid with no active session** (and vice-versa). Never route to a
  no-session screen while a session is live.
- **"Already authenticated" is only knowable _before_ login runs.** Capturing it afterward is a
  timing bug — post-login the session looks authenticated either way, so you can't tell "already"
  from "just now." Compute it up front and thread it through the result; don't recompute downstream.

## Lifecycle & async

- **Async callbacks can arrive after the view is gone.** Guard launch / network callbacks with
  `view != null` / `isAdded` before touching UI.
- **Dialogs / transactions after state is saved throw `IllegalStateException`.** For work that may
  run while backgrounded or being recreated, check `isStateSaved` first (or use
  `commitAllowingStateLoss()` / `showAllowingStateLoss()`).
- **In-memory flags reset on recreation.** A plain field (e.g. a "launched from Connect" flag) is
  lost on process death or Activity recreation. If the behavior must survive, scope the flag to the
  session or persist it in saved instance state.

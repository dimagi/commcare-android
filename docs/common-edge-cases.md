# Common Edge Cases

A planning checklist for new features. **Before finalizing a plan or spec, walk this list and
note how the design handles each category that applies.** Most categories below come from real
defects and review churn on CommCare Android (the Connect login/launch refactor in particular) —
considering them up front is cheaper than discovering them in review or QA.

This is a living document. When a feature surfaces an edge case that isn't here yet and is likely
to recur, add it.

## How to use

For each category, ask "does this feature touch this?" If yes, the spec should state the intended
behavior for the listed scenarios — not leave them implicit.

## Navigation & back stack

- What does the **back button** do from each new screen — return to the previous screen, or exit a
  flow? Is that behavior **consistent regardless of how the user reached the screen**, or
  history-dependent? History-dependent back behavior is a frequent source of confusion.
- Can an **Activity re-launch loop** occur (one Activity finishes while another re-launches the same
  destination)? Trace the full task stack, not just the screen in front of you.
- If you pop a fragment / back-stack entry, **when** does it happen relative to the host's
  visibility? Popping while the host is still foregrounded can cause a visible flash; deferring to a
  lifecycle callback (e.g. `onStop`) avoids it.
- Don't hardcode a destination id where you mean "the start of this nav path" — compare against the
  nav graph's **start destination** so the behavior survives graph changes.

## Activity / task stack & launch flags

- Intent flags like `FLAG_ACTIVITY_REORDER_TO_FRONT` / `CLEAR_TOP` / `SINGLE_TOP` match by
  **Activity class**. If a class is **shared across features/apps** (e.g. CommCare's Home), a flag
  meant to reuse "this" instance can surface a **different** feature's stale instance. Reason about
  which instances can already be in the task.
- "No-op if there's no existing instance" is often **not** strictly true for shared Activity classes.
- Verify cross-app / cross-feature task scenarios **on-device**, not just by reading code.

## Fragment & view lifecycle

- Guard UI work behind a **liveness check** (`view != null` / `isAdded`) — async callbacks can arrive
  after the fragment's view is gone.
- Showing a dialog or committing a transaction **after state is saved** throws
  `IllegalStateException`. Check `isStateSaved` (or use `...AllowingStateLoss`) for work that may run
  while backgrounded or being recreated.
- Don't let a controller own a fragment's lifecycle decisions implicitly — make the hand-off
  (success/failure callbacks) explicit.

## Session & authentication lifecycle

- **Don't close the user session to fix navigation.** Periodic CommCare tasks (sync, heartbeat, app
  update) depend on a live session, and the normal ~24h auto-logout must remain the only logout.
  Re-entering a launched app should resume instantly.
- Distinguish **"already authenticated"** from **"just authenticated this moment"** — the two often
  need different handling (e.g. reuse an existing screen vs. start a fresh one).
- A screen that's only valid with **no active session** (e.g. `LoginActivity`) must never be routed
  to while a session is live, and vice-versa.

## State derivation & timing

- Capture derived state **at the moment it's knowable**, not later. Example: "was the user already
  logged in?" is only answerable **before** login runs — computing it afterward is a timing bug
  because the session now looks logged-in either way. Thread such values through the result rather
  than recomputing them downstream.

## Entry points & deep links

- Enumerate **every way** the feature can be entered (direct tap, nav drawer, notification / FCM,
  fresh app open, deep link, redirect after login). Each may carry different state and expect
  different back behavior.
- Confirm behavior for the **cold-start** entry (process freshly created) separately from the
  warm in-app entry.

## Async callbacks & cancellation

- What happens if the user **leaves** before an async operation completes? Ensure callbacks no-op
  safely (see liveness checks above).
- Side effects that **must** run even on cancellation (analytics, cleanup) should be in a
  non-cancellable scope.

## Errors, retries & failure UX

- Define the behavior for **transient failure** (retry prompt) vs. **terminal failure** (give up with
  a clear message). Cap retries and escalate at the cap rather than looping.
- Distinguish recoverable failures (offer a path back) from unrecoverable ones (dismiss-only error).

## Configuration changes & process death

- Will the feature survive **rotation** and **process death / low-memory recreation**? Restore the
  in-flight state, or guard against acting on stale references.
- In-memory flags reset on recreation — decide whether the flag needs to be session- or
  bundle-scoped instead.

## Connectivity & device state

- CommCare runs in **low-connectivity** settings: handle slow/failed network, partial sync, and
  offline entry explicitly.
- Consider low storage, low memory, and interrupted background work.

## Localization

- New user-facing strings must be added to **all supported locale files**, not just the default.

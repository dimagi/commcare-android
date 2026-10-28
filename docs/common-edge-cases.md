# Common Edge Cases

A checklist of dimensions that feature specs commonly leave implicit — the omissions that later
surface as defects or review churn during implementation. **When planning or writing a spec
(especially with AI), skim this list and make each relevant dimension explicit in the spec.**

Kept deliberately high-level: the right answer differs per feature, so the goal is to prompt the
*question*, not prescribe the answer. Add a dimension when a real gap recurs, and keep the list
short and concise so it stays scannable.

## Consider specifying

- **Back navigation & entry points.** Enumerate every way the feature is entered (direct tap, nav
  drawer, notification / deep link, cold start). For each new screen, state what Back does — and
  whether that differs by how the screen was reached. Don't leave back behavior implicit.
- **Session & authentication.** State how the feature interacts with the user session and auth
  state: whether it requires an active session, starts or ends one, and how it should behave across
  the normal session lifetime.
- **Lifecycle & retained state.** State what must survive configuration changes (rotation) and
  process death / recreation, and where that state is held.
- **Form factors.** State which form factors and orientations are in scope (phone, tablet), or that
  it is single-form-factor by design.
- **Failure & connectivity.** State the intended behavior on error, retry, and low/no connectivity
  — CommCare runs in low-connectivity settings.

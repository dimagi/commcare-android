# Form Exit Warning on Push Notification Tap

**Ticket:** CCCT-2396
**Date:** 2026-05-11
**Status:** Design approved

## Problem

When a user is in `FormEntryActivity` and taps a push notification that
would navigate them elsewhere in the app, today the notification's
`PendingIntent` (built with `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK`
in `FirebaseMessagingUtil.buildNotification`) tears down the entire task
including `FormEntryActivity`. The user loses any unsaved form progress
without a prompt — unlike the back-button and toolbar-home paths, which
both route through `triggerUserQuitInput()` and surface the existing
quit-form warning dialog.

## Goal

Insert the same warning dialog into the navigation flow when a tap on a
navigation push notification would otherwise exit a partially completed
form. The user gets the same three choices they already get from the
back button: stay in the form, discard changes, or save as incomplete.
After Save or Discard, the notification's original navigation target is
opened. After Stay, the notification's navigation is dropped.

## Scope

In scope:

- All push notifications that navigate to a screen — every payload that
  currently flows through `FirebaseMessagingUtil.buildNotification`.

Out of scope:

- SYNC-only payloads. They never call `buildNotification` (see
  `FirebaseMessagingUtil.handleNotification:258`) and don't navigate.
- Untapped notifications. We only intercept on tap; nothing changes
  about posting, suppressing, or queueing notifications while a form is
  open.
- Changes to the existing quit dialog used by back-button / toolbar
  paths. Title, choices, and analytics for those paths stay as they are.

## Design

### Components

1. **`PushNotificationLaunchActivity`** *(new, Kotlin)*

   - `android:theme="@android:style/Theme.NoDisplay"`
   - `android:taskAffinity=""`
   - `android:excludeFromRecents="true"`
   - `android:launchMode="singleInstance"`
   - No UI. Single responsibility: at tap time, read the wrapped target
     intent from its own extras, check
     `FormEntryActivity.isFormEntryInProgress()`, dispatch accordingly,
     then `finish()`.

2. **New entry point on `FormEntryActivity`:**
   `triggerUserQuitInputForExternalNav(Intent pendingNav)`

   Sibling of the existing `triggerUserQuitInput()` at
   `FormEntryActivity.java:1223`. Same `formHasLoaded` and
   `isFormReadOnly` short-circuits, but each branch additionally
   dispatches `pendingNav` on the way out. On the normal path it calls
   the new overload
   `FormEntryDialogs.createQuitDialog(activity, isIncompleteEnabled, pendingNav)`.

3. **Overloaded `FormEntryDialogs.createQuitDialog`**

   New signature accepting a non-null `pendingNav` Intent. Reuses the
   existing title (`R.string.quit_form_title`) and choice strings
   (`do_not_exit`, `do_not_save`, `keep_changes`). The Save and Discard
   listeners run their existing logic, then start the pending intent.
   Stay just dismisses; `pendingNav` is dropped.

### Edits to existing files

- **`FirebaseMessagingUtil.buildNotification` (line 549).** The
  `PendingIntent.getActivity` target becomes
  `PushNotificationLaunchActivity`. The original navigation Intent is
  wrapped as a Parcelable extra
  (`PushNotificationLaunchActivity.EXTRA_WRAPPED_NAV_INTENT`).
  CLEAR_TASK|NEW_TASK flags are no longer applied to the PendingIntent's
  intent — those are applied by the launch activity to the wrapped
  intent at dispatch time when the form is *not* active.

- **`AndroidManifest.xml`.** Register `PushNotificationLaunchActivity`
  with no intent filters (it's only ever started via PendingIntent).

- **`FormEntryActivity`.** Already declared
  `android:launchMode="singleTop"` in the manifest
  (`AndroidManifest.xml:358`). Override `onNewIntent(Intent)` to
  detect `EXTRA_PENDING_NAV_INTENT` and route to
  `triggerUserQuitInputForExternalNav`.

- **`AnalyticsParamValue`.** Add a new constant
  `PUSH_NOTIFICATION_TAP` to label the source on
  `FirebaseAnalyticsUtil.reportFormQuitAttempt`, alongside the existing
  `BACK_BUTTON_PRESS` and `NAV_BUTTON_PRESS`.

### Data flow

```
[FCM payload arrives]
     │
     ▼
CommCareFirebaseMessagingService.onMessageReceived
     │
     ▼
FirebaseMessagingUtil.handleNotification
     │  (builds the real target Intent — DispatchActivity or ConnectActivity)
     ▼
FirebaseMessagingUtil.buildNotification
     │  Wraps the real Intent as an extra on a new Intent targeting
     │  PushNotificationLaunchActivity.
     │  PendingIntent.getActivity(...) is built against THAT intent.
     ▼
[Notification posted to tray]
     │
     ▼  user taps
PushNotificationLaunchActivity.onCreate
     │
     ├── FormEntryActivity.isFormEntryInProgress() == false
     │     └─► startActivity(wrappedIntent) with original
     │          FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK
     │          → finish() the launch activity → behaves exactly like today
     │
     └── FormEntryActivity.isFormEntryInProgress() == true
           └─► Intent reroute = new Intent(this, FormEntryActivity.class)
                 .addFlags(FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP)
                 .putExtra(EXTRA_PENDING_NAV_INTENT, wrappedIntent)
               startActivity(reroute) → finish() the launch activity

FormEntryActivity.onNewIntent(reroute)
     │
     ▼  sees EXTRA_PENDING_NAV_INTENT
triggerUserQuitInputForExternalNav(pendingNav)
     │
     │  FirebaseAnalyticsUtil.reportFormQuitAttempt(
     │     PUSH_NOTIFICATION_TAP, getCurrentFormXmlnsFailSafe())
     │
     ├── !formHasLoaded()        → startActivity(pendingNav); finish()
     ├── isFormReadOnly()        → finishReturnInstance(false);
     │                              startActivity(pendingNav)
     │
     └── normal case → FormEntryDialogs.createQuitDialog(
                          activity, mIncompleteEnabled, pendingNav)
              │
              ├── Stay        → dialog.dismiss(); pendingNav dropped
              ├── Discard     → discardChangesAndExit();
              │                  startActivity(pendingNav)
              └── Save        → stash pendingNav on activity;
                                 saveFormToDisk(EXIT);
                                 consume stash in savingComplete after
                                 successful EXIT-flavored save and
                                 startActivity(pendingNav)
```

### Async save handling

`SaveToDiskTask` is async, so the navigation cannot happen synchronously
in the Save listener. The implementation stores `pendingNav` in a
transient field on `FormEntryActivity` set when the dialog's Save
listener fires; the existing `savingComplete` callback consumes the
field after a successful EXIT-flavored save and starts the pending
intent. If the save fails, the existing save-failure UI runs and the
pending intent is **not** started — user stays in the form.

### Persistence across process death

`pendingNav` is persisted via `onSaveInstanceState` (alongside the
existing state at `FormEntryActivity.java:320`) and restored in
`onCreate` so a config change or low-memory kill mid-dialog does not
silently drop the deferred navigation. On restore, if `pendingNav` is
present but no dialog is visible, the dialog is re-shown from
`onResume`.

## Edge cases

| Case | Handling |
|------|----------|
| Tap while form is loading | `formHasLoaded()` false → start pending intent, finish form. Mirrors current `triggerUserQuitInput`. |
| Tap while form is read-only | `finishReturnInstance(false)` then start pending intent. Mirrors current. |
| Tap while a SaveToDiskTask is already in flight | `onNewIntent` sees `mSaveToDiskTask != null` → drop the new pending intent (treated as Stay). User can re-tap the notification. |
| Tap while quit dialog is already showing | Drop the new pending intent. Last-shown dialog wins. |
| Multiple notification taps in quick succession (form active, no dialog yet) | Last write wins on `pendingNav`. The most recently tapped notification is the one that gets navigated to. |
| Process death mid-dialog | `pendingNav` restored from `savedInstanceState`. Dialog re-shown in `onResume`. |
| Wrapped intent's target activity is missing | `startActivity` throws `ActivityNotFoundException`. Caught and logged via `Logger.exception`; user remains in their current screen. Same pattern as `FirebaseMessagingUtil.getIntentForPNIfAny:638`. |
| SYNC-only payload | Untouched — never goes through `buildNotification`. |
| Notification posted before form was opened, tapped after | `isFormEntryInProgress()` checked at tap time, so dialog still appears. |
| Notification posted during form, user exits form normally, then taps notification | `isFormEntryInProgress()` false at tap time → normal navigation. |

## Analytics

Single new constant `AnalyticsParamValue.PUSH_NOTIFICATION_TAP`. Reported
from `triggerUserQuitInputForExternalNav` via
`FirebaseAnalyticsUtil.reportFormQuitAttempt` before the dialog appears,
matching the existing pattern at `FormEntryActivity.java:626` and
`:1256`. No per-choice analytics added; no other analytics events
changed.

## Strings

No new string resources. The dialog reuses `quit_form_title`,
`do_not_exit`, `do_not_save`, and `keep_changes`.

## Testing

**Robolectric / unit tests**

- `PushNotificationLaunchActivity`: form-active path forwards a
  singleTop intent to `FormEntryActivity` with
  `EXTRA_PENDING_NAV_INTENT`; form-inactive path starts the wrapped
  intent with `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK`.
- `FormEntryDialogs.createQuitDialog` new overload: each of the three
  click listeners performs the documented action. Use a mock
  `FormEntryActivity` and assert (a) `discardChangesAndExit` and
  `startActivity(pendingNav)` for Discard, (b) `pendingNav` stashed and
  `saveFormToDisk(EXIT)` invoked for Save, (c) only `dialog.dismiss`
  for Stay.
- `FormEntryActivity.onNewIntent` routes to
  `triggerUserQuitInputForExternalNav` only when
  `EXTRA_PENDING_NAV_INTENT` is present; respects the loaded and
  read-only short-circuits.
- `FirebaseMessagingUtil.buildNotification`: the constructed
  PendingIntent's wrapped intent targets
  `PushNotificationLaunchActivity` and preserves the original target as
  `EXTRA_WRAPPED_NAV_INTENT`.

**Instrumentation**

- End-to-end: launch a form, fire a stubbed FCM payload that builds a
  navigation notification, tap it, verify the dialog appears. Then
  verify each of the three choices produces the expected resulting
  screen (form on Stay, notification target on Discard and Save) and
  that on Save the form record was persisted as incomplete before
  navigation.

## Risks

- **Manifest changes affect cold-start surfaces.** Adding a new activity
  is low-risk. `FormEntryActivity` is already `singleTop`, so no launch
  mode change is needed; the new behavior depends only on the
  `onNewIntent` override.
- **Parcelable Intent-in-Intent.** Putting an Intent as an extra is
  supported but unusual; some payloads (e.g. very large bundles) could
  push the Binder transaction size. The existing payloads are small
  metadata bundles, so this is not expected to be an issue, but should
  be exercised in the instrumentation test.
- **`pendingNav` Intent flags.** When the launch activity dispatches the
  wrapped intent in the form-active reroute, it does *not* re-apply
  `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK` — those would
  defeat the purpose. The post-dialog `startActivity(pendingNav)` calls
  apply whatever flags the wrapped intent itself was built with. We
  must verify the wrapped intents (DispatchActivity, ConnectActivity)
  behave correctly when started from inside the existing task without
  CLEAR_TASK.

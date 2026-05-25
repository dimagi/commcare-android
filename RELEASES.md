<!--
This file is meant as an easy way for us to collate notes and change logs across releases. 
-->

## CommCare 2.63.1

### Release Notes

<!--
These are published publicly on Playstore, Github Releases and CommCare Forums
-->

#### What's New


#### Important Bug Fixes

- Fix bug causing form widgets to lose focus when a Combobox dropdown on the same screen is re-validated.

#### Internal Release Notes
<!--
Release notes that are not applicable for wider CommCare users but only for specific projects.
These notes are only published internally in [CommCare Change log wiki](https://dimagi.atlassian.net/wiki/spaces/internal/pages/2145058874/CommCare+Mobile+Changelog)
along with the public release notes above
-->


## CommCare 2.63

### Release Notes

<!--
These are published publically on Playstore, Github Releases and CommCare Forums
-->

#### What's New
- Offline status shown on refreshable Connect pages when applicable
- Forms now allow a maximum of 50 attachments. To add another after the limit is reached, users will need to 
remove an existing one first

- [Relearn Tasking] Added relearn task notification UI to Connect opportunity cards
- [Relearn Tasking] Implements a new notification when a relearn task is assigned to a user
- [Work Area Assignment] Implements a new notification when a new work area is assigned to a user
- [6-box Backup Codes] Using 6-box numeric inputs to collect backup codes from the user

#### Important Bug Fixes
- Fixed a crash triggered during combobox item selection when the dropdown list had already been dismissed

#### Internal Release Notes

<!--
Release notes that are not applicable for wider CommCare users but only for a specific projects. 
These notes are only published internally in [CommCare Change log wiki](https://dimagi.atlassian.net/wiki/spaces/internal/pages/2145058874/CommCare+Mobile+Changelog)
along with the public release notes above
-->

- Session endpoint navigation from Connect notifications: clicking a notification with a `session_endpoint_id` now navigates the user directly to the specified CommCare session endpoint (after a sync if required), instead of opening the Connect activity.
- Tapping a navigation push notification while a form is open now prompts the user with the standard quit-form dialog (STAY IN FORM / EXIT WITHOUT SAVING / SAVE INCOMPLETE) before navigating away, preventing accidental loss of unsaved form data


### QA Notes

<!--
These are for internal use and for us to keep track of important notes that
we would like to communicate to QA as part of the release testing
-->

- **Task and Work Area Assignment Notifications (Connect):**
    - On clicking, Notification should take user to the relevant CommCare App Home page and auto-login and auto-syncs the user with a blocking dialog. 
    - Click the notification while logged out
    - Click the notification while the app is backgrounded
    - Verify that notifications redirect work as expected from various places in the app - Opp Screen, App Home, Login Screen, Form Entry etc and back navigation works correctly after the notification redirect
    - Verify no regression on existing Connect notification types (payments, messaging, delivery/learn progress).

- Verify that the existing opportunity card UI is unchanged when there are no relearn tasks.
- Verify that the opportunity card updates as expected when there are either pending relearn tasks or completed relearn tasks.
- Verify 6-box input functionality in Backup Code page, including:
  - Showing one or two of the controls depending on recovery/registration mode (respectively)
  - Handling password-style visibility with associated "eye" toggle
  - Verifying matching codes (or error message) in registration mode
  - Text cursor functionality (i.e. backspacing, clicking an earlier box to jump back)

- Test the new offline status indicator at the top of refreshable Connect pages (Connect Home, Learning Progress, Delivery Progress). Verify that the error message appears when entering these pages while offline, and that it disappears once the device comes back online.
- Verify that the combobox widget is working as expected when selecting an item that is used to filter another combobox widget and also determines the visibility of some other unrelated question whose relevance condition depends on the selection.
- Open the Connect notification history screen (the list of push notifications) and verify the screen title reads "Notifications" (or the localized PersonalID notification title) and that no secondary breadcrumb/title strip is shown above or below it. Confirm the back arrow and the cloud-sync menu action both still work, and that opening a notification still routes correctly.

- **Form exit warning on push notification tap:**
  - **Editable form, dialog appears:** Open any Connect app and enter a form. Trigger a navigation push notification (e.g. a Connect message, payment notification, or any `ccc_*` notification that opens a screen). Tap the notification.
    - Verify a "Exit Form?" dialog appears with three choices: "STAY IN FORM", "EXIT WITHOUT SAVING", and "SAVE INCOMPLETE" (the same dialog the back button shows).
    - Tap "STAY IN FORM" → dialog dismisses, the user stays in the form, no navigation occurs.
    - Repeat the scenario, this time tapping "EXIT WITHOUT SAVING" → the form is dismissed without saving and the notification's target screen opens.
    - Repeat again, tapping "SAVE INCOMPLETE" → the form is saved as incomplete (verify it appears in the Saved Forms list on the App Home), and after the save completes the notification's target screen opens.
  - **No form open, no dialog (regression check):** Tap the same kinds of notifications from the home screen, login screen, and other non-form screens. Verify the notification opens its target screen directly, with no dialog — identical to today's behavior.
  - **Read-only form:** Open a previously completed form in review mode and tap a navigation notification. Verify no dialog appears, the read-only view closes, and the notification's target screen opens.
  - **No regression on existing notification types:** Re-run the regression for Connect messaging notifications, payment notifications, learn/delivery progress notifications, opportunity summary notifications, and session-endpoint deep links. All should behave the same as before when no form is open, and should now show the dialog when a form is open.
  - **SYNC payloads unaffected:** Sync-action notifications (which never navigate) should continue to behave as today with no dialog interaction.
  - **Analytics:** when the dialog appears from a notification tap, Firebase logs a `form_exit_attempt` event with `method=push_notification_tap` (alongside the existing `back_button_press` and `nav_button_press` source labels).

## CommCare 2.62


### Release Notes

#### What's New

- Redesigned Connect landing page for a smoother experience
- Hausa language support added in Connect and PersonalID 
- Boundary overlays now available on case list maps 
- Unsubscribe from Connect messaging channels 
- Improved payment acknowledgement flow for Connect users 
- Better handling of PersonalID errors on multiple device logins 
- Enhanced GPS accuracy and reliability for GPS capture inside CommCare form

#### Important Bug Fixes

- Improved app install reliability on Android 15+ 
- Fixed WiFi Direct issue when sharing forms with media 
- Fixed SD card export from forms on Android 15+


### QA Notes

- Run the complete Connect and Personal ID regression plan after updating from CommCare 2.61. It's alright to run a set of regression tests together after updating from 2.61 but we should make sure that each test case is run on an app version that's been updated from 2.61 instead of a fresh install.

- **Connect Upgrade Testing:** Do a version upgrade from 2.57.0 to the latest version with a fully set up Personal ID login with opportunities and on upgrade, we should not need to re-login into Personal ID again and should see all the Connect related data as it was before the upgrade, this will need to be tested on all Connect screens.

- Test manual and automatic GPS capture and verify that the location values are saved correctly inside a CC form.

- Unsent forms correctly appear on the App Home Screen.

- **Form Entry**
  - Test that backing out or completing the form works correctly.
  - Test that form entry with phone rotation is saved and submitted successfully to the server.
  - Test incomplete form save.

- **Login Screen:** A video shared [here](https://dimagi.atlassian.net/browse/CI-467) shows steps to reproduce the bug. Verify that it's fixed, and also test various app switching and login scenarios on Login Screen.

- [Changes To Payment Acknowledgement Flow](https://docs.google.com/document/d/1Cl4CDc3uStMKOk4bAcBAq5APy1wKIYqaqC2RDR_JHKM/edit?tab=t.0): 
  
- **Verify bug fixes**
  - App installs are more reliable on Android 15+ devices. 
  - Fixes a Wifi direct bug when sharing forms with media. 
  - Fixes SD card export for forms on Android 15+ devices.

- **Connect landing screen redesign changes**
  - [CCCT-1648: Redesign Connect landing page on the mobile application](https://dimagi.atlassian.net/browse/CCCT-1648) — Be sure that you are able to see all 3 of the section headers and that they appear correctly in the UI: "In Progress", "New Opportunities", "Completed". Tap on a few opportunities and verify that the expected behavior still happens, especially that you are navigated to the correct app.
  - Already done as part of [QA-8382](https://dimagi.atlassian.net/browse/QA-8382) but QA should do a final check on the release version.

- **Map related changes**
  - Earlier QA was done in [QA-8273](https://dimagi.atlassian.net/browse/QA-8273) but QA should run a final check on the release candidate and also tests for the points below.
  - Test the map zooming when entering the Entity Map page, with and without location enabled on the device. Zooming should occur either way.
  - When location is enabled, the user's location should be factored into the zoom calculation so the user is included.
  - Rerun the performance testing done in [QA-8273](https://dimagi.atlassian.net/browse/QA-8273) and document the limits.

- **Hausa translation:** No need for detailed testing on this but do a smoke test going over different Connect Screens to see if anything looks weird.

- **PersonalID improvement for multiple device login**
  - Login to a PersonalID account that is currently logged in on a different device and has recently been accessed.
  - Verify that on successful login the new device shows the notification indicating that the old device was logged out.
  - Steps for triggering the lost config while in form entry:
    1. While on the Login page, turn on airplane mode.
    2. Login to a Connect app and enter form delivery.
    3. Turn off airplane mode and wait a few seconds for a background network request to attempt and fail (there should be no visible activity to the user, the call happens under the hood).
    4. Leave form entry, try to Sync on the home page, and verify the app bumps out to Login page and shows the error message.
  - Note that PersonalID tokens last 10 hours, and are not invalidated when a new login occurs. So after logging in on a second device, you will need to wait 10 hours before attempting to use the first device again (in order to trigger the error).

- **Push notification related changes** — user receives push notification whenever:
  - Learn assessment score is finalised — [CCCT-2070: (Mobile) Show push notification after we score a Learn assessment](https://dimagi.atlassian.net/browse/CCCT-2070)
  - Payment is rolled back — [CCCT-2099: Show push notification when last payment to worker is rolled back](https://dimagi.atlassian.net/browse/CCCT-2099)

- **Messaging channel subscription:** Test Subscribe and Unsubscribe for Connect messaging channels; unsubscribed channels should not receive any messages.

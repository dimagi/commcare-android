<!--
This file is meant as an easy way for us to collate notes and change logs across releases. 
-->

## CommCare 2.64

### Release Notes

<!--
These are published publicly on Playstore, Github Releases and CommCare Forums
-->

#### What's New

- [Profile Photo Update] PersonalID users can now update their profile photo directly from the side navigation drawer
- Reduced frequency of required biometric or pin unlocks for PersonalID and Connect  
- [Back Online Indicator] Refreshable Connect pages now show a green "Back Online" indicator at the top of the page when a sync succeeds after a previous offline failure
- [Delivery Progress Offline-First] The Connect Delivery Progress page now displays cached delivery data immediately on open, even with no network, and shows inline sync status (success / failure / offline) instead of a blocking loading dialog
- Launching an app from a Connect opportunity now opens it directly with a single loading dialog, instead of briefly flashing the login and app-setup screens

#### Important Bug Fixes

- Fixed the back arrow on the camera capture screen so it correctly returns to the previous screen

#### Internal Release Notes

<!--
Release notes that are not applicable for wider CommCare users but only for specific projects.
These notes are only published internally in [CommCare Change log wiki](https://dimagi.atlassian.net/wiki/spaces/internal/pages/2145058874/CommCare+Mobile+Changelog)
along with the public release notes above
-->

### QA Notes

<!--
These are for internal use and for us to keep track of important notes that
we would like to communicate to QA as part of the release testing
-->

- **Android Startup Strings Migration:** Walk the install / setup flows after a fresh install and confirm all on-screen text still renders correctly (no blank labels, no raw `install.button.start`-style keys showing through):
    - Launch a fresh CommCare install — verify the welcome screen ("Welcome to CommCare!" / "Please choose an installation method below") and install-method picker render.
    - Tap **Enter Code** / manual URL install — verify the prompt and the **Start Install** button label render. Submit an invalid URL and confirm the error message ("You did not scan a valid URL...") shows.
    - Tap **Offline Install** and try a `.ccz` install — verify the prompt ("Install your CommCare application from a .ccz file") and the **Install App** button label render.
    - From the install screen menu, tap **See Apps for My User** to open Install From List. Submit with empty fields — verify "Please enter all required fields." Submit with bad credentials in both Mobile User and Web User modes — verify each mode shows the appropriate error message. Toggle between user types and verify the **Web User** / **Mobile User** labels render.
    - From the App Manager menu, tap **Advanced Settings** — verify the title bar reads "App Manager > Advanced Settings". Tap **Developer Options** within Advanced Settings — verify the row title renders as "Developer Options" (not as a raw key).
    - Switch device language to one of the supported translations (Spanish, French, Portuguese, Hindi, Swahili, Hausa, Tigrinya, Lithuanian, Norwegian) and re-walk the install/setup flow. Confirm the migrated strings appear in the selected language with no missing-resource crashes and no English fallback for strings that should be translated.
- **PersonalID profile photo update from nav drawer:**
  - Sign in to PersonalID and open the side navigation drawer. Verify that the user's image in the drawer header is shown inside a circular white frame with a small camera icon overlay along the bottom edge.
  - Tap the image. Verify that an "Update Profile Photo" confirmation dialog appears with a message asking whether you would like to take a new profile photo, and Continue / Cancel buttons.
  - Verify that the dialog can be dismissed in three ways: tapping Cancel, tapping outside the dialog, and pressing the device back button. In all three cases, the photo should remain unchanged.
  - Tap Continue. Verify that the camera capture screen opens with the title "Take Profile Photo" (it should detect the user's face the same way as during PersonalID signup).
  - Capture a new photo. Verify that the drawer reopens, the new photo replaces the existing image, and the camera icon overlay is still shown along the bottom of the image.
  - Reopen the drawer later (after navigating around the app) and verify that the new photo is still shown.
  - **Failed upload:** turn on airplane mode, tap the image, and tap the Continue button. Verify that:
    - A toast appears with an error message.
    - The camera overlay icon switches to a yellow warning triangle.
  - With airplane mode still on, navigate around the app and reopen the drawer. Verify that the warning triangle is still shown over the image.
  - Turn airplane mode off, fully close the app (swipe it away from recent apps), and reopen it. Sign back in if needed. Verify that the warning triangle is gone and the camera icon is shown again.
  - With a working network connection, retry the photo update and verify a successful upload restores the camera icon and the new photo persists.
  - Verify that the photo update also reflects on HQ for the PersonalID user (i.e. the new photo is visible on the server-side admin view of the user's profile).

- **Add email address to PersonalID signup/recovery flow (WIP):**
  - **Flow overview:**
    - Signup: Phone → Biometrics → Phone OTP → Name → Backup Code → Email (optional) → Email OTP (only if email entered) → Photo
    - Recovery, no verified email available for the user: Phone → Biometrics → Phone OTP → Name → Backup Code → Email (optional) → Email OTP (only if email entered)
    - Recovery, verified email available for the user: Phone → Biometrics → Phone OTP → Name → Backup Code
  - **Email entry screen:**
    - Open the email entry screen (currently reachable only once the upstream PR wires it from the Backup Code step). Verify the CommCare-by-Dimagi banner is shown at the top, the action bar title reads "Email", and the screen shows an envelope icon next to the email input, with "Add your email (optional)" and a short description below the divider.
    - With the input empty or containing whitespace only, verify the Continue button is disabled and Skip for now is enabled.
    - Type a malformed email (e.g. `abc`, `user@`, `@nodomain.com`) and verify Continue stays disabled. With a malformed value in the field, press the keyboard Done key and verify nothing submits — the keyboard simply hides.
    - Type a well-formed email (e.g. `user@example.com`) and verify Continue becomes enabled.
    - Tap Skip for now. Verify a confirmation dialog appears with the title "Skip email?", the message "Are you sure you want to skip?", and Yes / No buttons.
        - Tap No: the dialog dismisses and the email screen stays put with any typed value preserved.
        - Tap Yes: the dialog dismisses and the flow advances to the next step (Photo Capture during signup).
    - Switch the device language to a supported locale (e.g. French, Spanish, Hindi, Swahili) and re-walk the screen. Verify the screen title, banner area, header copy, description, input hint, both button labels, and the skip-confirm dialog all render in the selected language.
  - **Email OTP screen:**
      - Reach the screen by entering a valid email on the previous step and tapping Continue. Verify the CommCare-by-Dimagi banner is shown at the top, the action bar title reads "Verify Email", a lock icon sits to the left of the 6-digit OTP field, and the description below the divider reads "Enter the 6-digit code sent to <email>" with the address the user typed.
      - On open, verify the Verify button is disabled, the OTP field is empty, the resend area shows a "Didn't receive your code? Resend in 120 s" countdown that decrements every second, and the Resend Code button itself is hidden.
      - Wait for (or fast-forward by changing device time) the cooldown to expire. Verify the countdown disappears and the Resend Code button becomes visible. Tap Resend Code — verify a fresh OTP arrives and the 2-minute countdown restarts.
      - Type 5 digits — verify the Verify button stays disabled. Type the 6th digit — verification fires automatically. With a correct code:
          - **Signup**: the flow advances to Photo Capture.
          - **Recovery**: the recovery-success screen is shown.
          - **Existing user**: a non-cancellable confirmation dialog appears with the title "Email Added" and message "Your email has been added successfully."; tapping OK closes the screen and the new email is visible on the user's HQ admin profile.
      - With an incorrect code, verify an error message appears under the OTP field and the OTP cells switch to a red error state. Enter a wrong code 3 times in a row — verify a dialog appears titled "Verification unsuccessful" with the message about 3 incorrect attempts, and two buttons: "Try again" (dismisses dialog, clears the OTP field, lets the user retry) and "Proceed without email" (advances to the next step without saving an email).
      - Press the device Back button on the verification screen — verify the user returns to the Email entry screen with the email address still populated.
      - Switch the device language to a supported locale (e.g. French, Spanish, Hindi, Swahili) and re-walk the screen. Verify the action-bar title, in-screen title, description (with email substitution), Verify button label, resend button + countdown text, error messages, and both failure-dialog buttons all render in the selected language.
  - **Existing logged-in users prompt:**
    - Precondition: signed in to PersonalID as an existing user with **no email** on file, and the `email_otp_verification` server toggle **ON**.
    - Return to the CommCare home screen. Verify a dialog titled "Add your email address" appears, with a message explaining an email helps recover the account if phone access is lost, and **Add email** / **Not now** buttons.
    - Tap **Not now**: the dialog dismisses and you land on the home screen normally.
    - Tap **Add email**: verify the Email entry screen opens (the same screen used during signup, now reached as an existing user).
    - With the toggle **OFF**, or for a user who **already has an email** on file, return to the home screen and verify the prompt does **not** appear.
    - The prompt is shown at most twice, and will not appear again thereafter.
    - Switch the device language to a supported locale and verify the dialog title, message, and both button labels render in the selected language.
  - **Backup Code → Email entry routing (signup + recovery):**
    - **Signup with `email_otp_verification` server toggle ON:** Walk a fresh PersonalID signup. After entering and confirming the backup code, verify the Email entry screen appears next (not Photo Capture).
    - **Signup with `email_otp_verification` server toggle OFF:** Walk a fresh PersonalID signup. After confirming the backup code, verify the flow skips Email and goes directly to Photo Capture.
    - **Recovery with the server already returning a verified email for the user:** Walk PersonalID account recovery (validate backup code on a new device). Verify the flow skips the Email screen entirely and goes directly to the "Account Recovered" success screen.
    - **Recovery with no server email, `email_otp_verification` toggle ON:** Recover an account that has no email on file. After backup code validation, verify the Email entry screen appears (so the user can add an email during recovery).
    - **Recovery with `email_otp_verification` toggle OFF:** Recover any account with the toggle disabled. Verify the flow goes directly from backup code to the "Account Recovered" success screen without the Email screen.
    - **Email persisted on the user record:** After completing a signup that included entering and verifying an email, verify the HQ admin view of the PersonalID user shows the email address that was entered. Repeat the same check after a recovery that included the Email screen.
  - In order to achieve this functionality, DB migrations are done to accommodate the new email address field. QA should start testing with the previous version of the app, having PersonalID login already, and then upgrade to this new version. The app should work without crashing.
  - QA should also test with a fresh installation of this new version, going through PersonalID signup/recovery.

- **[PersonalID] Session-based unlock for in-app Connect navigation:**
  - An app session is a foreground app session, i.e. user exiting/backgrounding the app and resuming into it counts as a new session. 
  - Tapping Connect Jobs, Messaging, or Work History from the nav drawer no longer prompts for biometric/PIN if the user already unlocked within the last 10 minutes in the same app session.
  - Notification redirects to these screens follow same rules as the menu itself. 
  - Opening any of below destinations from a push notification still always prompts:
    - Sensitive operations (login, link/unlink app) still require explicit re-authentication every time.
    - Notifications redirect into the app only required re-auth when user is not already logged into the CommCare App. 

- **Back Online indicator (Connect):**
  - Open a refreshable Connect page (Connect Home, Learning Progress, Delivery Progress) while online and trigger a sync. Verify that the success bar at the top of the page now shows a green background with "Last synced: Just Now" and no right-side indicator.
  - Turn on airplane mode (or otherwise disable network) and trigger a sync on the same page. Verify that the orange "Offline" indicator appears at the top of the page.
  - Turn the network back on and trigger a sync (or wait for the auto-refresh on reconnect). Verify that the bar now shows a green background with "Last synced: Just Now" on the left and "Back Online" plus a WiFi icon on the right, and that it auto-dismisses after a few seconds.
  - Trigger a non-network failure (e.g. a server error) and then a successful sync. Verify that the bar shows the regular green success message **without** the "Back Online" indicator (the Back Online indicator should only appear after an offline failure).
  - Switch the device language to a non-English locale (e.g. French, Spanish, Hindi) and repeat the back-online flow. Verify that the "Back Online" label is shown in the selected language.

- **Back button on camera capture screen:**
  - During PersonalID signup for a new phone number, get to the photo capture step and tap **Take Photo** to open the camera. Tap the back arrow in the top toolbar. Verify that the camera closes and you are returned to the photo capture screen.
  - From a signed-in PersonalID session, open the side navigation drawer and tap the user image, then Continue. Tap the back arrow in the camera screen's top toolbar. Verify that the camera closes and you are returned to the previous screen with no photo change.
  - In both flows, verify the device's system back button continues to work the same way.

- **Delivery Progress offline-first (Connect):**
  - Open the Connect Delivery Progress page while online with a working network and let it sync. Verify the progress, payment list, payment-confirmation tile, and "Last updated" timestamp all populate as before, and that the green "Sync successful" bar flashes briefly at the top.
  - Background the app, turn on airplane mode, and reopen the Delivery Progress page. Verify cached delivery data (progress, deliveries, payments, "Last updated" timestamp) appears immediately without waiting for a network call, and that the orange "Offline" indicator with the previous sync time is shown at the top.
  - Confirm the full-screen blocking loading dialog that used to appear on refresh no longer appears — the inline small progress spinner is the only loading indicator.
- **Connect app launch:**
  - From the Connect opportunities list, launch an installed learn or delivery app: confirm it opens to the app home behind a single progress dialog (no login/app-setup screens flashing through), and that pressing back from the app home returns to the opportunities list.
  - With networking off, launching an app from a Connect opportunity should fall back to the normal login screen showing the usual error — not hang on the dialog or crash.



## CommCare 2.63

### Release Notes

<!--
These are published publicly on Playstore, Github Releases and CommCare Forums
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
Release notes that are not applicable for wider CommCare users but only for specific projects. 
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

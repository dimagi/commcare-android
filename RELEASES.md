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

#### Important Bug Fixes

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
  - **Email entry screen:** (will be added here)
  - **Email OTP screen:** (will be added here)
  - **Legacy logged-in users prompt:** (will be added here)
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

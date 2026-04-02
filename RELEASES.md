<!--
This file is meant as an easy way for us to collate notes and change logs across releases. 
-->

## CommCare 2.63

### Release Notes

<!--
These are published publically on Playstore, Github Releases and CommCare Forums
-->

#### What's New

- [Relearn Tasking] Added relearn task notification UI to Connect opportunity cards

#### Important Bug Fixes

#### Internal Release Notes

<!--
Release notes that are not applicable for wider CommCare users but only for a specific projects. 
These notes are only published internally in [CommCare Change log wiki](https://dimagi.atlassian.net/wiki/spaces/internal/pages/2145058874/CommCare+Mobile+Changelog)
along with the public release notes above
-->


### QA Notes

<!--
These are for internal use and for us to keep track of important notes that
we would like to communicate to QA as part of the release testing
-->

- Verify that the existing opportunity card UI is unchanged when there are no relearn tasks.
- Verify that the opportunity card updates as expected when there are either pending relearn tasks or completed relearn tasks.

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

# CHANGELOG

The changelog for [CommCare Android](https://github.com/dimagi/commcare-android). Also see the [releases](https://github.com/dimagi/commcare-android/releases) on Github.

## [2.50.0] (upcoming release)
---
### Features

### Bug Fixes

## [2.49.0] - 2020-06-11
---
### Features
- Removed support for Android 4.0 devices.
- Improved GIS capabilities so that users can view cases on map and record a boundary in an X-Form.
- Implemented a more robust background scheduling mechanism for Auto-updates. 
- Improved error message when users are being rate-limited. 
- CommCare will start auto-submitting forms in a timely fashion. 
- Added support for updating CommCare from inside CommCare when a newer version is available on playstore. 
- Now we show a detailed error message when user is behind a captive portal while using CommCare. 
- Modified the search algorithm to match perfect prefixes. 
- Added support for `index-of` x-query function which will return the position of atomic value within a sequence. 
- Implemented a custom property `cc-label-required-questions-with-asterisk` to show a red asterisk to denote mandatory questions in a form. 
- Added a new flag "AUTO_SYNC_FREQUENCY" for auto-sync which will get priority over the old flag "cc-auto-update".

### Bug Fixes
- Fixed a bug in the position of popup menus which only affected users on android 7 devices.

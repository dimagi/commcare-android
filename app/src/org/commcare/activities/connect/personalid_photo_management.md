# PersonalID Photo Management

## Overview

PersonalID users can update their profile photo directly from the navigation drawer. Tapping
the avatar opens a confirmation dialog; confirming launches the same camera capture flow used
during PersonalID signup. The photo is uploaded via the existing `update_user_profile`
endpoint, stored locally, and reflected in the drawer header.

## User Flow

1. User opens the side drawer.
2. User taps their avatar in the drawer header.
3. A confirmation dialog appears (Cancel / Continue, dismissible by tapping outside).
4. On Continue, `MicroImageActivity` launches with face detection (front camera, max 160px /
   100KB), identical to the signup photo capture.
5. After capture, the drawer is reopened with the new photo loaded optimistically.
6. The photo is uploaded via `PersonalIdApiHandler.updateProfile()`.
7. **On success**: photo persisted to `ConnectUserRecord.photo`; the camera overlay icon
   stays on the avatar.
8. **On non-blocking failure** (e.g. no internet): photo reverts to the previous one, a toast
   shows the standard PersonalID error message, and the avatar overlay icon switches to a
   warning triangle. A SharedPreferences flag persists this warning state until the app is
   closed and reopened.
9. **On blocking failure** (e.g. file too large): the app crashes via
   `PersonalIdOrConnectApiErrorHandler.handle()` to mirror the signup flow's behavior.

## Components

* **`BaseDrawerController`** (`navdrawer/BaseDrawerController.kt`)
  * Wires the avatar tap → confirmation dialog → camera launch.
  * Reads `PersonalIDUserPreferences.didLastPhotoUploadFail()` on every drawer refresh and
    swaps `avatar_overlay_icon` between the camera and warning drawables accordingly.
  * Calls `openDrawer()` after a successful capture to keep the sidebar visible while the
    upload runs.

* **`CommCareApplication`** (`CommCareApplication.java`)
  * Clears the photo-upload-failure flag from `PersonalIDUserPreferences` in `onCreate`,
    so the warning icon resets when the process starts (i.e. when the app is closed and
    reopened) but persists across in-process activity transitions.

* **`PersonalIDUserPreferences`** (`preferences/PersonalIDUserPreferences.kt`)
  * SharedPreferences wrapper holding the boolean `last_photo_upload_failed` flag.

* **API Layer** (unchanged, reused from signup-completion):
  * `ApiPersonalId.updateUserProfile()` — `POST /users/update_profile` with HTTP Basic Auth.
  * `PersonalIdApiHandler.updateProfile()` — passes only the photo (other fields null) since
    only the photo is editable from the drawer today.

## UI Resources

* `nav_drawer_header.xml` — avatar in a 72dp `FrameLayout` with white circular frame
  (`bg_user_avatar_frame`) and a 24dp overlay container (`bg_user_avatar_icon_overlay`,
  60% opacity black) at `bottom|start`.
* `ic_personalid_camera.xml` — default camera icon shown over the avatar.
* `ic_personalid_warning.xml` — warning triangle shown after a failed upload.

## Error Handling

Errors are routed through the shared `PersonalIdOrConnectApiErrorHandler.handle()` to keep
behavior aligned with the PersonalID signup flow:

* **Non-blocking** (network, server, rate-limit, etc.): returns a localized message which is
  surfaced as a toast; the warning overlay is shown and the failure flag persisted.
* **Blocking** (e.g. `FILE_TOO_LARGE_ERROR` with a non-null `Throwable`): re-throws as a
  `RuntimeException`, mirroring the signup flow's crash behavior.

## Authentication

Photo upload uses HTTP Basic Auth with the user's stored `userId` and `password` (same as
`updateUserProfile` elsewhere). This differs from initial profile completion, which uses a
Bearer token from the configuration session.

## Data Storage

The photo is stored as a base64 string in `ConnectUserRecord.photo` (persisted field #13,
nullable). The drawer reads this field on every `refreshDrawerContent()` call, so updates
appear without explicit refresh callbacks.
# PersonalID Photo Management

## Overview

PersonalID users can update their profile photo directly from the navigation drawer. Tapping
the user image opens a confirmation dialog; confirming launches the same camera capture flow used
during PersonalID signup. The photo is uploaded via the existing `update_user_profile`
endpoint, stored locally, and reflected in the drawer header.

## User Flow

1. User opens the side drawer.
2. User taps their image in the drawer header.
3. A confirmation dialog appears (Cancel / Continue, dismissible by tapping outside).
4. On Continue, the controller checks network connectivity. If the device is offline, a
   "no network" toast is shown and the flow ends without launching the camera.
5. Otherwise, `MicroImageActivity` launches with face detection (front camera, max 160px /
   100KB), identical to the signup photo capture.
6. After capture, the photo is uploaded via `PersonalIdApiHandler.updateProfile()`. The
   drawer continues to show the previous photo while the upload is in flight.
7. **On success**: photo persisted to `ConnectUserRecord.photo`, the new photo is loaded
   into the drawer, the camera overlay icon is restored, and a success toast is shown.
8. **On non-blocking failure** (e.g. server error mid-upload): the previous photo remains
   visible (nothing to revert, since the new photo was never displayed), a toast shows the
   standard PersonalID error message, and the user image overlay icon switches to a warning
   triangle. The warning state lives on the `BaseDrawerController` instance and is cleared
   on the next successful upload or when a new controller is created.
9. **On blocking failure** (e.g. file too large): the app crashes via
   `PersonalIdOrConnectApiErrorHandler.handle()` to mirror the signup flow's behavior.

## Components

* **`BaseDrawerController`** (`navdrawer/BaseDrawerController.kt`)
  * Wires the user image tap → confirmation dialog → network check → camera launch.
  * Holds an in-memory `lastPhotoUploadFailed: Boolean` flag that is read on every drawer
    refresh to swap `user_image_overlay_icon` between the camera and warning drawables.
  * Re-reads the `ConnectUserRecord` from the database in `refreshDrawerContent()` and
    `uploadUserPhoto()` rather than caching it at setup time, so the drawer always reflects
    the currently signed-in user.

* **API Layer** (unchanged, reused from signup-completion):
  * `ApiPersonalId.updateUserProfile()` — `POST /users/update_profile` with HTTP Basic Auth.
  * `PersonalIdApiHandler.updateProfile()` — passes only the photo (other fields null) since
    only the photo is editable from the drawer today.

## UI Resources

* `nav_drawer_header.xml` — user image in a 72dp `MaterialCardView` with white circular frame and an overlay image container (60% opacity, black).
* `ic_personalid_camera.xml` — default camera icon shown over the user image.
* `ic_personalid_warning.xml` — warning triangle shown after a failed upload.

## Error Handling

Errors are routed through the shared `PersonalIdOrConnectApiErrorHandler.handle()` to keep
behavior aligned with the PersonalID signup flow:

* **Non-blocking** (network, server, rate-limit, etc.): returns a localized message which is
  surfaced as a toast; the warning overlay is shown and the in-memory failure flag is set.
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
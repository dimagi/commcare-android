# PersonalID Photo Management

## Problem

PersonalID users who complete registration without a photo (or want to update their existing photo) have no way to add or change their profile photo after sign-up.

## Acceptance Criteria

- Users without a photo can add one to their PersonalID account
- Users with an existing photo can update it

## Approach

New standalone `PersonalIdProfileActivity` launched from the side nav profile header. Camera-only capture reusing `MicroImageActivity` (face detection). New dedicated API endpoint for photo updates.

## Entry Point

- Unhide the existing `manage_profile` TextView in `nav_drawer_header.xml`
- Make the profile header card in the side nav clickable
- `BaseDrawerController.kt` gets a click listener that launches `PersonalIdProfileActivity`

## Profile Screen UI

```
+-----------------------------+
|  <- Profile                 |   Toolbar with back arrow
+-----------------------------+
|                             |
|       +-------------+      |
|       |             |      |   Circular photo (120dp)
|       |   Photo     |      |   Current photo or
|       |  or Avatar  |      |   default placeholder
|       |             |      |
|       +-------------+      |
|                             |
|        John Smith           |   User's name (read-only)
|                             |
|   +---------------------+  |
|   |   Add Photo         |  |   "Add Photo" (no photo)
|   +---------------------+  |   "Update Photo" (has photo)
|                             |
+-----------------------------+
```

### States

- **No photo:** Placeholder avatar, button says "Add Photo"
- **Has photo:** Current photo in circle (Glide, circular crop), button says "Update Photo"
- **Uploading:** `CustomProgressDialog` shown (reusing existing pattern from Connect activities)
- **Success:** Dialog dismissed, photo updated in-place, snackbar confirms update
- **Error:** Dialog dismissed, snackbar with error, photo preview reverted, user can retry

## Data Flow

1. `PersonalIdProfileActivity` launches `MicroImageActivity` via `ActivityResultLauncher`
2. `MicroImageActivity` returns Base64 WebP string (`"data:image/webp;base64,..."`)
3. Activity receives result, updates photo preview ImageView
4. `CustomProgressDialog` shown
5. New `ApiPersonalId.updatePhoto()` called with Base64 photo and auth token
6. On success: update `ConnectUserRecord.photo` in local DB, dismiss dialog, show success snackbar
7. On failure: dismiss dialog, revert preview to previous photo/placeholder, show error snackbar

Error handling follows existing `PersonalIdApiHandler` patterns (`FAILED_TO_UPLOAD`, `FILE_TOO_LARGE`, etc.).

## Side Nav Refresh

When user returns from `PersonalIdProfileActivity`, the side nav's `refreshDrawerContent()` reloads user data from DB and updates the profile photo via Glide automatically.

## Files

### New

| File | Purpose |
|------|---------|
| `PersonalIdProfileActivity.java` | Activity hosting the profile screen |
| `activity_personalid_profile.xml` | Layout for the profile screen |

### Modified

| File | Change |
|------|--------|
| `nav_drawer_header.xml` | Unhide "Manage profile" text |
| `BaseDrawerController.kt` | Click listener on profile card to launch profile activity |
| `ApiService.java` | New `updatePhoto` endpoint method |
| `ApiEndPoints.java` | New endpoint constant |
| `ApiPersonalId.java` | New `updatePhoto()` static method |
| `PersonalIdApiHandler.java` | New `updatePhoto()` handler method |
| `AndroidManifest.xml` | Register `PersonalIdProfileActivity` |
| `strings.xml` | Button labels, success/error messages |

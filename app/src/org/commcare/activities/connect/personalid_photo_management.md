# PersonalID Photo Management

## Overview

PersonalID users can add or update their profile photo from a dedicated profile screen accessible via the side navigation drawer. The photo is captured using the device camera with face detection, uploaded to the PersonalID server, and stored locally for display in the nav drawer.

## Components

* **PersonalIdProfileActivity** (`activities/connect/PersonalIdProfileActivity.kt`)
  * Standalone activity displaying user name (read-only) and circular profile photo
  * "Add Photo" / "Update Photo" button launches camera capture
  * Optimistic UI: displays new photo immediately, reverts on upload failure
  * Uses `CustomProgressDialog` during upload

* **API Layer**
  * `ApiEndPoints.updatePhoto` - `POST /users/update_photo`
  * `ApiService.updatePhoto()` - Retrofit interface method
  * `ApiPersonalId.updatePhoto()` - Constructs request with `ProvidedAuth` (userId + password)
  * `PersonalIdApiHandler.updatePhoto()` - Handler with `NoParsingResponseParser` (success/failure only)

* **Navigation**
  * `ConnectNavHelper.unlockAndGoToProfile()` - Requires PersonalID unlock before navigation
  * `BaseDrawerController` - Profile card click listener wired to launch profile screen

## Photo Capture Flow

1. User taps "Add Photo" / "Update Photo"
2. `MicroImageActivity` launches with custom title via `MICRO_IMAGE_TITLE_EXTRA` (front camera, face detection, max 160px / 100KB)
3. On capture success, base64 photo returned via `ActivityResult`
4. Photo displayed immediately via Glide with circular crop
5. Cancellable progress dialog shown during upload
6. `PersonalIdApiHandler.updatePhoto()` sends base64 to server
7. On success: photo saved to `ConnectUserRecord` via `ConnectUserDatabaseUtil`, Toast confirmation
8. On failure: photo reverted to previous state, error shown via Toast

## Authentication

Photo upload uses HTTP Basic Auth with the user's stored `userId` and `password` (same pattern as `updateUserProfile`). This differs from the initial profile completion flow which uses a Bearer token from the configuration session.

## Data Storage

The photo is stored as a base64 string in `ConnectUserRecord.photo` (persisted field #13, nullable). The nav drawer reads this field on every drawer open via `refreshDrawerContent()`, so photo updates are reflected without explicit refresh callbacks.

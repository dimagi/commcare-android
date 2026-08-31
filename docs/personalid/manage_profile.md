# PersonalID Manage Profile

## Overview

Signed-in PersonalID users reach their profile from the navigation drawer's **Manage Profile**
link. The Profile screen shows their details and offers forgetting the account; a separate Edit
screen lets them change name, email, and photo.

## Structure

* **`PersonalIdProfileActivity`** — single-activity host for the profile nav graph. Every
  destination shows a back arrow (no top-level destinations). Locked to portrait mode.
* **`PersonalIdProfileFragment`** — read-only view plus the Forget PersonalID action.
* **`PersonalIdProfileEditFragment`** / **`PersonalIdProfileEditViewModel`** — the edit form.
  Form state lives in a `SavedStateHandle` so typed values survive rotation, process death, and
  the round-trip to email verification; the persisted `ConnectUserRecord` remains the source of
  truth for original values.

## Save behavior

Photo, name, and email are three independent saves — this is intentional, not a bug:

* **Photo** — edited through the shared `PersonalIdPhotoUpdater` and persisted on capture,
  independently of the Name/Email form.
* **Name** — written via the `update_profile` API when the form is saved.
* **Email** — a change requires OTP verification. The edit fragment sends the OTP and navigates
  to `PersonalIdEmailVerificationFragment` (`EXISTING_USER` workflow), which writes the new email
  to the record and finishes the activity.

Because name commits before the OTP step, a user who saves a name change and then abandons email
verification keeps the new name while the email stays unchanged.

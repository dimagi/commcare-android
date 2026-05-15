# Manage Profile — Implementation Plan

**Design doc:** [Manage Profile](https://docs.google.com/document/d/11B3b8K92_bPQh71CVmNm8VTLVvvKid6ta4G7esSrSVc/edit?tab=t.0) — the "Path Forward" section is the source of truth (Option 2 for Profile, Option 1 for Edit Information).
**Branch:** `CCCT-2345-create-manage-profile-screen-plan`

## Goal

Give a signed-in PersonalID user a dedicated Profile screen reached from the nav drawer, plus a separate Edit screen for changing editable fields. They can:
- View name, phone, email, and their photo
- Update the photo (reusing the existing flow)
- Tap an edit pencil to change their **name** and **email** on a separate Edit screen
- Open Work History via a Credentials card
- Open App Manager from a 3-dot menu
- Forget their PersonalID via a destructive CTA with a confirmation modal

App Manager and Forget PersonalID move out of the login screen 3-dot menu into the new Profile screen.

Phone number is **not editable** in this MVP — it renders as a greyed-out, disabled field on the Edit screen (per the Path Forward).

## Architecture

A new `PersonalIdProfileActivity` hosts a small Jetpack Navigation graph with two destinations: `PersonalIdProfileFragment` (view-only) and `PersonalIdEditProfileFragment` (form). Both new fragments live in `org.commcare.fragments.personalId` and are written in Kotlin with ViewBinding + `ViewModelProvider`, matching the pattern already used by other PersonalID fragments (see `PersonalIdPhotoCaptureFragment` for reference).

The screen reads from `ConnectUserDatabaseUtil.getUser(context)` (returns a `ConnectUserRecord`) and writes via `ApiPersonalId.updateUserProfile(...)`. On a successful save we update the local `ConnectUserRecord` and `ConnectUserDatabaseUtil.storeUser(...)`.

The photo update flow that lives in `BaseDrawerController` today gets extracted into a reusable helper so both the drawer and the Edit Profile screen call the same code path.

**Dark launch.** The drawer link — the only entry point to all of this work — is hard-coded to `View.GONE` from Phase 1 through Phase 5, so every intermediate phase is safely releasable. Phase 6 swaps the hard-coded `View.GONE` for the real signed-in conditional and removes the old login-screen entries this feature replaces.

## File map

**New**
- `app/src/org/commcare/activities/connect/PersonalIdProfileActivity.kt`
- `app/src/org/commcare/fragments/personalId/PersonalIdProfileFragment.kt`
- `app/src/org/commcare/fragments/personalId/PersonalIdEditProfileFragment.kt`
- `app/src/org/commcare/activities/connect/viewmodel/PersonalIdProfileViewModel.kt`
- `app/src/org/commcare/activities/connect/viewmodel/PersonalIdEditProfileViewModel.kt`
- `app/src/org/commcare/connect/photo/PersonalIdPhotoUpdater.kt` *(extracted helper)*
- `app/res/layout/activity_personalid_profile.xml`
- `app/res/layout/personalid_profile_screen.xml`
- `app/res/layout/personalid_edit_profile_screen.xml`
- `app/res/menu/personalid_profile_menu.xml`
- `app/res/navigation/nav_graph_profile.xml`
- Unit tests for both ViewModels in `app/unit-tests/src/org/commcare/activities/connect/viewmodel/`

**Modified**
- `app/res/layout/nav_drawer_header.xml` — add the "Manage Profile" link under the user name
- `app/src/org/commcare/navdrawer/DrawerViewRefs.kt` — bind the new link
- `app/src/org/commcare/navdrawer/BaseDrawerController.kt` — wire the click; delegate the photo flow to the new helper
- `app/src/org/commcare/connect/network/ApiPersonalId.java` — add `email` to `updateUserProfile`
- `app/src/org/commcare/activities/LoginActivity.java` — remove `App Manager` and `Forget PersonalID` from the 3-dot menu
- `app/AndroidManifest.xml` — register `PersonalIdProfileActivity`
- `app/res/values/strings.xml` — new strings (see Phase 1)

---

## Phase 1 — Sidebar "Manage Profile" link

Goal: a tappable "Manage Profile" subtitle below the user's name in the drawer header that opens the new (empty for now) Profile activity.

1. **Strings.** Add the new keys to `app/res/values/strings.xml`, following the existing `personalid_*` naming convention (see [around line 689](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/res/values/strings.xml#L689) for recent additions):
   - `personalid_manage_profile` — `<u>Manage Profile</u>` (the `<u>` tags render an underline via `setText`; translators must preserve them)
   - `personalid_profile_title` — "Profile"
   - `personalid_edit_profile_title` — "Edit Profile"
   - `personalid_profile_section_personal_information`
   - `personalid_profile_field_name`, `personalid_profile_field_phone`, `personalid_profile_field_email`
   - `personalid_profile_credentials_title`, `personalid_profile_credentials_subtitle` ("View My Earned Certificates")
   - `personalid_profile_forget_account`, `personalid_profile_menu_app_manager`
   - `personalid_edit_profile_email_otp_notice` — "Changes to your email will require OTP verification."
   - `personalid_edit_profile_error_email_invalid`, `personalid_edit_profile_error_email_required`
   - Forget modal: `personalid_profile_forget_confirm_title`, `personalid_profile_forget_confirm_message`
   - Discard modal: `personalid_edit_profile_discard_title` ("Discard your changes?"), `personalid_edit_profile_discard_message` ("Your unsaved changes will be lost."), `personalid_edit_profile_discard_positive` ("Discard"), `personalid_edit_profile_discard_negative` ("Keep editing")

2. **Drawer header layout.** In `nav_drawer_header.xml`, inside the inner vertical `LinearLayout` that holds `@id/header_user_name` ([around line 71](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/res/layout/nav_drawer_header.xml#L71)), add a second `TextView` directly below with id `@+id/header_manage_profile`, text `@string/personalid_manage_profile`, small white text, and `?attr/selectableItemBackground`. The `<u>` tags in the string render the underline (Fig. 1 in the design doc).

3. **View ref.** Add `val manageProfileLink: TextView = rootView.findViewById(R.id.header_manage_profile)` to `DrawerViewRefs.kt`.

4. **Click + visibility.** In `BaseDrawerController.kt`:
   - In `setupListeners()` ([around line 132](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/navdrawer/BaseDrawerController.kt#L132)), add a click listener on `binding.manageProfileLink` that starts `PersonalIdProfileActivity` and calls `closeDrawer()`.
   - In `refreshDrawerContent()` ([around line 291](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/navdrawer/BaseDrawerController.kt#L291) where `profileCard.visibility` is set), hard-code `manageProfileLink.visibility = View.GONE`. This keeps the feature invisible to users while phases 2–5 land. Phase 6 swaps this line for the real signed-in conditional.

5. **Activity stub.** Create `PersonalIdProfileActivity` extending `CommCareActivity<PersonalIdProfileActivity>` (matches `PersonalIdWorkHistoryActivity`) with an empty `onCreate` so the click target exists. Register it in `AndroidManifest.xml`, mirroring the `PersonalIdWorkHistoryActivity` entry.

6. **Verify.** Build the debug variant with `./gradlew :app:assembleCommcareDebug`. Install, sign in, open the drawer — the link should **not** appear (it's pinned to `View.GONE`). To smoke-test the click wiring, temporarily change the line to `View.VISIBLE`, rebuild, confirm the link appears and tapping it opens the empty activity, then revert to `View.GONE` before committing.

**Commit** when the build is clean and manual smoke passes. Use a single commit for Phase 1.

---

## Phase 2 — Profile activity + navigation graph

Goal: replace the stub activity with a proper host that owns the Profile and Edit Profile destinations.

1. **Nav graph.** Create `nav_graph_profile.xml` with two `<fragment>` destinations — `personalid_profile_fragment` (start, `android:label="@string/personalid_profile_title"`) and `personalid_edit_profile_fragment` (`android:label="@string/personalid_edit_profile_title"`) — and an action `action_profile_to_edit_profile` between them. Mirror the structure of `nav_graph_personalid.xml`. `NavigationUI` reads these labels for the toolbar title.

2. **Activity layout.** `activity_personalid_profile.xml` is a vertical `LinearLayout` containing `<include layout="@layout/appbar_layout"/>` at the top and a `FragmentContainerView` (`@+id/profile_nav_host`, `app:defaultNavHost="true"`, `app:navGraph="@navigation/nav_graph_profile"`) below it. The toolbar stays fixed; each destination fragment wraps its own content in a `ScrollView`.

3. **Activity.** Replace the stub with `class PersonalIdProfileActivity : CommCareActivity<PersonalIdProfileActivity>()`. In `onCreate`, look up the `NavHostFragment` by `R.id.profile_nav_host` and call `NavigationUI.setupActionBarWithNavController(this, navController)` so the toolbar shows the destination label and the back arrow. Override `onSupportNavigateUp()` to delegate to `navController.navigateUp()`. `CommCareActivity` provides the `SupportActionBar` from the `appbar_layout` include — no manual `setSupportActionBar` call needed.

4. **Placeholder fragments.** Create both Kotlin fragment files with empty `onCreateView` returning a dummy view so the nav graph compiles. They will be filled in by Phases 3 and 5.

5. **Verify.** Build, open Profile from the drawer — the empty fragment renders inside a CommCare-themed toolbar. The toolbar title reads "Profile".

**Commit.**

---

## Phase 3 — Profile screen (view-only)

Goal: render the user's photo, name, phone, email, the Credentials card, the Forget CTA, and the 3-dot/edit menu, as shown in Fig. 6 (left side) of the design doc.

1. **Layout `personalid_profile_screen.xml`.** Top to bottom, wrapped in a `ScrollView`:
   - Circular photo, no overlay and not clickable — a `MaterialCardView` with `app:cardCornerRadius="50dp"` wrapping a centered `ImageView` (id `@+id/profile_user_image`). The photo is read-only on this screen; the camera-icon overlay only appears on the Edit screen.
   - Below the photo: name (large, centered) and primary phone (small, centered). IDs `profile_name`, `profile_phone_subtitle`.
   - Section header "Personal Information".
   - Three read-only rows (small label above value): Name, Phone Number, Email Address. Use IDs `profile_value_name`, `profile_value_phone`, `profile_value_email`. Match the Figma row style — see Fig. 6.
   - Credentials card (`@+id/profile_credentials_card`): a `MaterialCardView` with the credentials icon, title "Credentials", subtitle "View My Earned Certificates", and a trailing chevron.
   - Forget PersonalID button (`@+id/profile_btn_forget_personalid`) styled as a destructive action — `@color/red_700` background, white text, full-width, near the bottom.

   The toolbar lives in the host activity, not in this layout.

2. **Menu `personalid_profile_menu.xml`.** Two items:
   - `@+id/action_edit_profile` — `showAsAction="ifRoom"`, pencil icon. Check the existing drawables before creating a new one; a generic edit icon may already exist.
   - `@+id/action_app_manager` — `showAsAction="never"` so it lands in the overflow.

3. **`PersonalIdProfileViewModel`.** A small `ViewModel` exposing a `LiveData<UiState>` with `name`, `primaryPhone`, `email`, and `photoBase64`. It loads from `ConnectUserDatabaseUtil.getUser(context)` and maps the record to the UI state. Pull the mapping out into a pure `companion object` function so it is trivially unit-testable: `fun toUiState(record: ConnectUserRecord): UiState`.

   **Tests** (`PersonalIdProfileViewModelTest`): one test asserts the four fields map through correctly; one asserts `null` email falls back to an empty string.

4. **`PersonalIdProfileFragment`.** Wire ViewBinding, instantiate the ViewModel via `ViewModelProvider`, observe the LiveData, and render. Load the photo with Glide using the same configuration as `BaseDrawerController.loadUserPhoto` (placeholder = `R.drawable.nav_drawer_person_avatar`). The photo `ImageView` has no click listener on this screen. Call `setHasOptionsMenu(true)` and inflate `personalid_profile_menu.xml` in `onCreateOptionsMenu`. Hook `action_edit_profile` to navigate via `findNavController().navigate(R.id.action_profile_to_edit_profile)`. Leave `action_app_manager`, the Forget button, and the Credentials card as stubs — Phase 4 wires them.

5. **Verify.** Open the Profile screen; the user's real name, phone, email and photo all render. Tap the edit pencil — it navigates to the empty Edit fragment. Run the ViewModel tests with `./gradlew :app:testCommcareDebugUnitTest`.

**Commit.**

---

## Phase 4 — Profile screen actions

Three small pieces of behavior, each independently testable.

### 4.1 Forget PersonalID with confirmation modal

Wire `profile_btn_forget_personalid` to show a `StandardAlertDialog` (the same dialog class used by `BaseDrawerController.showUpdatePhotoConfirmationDialog` [around line 165](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/navdrawer/BaseDrawerController.kt#L165)). Title and message: `personalid_profile_forget_confirm_title`, `personalid_profile_forget_confirm_message`. Positive button calls `PersonalIdManager.getInstance().forgetUser(reason)` then `requireActivity().finish()`. Negative button just dismisses.

For the analytics `reason` argument, add a new constant on `AnalyticsParamValue` (e.g. `PERSONAL_ID_FORGOT_USER_PROFILE_PAGE`) so we can distinguish the Profile-screen trigger from the legacy login-screen trigger.

Manual test: tapping the button shows the modal; Cancel dismisses; Forget wipes local state and the calling activity's drawer returns to its signed-out appearance on next open. The drawer self-heals via `refreshDrawerContent()` on `onDrawerSlide`; `LoginActivity.onResume()` already calls `uiController.refreshView()`. No extra refresh hooks are needed.

### 4.2 App Manager

In the menu handler for `action_app_manager`, start `AppManagerActivity` with `Intent.FLAG_ACTIVITY_NEW_TASK`. This mirrors [`LoginActivity.java` lines 615–619](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L615-L619) verbatim.

### 4.3 View My Earned Certificates

In the click listener for the Credentials card, call `ConnectNavHelper.unlockAndGoToWorkHistory(requireActivity())` so the biometric unlock gate runs (matching the drawer's existing Work History entry point). Confirm the exact signature against existing callers of `unlockAndGoToWorkHistory`.

**Commit each of 4.1–4.3 separately** so reviewers can read them independently.

---

## Phase 5 — Edit Profile screen

Goal: a form where the user changes their name and email and saves. Phone is shown but greyed out and disabled. Cancelling or backing out with unsaved changes triggers a discard modal. Email validation fails inline with a red outline.

### 5.1 Add `email` to `updateUserProfile` and its wrapper

Edit **both** layers, in this order:

1. `ApiPersonalId.updateUserProfile(...)` ([lines 233–261](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/connect/network/ApiPersonalId.java#L233-L261)) — add `String email` between `displayName` and `secondaryPhone`. If non-null, `params.put("email", email)`.
2. `PersonalIdApiHandler.updateProfile(...)` ([lines 287–304](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/connect/network/connectId/PersonalIdApiHandler.java#L287-L304)) — same new parameter in the same position, passed through.

The only existing caller is `BaseDrawerController.uploadUserPhoto` ([line ~392](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/navdrawer/BaseDrawerController.kt#L392)), which hits the wrapper. Migrate it to named arguments with `email = null` so adjacent nulls cannot silently swap: `.updateProfile(context = ..., userName = ..., password = ..., displayName = null, email = null, secondaryPhone = null, photoAsBase64 = photoBase64)`.

### 5.2 Extract the photo update flow into `PersonalIdPhotoUpdater`

The drawer-header version of the photo flow lives in [`BaseDrawerController.kt` lines ~160–410](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/navdrawer/BaseDrawerController.kt#L160-L410) (`showUpdatePhotoConfirmationDialog`, `initTakePhotoLauncher`, `launchCameraForPhotoEdit`, `uploadUserPhoto`, plus the two `USER_PHOTO_MAX_*` constants). `loadUserPhoto` stays in `BaseDrawerController` — the helper does **not** centralize photo rendering.

Move only the update flow (confirmation → camera → upload) into a new `PersonalIdPhotoUpdater` class in `org.commcare.connect.photo`. API:

```kotlin
class PersonalIdPhotoUpdater {
    fun register(caller: ActivityResultCaller)   // call from onCreate of Activity or Fragment
    fun show(
        onSuccess: (photoBase64: String) -> Unit,
        onFailure: (PersonalIdOrConnectApiErrorCodes, Throwable?) -> Unit,
    )
}
```

`ActivityResultCaller` is implemented by both `ComponentActivity` and `Fragment`, so one `register` overload covers both call sites. `register` **must** run before the caller reaches `STARTED`.

Refactor `BaseDrawerController` to construct + register an updater and call `show` from the existing photo-tap listener. The drawer's lambdas keep its existing drawer-specific behavior — flipping `lastPhotoUploadFailed`, swapping the overlay icon between camera and warning, and showing the success/error toast. The Edit screen wires up its own instance in 5.5.

Each consumer keeps its own one-line Glide call for rendering — `loadUserPhoto` stays in `BaseDrawerController`.

Keep this commit focused on the move + reuse, with no behavior changes.

### 5.3 Layout `personalid_edit_profile_screen.xml`

Match Fig. 4 / page 8 (right side) of the design doc, top to bottom. Wrap the whole content in a `ScrollView` so the form remains usable on short screens and with the soft keyboard up.
- Circular photo **with camera-icon overlay** — reuse the `MaterialCardView` + black-60 overlay pattern from [`nav_drawer_header.xml` lines 30–64](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/res/layout/nav_drawer_header.xml#L30-L64). IDs `@+id/user_image` and `@+id/user_image_overlay_icon`. Tapping the photo launches the `PersonalIdPhotoUpdater` (wired in 5.5). This is the only screen where the photo is editable.
- Name (large) and phone (small) underneath, identical to the Profile screen, for visual continuity.
- Section header "Personal Information".
- **Name** — `TextInputLayout` wrapping a `TextInputEditText`, id `@+id/profile_input_name`, enabled.
- **Phone Number** — `TextInputLayout`, id `@+id/profile_input_phone`, `android:enabled="false"`, with a `helperText` that explains it cannot be changed. Match the greyed-out background in the Figma.
- **Email Address** — directly *above* the field add a `TextView` (id `@+id/profile_email_otp_notice`) with `android:text="@string/personalid_edit_profile_email_otp_notice"`, `android:drawableStart="@drawable/ic_personalid_warning"`, a small `drawablePadding`, gray text + `app:drawableTint` matching the gray of the Figma caption, and a small text size — matching the right side, page 8 of the design doc. The OTP verification flow itself is out of scope for this feature and will be built separately; we show the notice now so the copy is in place when that flow ships. Below the notice, the `TextInputLayout`, id `@+id/profile_input_email`, `android:inputType="textEmailAddress"`.
- Bottom row: a `Cancel` text button on the left (`@+id/btn_cancel`) and a filled `Save` button on the right (`@+id/btn_save`). Save starts disabled.

For validation errors, use `TextInputLayout`'s built-in `error` API — setting `.error = "..."` automatically applies the red outline and error caption that Material's theme drives. Use the existing `@color/red_700` (already `#D32F2F`) wherever the design calls for that color.

### 5.4 `PersonalIdEditProfileViewModel`

Constructor takes a `SavedStateHandle`; originals and current values are stored as `LiveData` via `savedStateHandle.getLiveData(KEY_...)`. This survives both rotation and process death without losing in-progress edits.

Derived booleans:
- `isModified()` — current ≠ original on any field
- `isValid()` —
  - if `originalEmail` is empty: `currentEmail` is empty or matches `android.util.Patterns.EMAIL_ADDRESS`
  - if `originalEmail` is non-empty: `currentEmail` is non-empty and matches the pattern (clearing an existing email is not allowed in MVP)
- `canSave() = isModified() && isValid()`

Expose `initialize(name, email)` (idempotent — only seeds the originals if the handle is empty), `onNameChanged(String)`, and `onEmailChanged(String)`.

When the email is invalid because it's empty but the original was non-empty, the fragment surfaces `personalid_edit_profile_error_email_required`; for malformed non-empty input it surfaces `personalid_edit_profile_error_email_invalid`.

**Tests** — `@RunWith(AndroidJUnit4::class) @Config(application = CommCareTestApplication::class)` (Robolectric, matches `ConnectJobsListViewModelTest`):
- `isModified()` flips true when name or email changes, and back to false when the field is edited back to its original.
- Only-name-modified and only-email-modified each independently trigger `isModified()`.
- `isValid()` when original email was empty: true for empty and well-formed addresses, false for malformed and whitespace-only.
- `isValid()` when original email was non-empty: clearing it is invalid; only non-empty matching addresses are valid.
- `canSave()` is the AND of `isModified()` and `isValid()`.
- `initialize()` called a second time is a no-op; in-progress edits to name/email are preserved.
- A ViewModel constructed from an already-populated `SavedStateHandle` restores both originals and current values; subsequent `initialize` does not overwrite them.

### 5.5 `PersonalIdEditProfileFragment`

- Obtain the ViewModel with `by viewModels()` (the Kotlin delegate provides a `SavedStateHandle` automatically).
- Load the `ConnectUserRecord` in `onViewCreated`, call `viewModel.initialize(user.name, user.email ?: "")` (idempotent — safe on rotation), and pre-fill all three inputs from the LiveData.
- Render the photo with Glide (same configuration as the Profile screen). Instantiate `PersonalIdPhotoUpdater` as a fragment field and call `updater.register(this)` from `onCreate` so the `ActivityResultLauncher` is registered before `STARTED`. Call `updater.show(onSuccess, onFailure)` from the photo and overlay click listeners — `onSuccess` reloads the photo via Glide and shows a success toast; `onFailure` shows the error toast via `PersonalIdOrConnectApiErrorHandler.handle(...)`. Photo changes save independently of the Name/Email form — they do not affect `isModified()` or the Save button.
- Attach a `TextWatcher` on the Name and Email `EditText`s that forwards to `onNameChanged` / `onEmailChanged`.
- Observe state on every change: `btn_save.isEnabled = viewModel.canSave()`; when the email is invalid, set `profile_input_email.error` to `personalid_edit_profile_error_email_required` (empty but original was non-empty) or `personalid_edit_profile_error_email_invalid` (malformed); otherwise clear it.
- `btn_save` click: disable the button immediately and re-enable it in both success and failure paths (prevents double-submission). Call `PersonalIdApiHandler.updateProfile` (the wrapper, same path the drawer uses) using **named arguments** — `displayName = newName, email = newEmail, secondaryPhone = null, photoAsBase64 = null`. On success, update the `ConnectUserRecord` in place, call `ConnectUserDatabaseUtil.storeUser(...)`, show a success toast, and `findNavController().popBackStack()`. On failure, surface the standard error via `PersonalIdOrConnectApiErrorHandler.handle(...)`.
- `btn_cancel` and the toolbar back arrow both call a single `handleBack()` method (see 5.6).

### 5.6 Discard confirmation on back / cancel

Register an `OnBackPressedCallback` in `onViewCreated` so the system back button routes through the same `handleBack()` as the Cancel button and the toolbar's Up arrow (intercept `android.R.id.home` in `onOptionsItemSelected` and call `handleBack()`).

`handleBack()` logic: if `!viewModel.isModified()`, `popBackStack()` immediately. Otherwise show a `StandardAlertDialog` with title/message from the new `personalid_edit_profile_discard_*` strings. Positive ("Discard") → pop. Negative ("Keep editing") → dismiss and stay.

**Verify.** Build, open Edit, exercise: prefilled values, Save disabled until a field is modified, invalid email shows red outline + caption, Save disabled while invalid, valid Save round-trips to backend and updates UI, network failure shows toast and leaves the form populated, cancel/back with changes shows discard modal, no-change back returns immediately. Run the ViewModel tests.

**Commit 5.1 alone**, **commit the photo extraction in 5.2 alone**, then **commit the Edit screen + ViewModel + tests together**, then **commit the discard-confirmation behavior**.

---

## Phase 6 — Reveal the feature and clean up

### 6.1 Reveal the drawer link

In `BaseDrawerController.refreshDrawerContent()`, replace the hard-coded `manageProfileLink.visibility = View.GONE` from Phase 1 with the real conditional, matching the existing `profileCard.visibility` line: visible when signed in, gone otherwise.

After this commit the feature is reachable in production. App Manager and Forget PersonalID are still in the login-screen 3-dot menu too — that's fine and intentional, since 6.2 removes them in a separate commit.

Manual test: sign in, open drawer — link appears under the user's name and opens the Profile screen.

### 6.2 Remove the old login-screen entries

In `LoginActivity.java`:
- Remove the `menu.add(...)` for `MENU_APP_MANAGER` and `MENU_PERSONAL_ID_FORGET` in `onCreateOptionsMenu` (lines [581](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L581) and [583](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L583)).
- Remove the corresponding `case` blocks in `onOptionsItemSelected` (lines [615–619](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L615-L619) and [623–628](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L623-L628)).
- Remove the now-unused `MENU_APP_MANAGER` and `MENU_PERSONAL_ID_FORGET` constants and their entries in `createMenuItemToAnalyticsParamMapping`.
- Remove the visibility logic for both in `onPrepareOptionsMenu` ([line ~594](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L594)).

Confirm `AppManagerActivity` has no other entry point you rely on by searching the codebase for `AppManagerActivity`. If a debug-only entry exists, leave it.

(No `LoginActivity` unit tests reference these menu constants — confirmed by grep against `app/unit-tests` and `app/instrumentation-tests`.)

Manual test: the login screen's 3-dot menu no longer shows either item; both are reachable from the Profile screen instead.

### 6.3 Docs

Add `docs/personalid/manage_profile.md` with a short overview: how to reach Profile, where user data is read from and written to, which fields are editable in MVP (name, email) vs greyed out (phone), where the photo update flow lives (`PersonalIdPhotoUpdater`), and a note that **photo updates and Name/Email Save are independent network calls** — a photo persists immediately, even if the user later discards or fails to save the form. Do not link to the design doc or internal tickets — this repo is open-source and external readers won't have access.

---

## Out of scope (do not implement)

Per the Path Forward decision in the design doc:
- Phone number editing + OTP verification — phone is greyed out only
- Rate limiting on profile updates
- Email / OTP confirmation flow for any updates (V1)
- Push notification on profile update (V1)
- Credentials & Payments info-only sections on Profile (V1)
- Updating the user's backup code (entirely out of scope)

# Manage Profile — Implementation Plan

**Design doc:** [Manage Profile](https://docs.google.com/document/d/11B3b8K92_bPQh71CVmNm8VTLVvvKid6ta4G7esSrSVc/edit?tab=t.0) — the "Path Forward" section is the source of truth (Option 2 for Profile, Option 1 for Edit Information).
**Branch:** `CCCT-2345-create-manage-profile-screen-plan`

## Goal

Give a signed-in PersonalID user a dedicated Profile screen reached from the nav drawer, plus a separate Edit screen for changing editable fields. They can:
- View name, phone, email, and their photo
- Update the photo (reusing the existing flow)
- Tap an edit pencil to change their **name** and **email** on a separate Edit screen. Email changes require OTP verification.
- Open App Manager from a 3-dot menu
- Forget their PersonalID via a destructive CTA with a confirmation modal

Forget PersonalID moves out of the Login *and* Setup 3-dot menus into the new Profile screen. App Manager stays in the Login menu — it must remain reachable for users without PersonalID — and is *also* exposed from Profile as an additional entry point.

Phone number is **not editable** in this MVP — it renders as a greyed-out, disabled field on the Edit screen (per the Path Forward).

## Architecture

A new `PersonalIdProfileActivity` hosts a small Jetpack Navigation graph with two destinations: `PersonalIdProfileFragment` (view-only) and `PersonalIdEditProfileFragment` (form). Both new fragments live in `org.commcare.fragments.personalId` and are written in Kotlin with ViewBinding + `ViewModelProvider`, matching the pattern already used by other PersonalID fragments (see `PersonalIdPhotoCaptureFragment` for reference).

The screen reads from `ConnectUserDatabaseUtil.getUser(context)` (returns a `ConnectUserRecord`) and writes via `ApiPersonalId.updateUserProfile(...)`. On a successful save we update the local `ConnectUserRecord` and `ConnectUserDatabaseUtil.storeUser(...)`.

The photo update flow that lives in `BaseDrawerController` today gets extracted into a reusable helper so both the drawer and the Edit Profile screen call the same code path.

**Unlock gate.** Tapping the drawer link runs through `PersonalIdUnlocker.unlock(activity, UnlockPolicy.ALWAYS, ...)` before launching `PersonalIdProfileActivity`.

**Email OTP flow.** When the user taps Save with a new email, a confirmation dialog explains that a verification code will be sent. On Continue, Edit first commits any pending name change via `PersonalIdApiHandler.updateProfile(displayName = ...)`; if that call fails, Edit shows the standard error toast and stays put (no OTP navigation, no email change). On a successful name save — or if the name was unchanged — Edit navigates to the shared OTP verification screen passing only the new email. That screen owns the entire verify flow: it calls `requestEmailOtp` on its own create, presents the code input, calls `verifyEmailOtp` to commit the new email, and handles every request/verify error internally (retry UX, error toasts, etc.). When the user is done with the OTP screen — whether they verified successfully or backed out — the OTP screen pops back to Edit with a single `email_verified: Boolean` result. On `true`, Edit persists the new email locally (`ConnectUserRecord.email`, `ConnectUserDatabaseUtil.storeUser`), shows a success toast, and pops to Profile. On `false`, Edit just resumes with the typed values intact; the user can retry from Save. Pure-name edits (email unchanged) skip the OTP path entirely and commit via `updateProfile` directly.

**OTP screen dependency.** Because the verification screen is owned by a separate WIP branch, this plan describes the handoff at a contract level. The implementer of Phase 5.5 must confirm three things with the in-flight feature's owners before wiring:
1. How the OTP screen is launched (preferred: a nav-graph destination added to `nav_graph_personalid_profile.xml`; fallback: `ActivityResultLauncher` if it's a separate `Activity`).
2. The arguments it accepts — `newEmail: String`. Nothing else; name is saved separately by Edit before navigation.
3. The result shape — a `FragmentResult` (or activity result) under a known key (assumed `OTP_RESULT_KEY`) containing one boolean `email_verified`. `true` means the OTP screen committed the new email server-side; `false` means the user backed out without verifying. The OTP screen does *not* dispatch error details to Edit — request and verify errors are handled inside the OTP screen.

If the OTP screen has not merged by the time Phase 5.5 is implemented, stub it behind a local test fragment that exposes a "verify success" and "back out" affordance and dispatches the boolean result so Phase 5 stays unblocked; replace the stub when the real screen lands.

**Name + email ordering caveat.** Because name commits before email and the two saves are independent, a user who saves both fields and then abandons the OTP flow (back arrow or gives up after a verify failure) will see their name change persisted while the email stays at its original value. This is acceptable given the design tradeoff — flagged here so it's not a surprise during QA and so the Phase 6 doc note captures it.

**Dark launch.** The drawer link — the only entry point to all of this work — is hard-coded to `View.GONE` from Phase 1 through Phase 5, so every intermediate phase is safely releasable. Phase 6 swaps the hard-coded `View.GONE` for the real signed-in conditional and removes the legacy `Forget PersonalID` entries from the Login and Setup menus (App Manager stays in Login).

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
- `app/res/navigation/nav_graph_personalid_profile.xml`
- Unit tests for both ViewModels in `app/unit-tests/src/org/commcare/activities/connect/viewmodel/`

**Modified**
- `app/res/layout/nav_drawer_header.xml` — add the "Manage Profile" link under the user name
- `app/src/org/commcare/navdrawer/DrawerViewRefs.kt` — bind the new link
- `app/src/org/commcare/navdrawer/BaseDrawerController.kt` — wire the click; delegate the photo flow to the new helper
- `app/src/org/commcare/activities/LoginActivity.java` — remove `Forget PersonalID` from the 3-dot menu (`App Manager` stays)
- `app/src/org/commcare/activities/CommCareSetupActivity.java` — remove `Forget PersonalID` from the 3-dot menu
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
   - `personalid_profile_forget_account`, `personalid_profile_menu_app_manager`
   - `personalid_edit_profile_error_email_invalid` ("Enter a valid email address."), `personalid_edit_profile_error_email_required` ("Email is required.")
   - `personalid_edit_profile_email_otp_notice` ("Updating your email address will require an OTP verification.")
   - Forget modal: `personalid_profile_forget_confirm_title`, `personalid_profile_forget_confirm_message`
   - Discard modal: `personalid_edit_profile_discard_title` ("Discard your changes?"), `personalid_edit_profile_discard_message` ("Your unsaved changes will be lost."), `personalid_edit_profile_discard_positive` ("Discard"), `personalid_edit_profile_discard_negative` ("Keep editing")
   - OTP confirmation modal: `personalid_edit_profile_otp_confirm_title` ("Verify your new email"), `personalid_edit_profile_otp_confirm_message` ("We'll send a 6-digit code to %1$s. Enter it on the next screen to verify the email." — note the `%1$s` placeholder for the new email; translators must preserve it), `personalid_edit_profile_otp_confirm_positive` ("Send Code"), `personalid_edit_profile_otp_confirm_negative` ("Cancel")

2. **Drawer header layout.** In `nav_drawer_header.xml`, inside the inner vertical `LinearLayout` that holds `@id/header_user_name` ([around line 71](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/res/layout/nav_drawer_header.xml#L71)), add a second `TextView` directly below with id `@+id/header_manage_profile`, text `@string/personalid_manage_profile`, small white text, and `?attr/selectableItemBackground`. The `<u>` tags in the string render the underline (Fig. 1 in the design doc).

3. **View ref.** Add `val manageProfileLink: TextView = rootView.findViewById(R.id.header_manage_profile)` to `DrawerViewRefs.kt`.

4. **Click + visibility.** In `BaseDrawerController.kt`:
   - In `setupListeners()` ([around line 132](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/navdrawer/BaseDrawerController.kt#L132)), add a click listener on `binding.manageProfileLink` that calls `closeDrawer()` and then routes through `PersonalIdUnlocker.unlock(activity, UnlockPolicy.ALWAYS) { success -> if (success) activity.startActivity(Intent(activity, PersonalIdProfileActivity::class.java)) }`. This matches the gate used by `LoginActivity.onCreate` ([line 243](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L243)) and every other PersonalID-protected entry point in the codebase.
   - In `refreshDrawerContent()` ([around line 291](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/navdrawer/BaseDrawerController.kt#L291) where `profileCard.visibility` is set), hard-code `manageProfileLink.visibility = View.GONE`. This keeps the feature invisible to users while phases 2–5 land. Phase 6 swaps this line for the real signed-in conditional.

5. **Activity stub.** Create `PersonalIdProfileActivity` extending `CommCareActivity<PersonalIdProfileActivity>` (matches `PersonalIdWorkHistoryActivity`) with an empty `onCreate` so the click target exists. Register it in `AndroidManifest.xml`, mirroring the `PersonalIdWorkHistoryActivity` entry.

6. **Verify.** Build the debug variant with `./gradlew :app:assembleCommcareDebug`. Install, sign in, open the drawer — the link should **not** appear (it's pinned to `View.GONE`). To smoke-test the click wiring, temporarily change the line to `View.VISIBLE`, rebuild, confirm the link appears and tapping it opens the empty activity, then revert to `View.GONE` before committing.

**Commit** when the build is clean and manual smoke passes. Use a single commit for Phase 1.

---

## Phase 2 — Profile activity + navigation graph

Goal: replace the stub activity with a proper host that owns the Profile and Edit Profile destinations.

1. **Nav graph.** Create `nav_graph_personalid_profile.xml` with two `<fragment>` destinations — `personalid_profile_fragment` (start, `android:label="@string/personalid_profile_title"`) and `personalid_edit_profile_fragment` (`android:label="@string/personalid_edit_profile_title"`) — and an action `action_profile_to_edit_profile` between them. Mirror the structure of `nav_graph_personalid.xml`. `NavigationUI` reads these labels for the toolbar title.

2. **Activity layout.** `activity_personalid_profile.xml` is a vertical `LinearLayout` containing `<include layout="@layout/appbar_layout"/>` at the top and a `FragmentContainerView` (`@+id/profile_nav_host`, `app:defaultNavHost="true"`, `app:navGraph="@navigation/nav_graph_personalid_profile"`) below it. The toolbar stays fixed; each destination fragment wraps its own content in a `ScrollView`.

3. **Activity.** Replace the stub with `class PersonalIdProfileActivity : CommCareActivity<PersonalIdProfileActivity>()`. In `onCreate`, look up the `NavHostFragment` by `R.id.profile_nav_host` and call `NavigationUI.setupActionBarWithNavController(this, navController)` so the toolbar shows the destination label and the back arrow. Override `onSupportNavigateUp()` to delegate to `navController.navigateUp()`. `CommCareActivity` provides the `SupportActionBar` from the `appbar_layout` include — no manual `setSupportActionBar` call needed.

4. **Placeholder fragments.** Create both Kotlin fragment files with empty `onCreateView` returning a dummy view so the nav graph compiles. They will be filled in by Phases 3 and 5.

5. **Verify.** Build, open Profile from the drawer — the empty fragment renders inside a CommCare-themed toolbar. The toolbar title reads "Profile".

**Commit.**

---

## Phase 3 — Profile screen (view-only)

Goal: render the user's photo, name, phone, email, the Forget CTA, and the 3-dot/edit menu, as shown in Fig. 6 (left side) of the design doc.

1. **Layout `personalid_profile_screen.xml`.** Top to bottom, wrapped in a `ScrollView`:
   - Circular photo, no overlay and not clickable — a `MaterialCardView` with `app:cardCornerRadius="50dp"` wrapping a centered `ImageView` (id `@+id/profile_user_image`). The photo is read-only on this screen; the camera-icon overlay only appears on the Edit screen.
   - Below the photo: name (large, centered) and primary phone (small, centered). IDs `profile_name`, `profile_phone_subtitle`.
   - Section header "Personal Information".
   - Three read-only rows (small label above value): Name, Phone Number, Email Address. Use IDs `profile_value_name`, `profile_value_phone`, `profile_value_email`. Match the Figma row style — see Fig. 6.
   - Forget PersonalID button (`@+id/profile_btn_forget_personalid`) styled as a destructive action — `@color/red_700` background, white text, full-width, near the bottom.

   The Credentials / "View My Earned Certificates" card from the design doc is intentionally omitted in MVP — users already reach Work History from the nav drawer, so a second entry point adds no value here.

   The toolbar lives in the host activity, not in this layout.

2. **Menu `personalid_profile_menu.xml`.** Two items:
   - `@+id/action_edit_profile` — `showAsAction="ifRoom"`, pencil icon. Check the existing drawables before creating a new one; a generic edit icon may already exist.
   - `@+id/action_app_manager` — `showAsAction="never"` so it lands in the overflow.

3. **`PersonalIdProfileViewModel`.** A small `ViewModel` exposing a `LiveData<UiState>` with `name`, `primaryPhone`, `email`, and `photoBase64`. It loads from `ConnectUserDatabaseUtil.getUser(context)` and maps the record to the UI state. Pull the mapping out into a pure `companion object` function so it is trivially unit-testable: `fun toUiState(record: ConnectUserRecord): UiState`.

   **Tests** (`PersonalIdProfileViewModelTest`): one test asserts the four fields map through correctly; one asserts `null` email falls back to an empty string.

4. **`PersonalIdProfileFragment`.** Wire ViewBinding, instantiate the ViewModel via `ViewModelProvider`, observe the LiveData, and render. Load the photo with Glide using the same configuration as `BaseDrawerController.loadUserPhoto` (placeholder = `R.drawable.nav_drawer_person_avatar`). The photo `ImageView` has no click listener on this screen. Call `setHasOptionsMenu(true)` and inflate `personalid_profile_menu.xml` in `onCreateOptionsMenu`. Hook `action_edit_profile` to navigate via `findNavController().navigate(R.id.action_profile_to_edit_profile)`. Leave `action_app_manager` and the Forget button as stubs — Phase 4 wires them.

5. **Verify.** Open the Profile screen; the user's real name, phone, email and photo all render. Tap the edit pencil — it navigates to the empty Edit fragment. Run the ViewModel tests with `./gradlew :app:testCommcareDebugUnitTest`.

**Commit.**

---

## Phase 4 — Profile screen actions

Two small pieces of behavior, each independently testable.

### 4.1 Forget PersonalID with confirmation modal

Wire `profile_btn_forget_personalid` to show a `StandardAlertDialog` (the same dialog class used by `BaseDrawerController.showUpdatePhotoConfirmationDialog` [around line 165](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/navdrawer/BaseDrawerController.kt#L165)). Title and message: `personalid_profile_forget_confirm_title`, `personalid_profile_forget_confirm_message`. Positive button calls `PersonalIdManager.getInstance().forgetUser(reason)`, then starts `DispatchActivity` with `Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK` and finishes the Profile activity. Routing through `DispatchActivity` rather than just calling `finish()` unwinds any PersonalID-gated host activity underneath.

For the analytics `reason` argument, add a new constant on `AnalyticsParamValue` (e.g. `PERSONAL_ID_FORGOT_USER_PROFILE_PAGE`) so the Profile-screen trigger is distinguishable in analytics from the (now-removed) legacy login- and setup-screen triggers.

Manual test: tapping the button shows the modal; Cancel dismisses; Forget wipes local state and routes through `DispatchActivity`, landing on Login/Setup with the drawer in its signed-out appearance. Exercise the case where Profile was opened from a PersonalID-gated screen (e.g. Messaging) — after Forget, the user should not be able to back-stack to that screen.

### 4.2 App Manager

In the menu handler for `action_app_manager`, start `AppManagerActivity` with `Intent.FLAG_ACTIVITY_NEW_TASK`. This mirrors [`LoginActivity.java` lines 615–619](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L615-L619) verbatim.

**Commit each of 4.1–4.2 separately** so reviewers can read them independently.

---

## Phase 5 — Edit Profile screen

Goal: a form where the user changes their name and/or email and saves. Phone is shown but greyed out and disabled. Email validation fails inline with a red outline. A Save tap that includes a new email triggers an OTP confirmation dialog and hands off to the shared OTP verification screen (see 5.5); a Save tap with name-only changes commits directly via `PersonalIdApiHandler.updateProfile`. Cancelling or backing out with unsaved changes triggers a discard modal.

### 5.1 Extract the photo update flow into `PersonalIdPhotoUpdater`

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

Refactor `BaseDrawerController` to construct + register an updater and call `show` from the existing photo-tap listener. The drawer's lambdas keep its existing drawer-specific behavior — flipping `lastPhotoUploadFailed`, swapping the overlay icon between camera and warning, and showing the success/error toast. The Edit screen wires up its own instance in 5.4.

Each consumer keeps its own one-line Glide call for rendering — `loadUserPhoto` stays in `BaseDrawerController`.

Keep this commit focused on the move + reuse, with no behavior changes.

### 5.2 Layout `personalid_edit_profile_screen.xml`

Match Fig. 4 / page 8 (right side) of the design doc, top to bottom. Wrap the whole content in a `ScrollView` so the form remains usable on short screens and with the soft keyboard up.
- Circular photo **with camera-icon overlay** — reuse the `MaterialCardView` + black-60 overlay pattern from [`nav_drawer_header.xml` lines 30–64](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/res/layout/nav_drawer_header.xml#L30-L64). IDs `@+id/user_image` and `@+id/user_image_overlay_icon`. Tapping the photo launches the `PersonalIdPhotoUpdater` (wired in 5.4). This is the only screen where the photo is editable.
- Name (large) and phone (small) underneath, identical to the Profile screen, for visual continuity.
- Section header "Personal Information".
- **Name** — `TextInputLayout` wrapping a `TextInputEditText`, id `@+id/profile_input_name`, enabled.
- **Phone Number** — `TextInputLayout`, id `@+id/profile_input_phone`, `android:enabled="false"`, with a `helperText` that explains it cannot be changed. Match the greyed-out background in the Figma.
- **Email Address** — directly *above* the input wrapper (i.e. between the "Email Address" field label and the input box) add a `TextView` (id `@+id/profile_email_otp_notice`) with `android:text="@string/personalid_edit_profile_email_otp_notice"`, `android:drawableStart="@drawable/ic_personalid_warning"`, a small `drawablePadding`, gray text + `app:drawableTint` matching the gray Figma caption, and a small text size — matching the right side, page 8 of the design doc. Below the notice, the `TextInputLayout`, id `@+id/profile_input_email`, enabled, `android:inputType="textEmailAddress"`. Inline validation errors are surfaced via `TextInputLayout.error` using the strings from Phase 1.
- Bottom row: a `Cancel` text button on the left (`@+id/btn_cancel`) and a filled `Save` button on the right (`@+id/btn_save`). Save starts disabled.

For validation errors, use `TextInputLayout`'s built-in `error` API — setting `.error = "..."` automatically applies the red outline and error caption that Material's theme drives.

### 5.3 `PersonalIdEditProfileViewModel`

Constructor takes a `SavedStateHandle`; originals (`originalName`, `originalEmail`) and current values (`currentName`, `currentEmail`) are stored as `LiveData` via `savedStateHandle.getLiveData(KEY_...)`. This survives rotation, process death, *and* the round-trip to the OTP screen — when Edit comes back into view, the typed name and email are intact. Phone is display-only and the ViewModel never reads or writes it.

Derived booleans:
- `isNameModified()` — `currentName != originalName`
- `isEmailModified()` — `currentEmail != originalEmail`
- `isModified() = isNameModified() || isEmailModified()`
- `isNameValid() = currentName.isNotBlank()` — name cannot be cleared to empty
- `isEmailValid()` —
  - if `originalEmail` is empty: `currentEmail` is empty or matches `android.util.Patterns.EMAIL_ADDRESS`
  - if `originalEmail` is non-empty: `currentEmail` is non-empty and matches the pattern (clearing an existing email is not allowed in MVP)
- `canSave() = isModified() && isNameValid() && isEmailValid()`

Expose `initialize(name, email)` (idempotent — only seeds the originals if the handle is empty), `onNameChanged(String)`, and `onEmailChanged(String)`.

When the email is invalid because it's empty but the original was non-empty, the fragment surfaces `personalid_edit_profile_error_email_required`; for malformed non-empty input it surfaces `personalid_edit_profile_error_email_invalid`.

**Tests** — `@RunWith(AndroidJUnit4::class) @Config(application = CommCareTestApplication::class)` (Robolectric, matches `ConnectJobsListViewModelTest`):
- `isNameModified()` and `isEmailModified()` each flip true independently when their field changes, and back to false when edited back to original. `isModified()` is their OR.
- `isNameValid()` is false for blank names, true otherwise.
- `isEmailValid()` when original email was empty: true for empty and well-formed addresses, false for malformed and whitespace-only.
- `isEmailValid()` when original email was non-empty: clearing it is invalid; only non-empty matching addresses are valid.
- `canSave()` is the AND of `isModified()`, `isNameValid()`, and `isEmailValid()`.
- `initialize()` called a second time is a no-op; in-progress edits to name and email are preserved.
- A ViewModel constructed from an already-populated `SavedStateHandle` restores both originals and current values; a subsequent `initialize` does not overwrite them.

### 5.4 `PersonalIdEditProfileFragment`

- Obtain the ViewModel with `by viewModels()` (the Kotlin delegate provides a `SavedStateHandle` automatically).
- Load the `ConnectUserRecord` in `onViewCreated`, call `viewModel.initialize(user.name, user.email ?: "")` (idempotent — safe on rotation *and* on return from the OTP screen), and pre-fill the Name and Email inputs from the LiveData. Populate the disabled Phone field directly from the record (no LiveData needed — it never changes on this screen).
- Render the photo with Glide (same configuration as the Profile screen). Instantiate `PersonalIdPhotoUpdater` as a fragment field and call `updater.register(this)` from `onCreate` so the `ActivityResultLauncher` is registered before `STARTED`. Call `updater.show(onSuccess, onFailure)` from the photo and overlay click listeners — `onSuccess` reloads the photo via Glide and shows a success toast; `onFailure` shows the error toast via `PersonalIdOrConnectApiErrorHandler.handle(...)`. Photo changes save independently of the Name/Email form — they do not affect `isModified()` or the Save button.
- Attach `TextWatcher`s on the Name and Email `EditText`s that forward to `onNameChanged` / `onEmailChanged`.
- Observe state on every change: `btn_save.isEnabled = viewModel.canSave()`; when the email is invalid, set `profile_input_email.error` to `personalid_edit_profile_error_email_required` (empty but original was non-empty) or `personalid_edit_profile_error_email_invalid` (malformed); otherwise clear it.
- `btn_save` click branches on whether email is dirty:
   - **Name-only path** (`!viewModel.isEmailModified()`): disable the button immediately and re-enable it in both success and failure paths (prevents double-submission). Call `PersonalIdApiHandler.updateProfile` (the wrapper, same path the drawer uses) using **named arguments** — `displayName = newName, secondaryPhone = null, photoAsBase64 = null` — so adjacent nulls cannot silently swap. On success, update the `ConnectUserRecord` in place, call `ConnectUserDatabaseUtil.storeUser(...)`, show a success toast, and `findNavController().popBackStack()`. On failure, surface the standard error via `PersonalIdOrConnectApiErrorHandler.handle(...)`.
   - **Email path** (`viewModel.isEmailModified()`): hand off to the OTP confirmation flow in 5.5. 5.5 handles both the optional name save (if name is also dirty) and the OTP screen handoff; do not call `updateProfile` from inside this branch.
- `btn_cancel` and the toolbar back arrow both call a single `handleBack()` method (see 5.6).

### 5.5 OTP confirmation dialog and handoff to the verification screen

This subsection covers the Save-tap path when `viewModel.isEmailModified()` is true. **Before implementing, confirm the OTP screen contract with the in-flight email feature's owners (see the "OTP screen dependency" note in Architecture).** Stub the OTP screen with a local test fragment if it has not merged yet.

1. **Confirmation dialog.** Show a `StandardAlertDialog` (the same dialog class used elsewhere in the Profile feature) populated from the Phase 1 strings:
   - Title: `personalid_edit_profile_otp_confirm_title`
   - Message: `getString(personalid_edit_profile_otp_confirm_message, currentEmail)` — the `%1$s` placeholder is filled with the new email.
   - Positive button: `personalid_edit_profile_otp_confirm_positive` → continues to step 2.
   - Negative button: `personalid_edit_profile_otp_confirm_negative` → dismisses the dialog. The Edit screen state is unchanged (typed name and email are preserved); the Save button stays enabled.

2. **Commit the name change first (if dirty).** On Continue:
   - Disable `btn_save` immediately (prevents double-tap; re-enabled on failure or on return from OTP — see step 5).
   - If `viewModel.isNameModified()`: call `PersonalIdApiHandler.updateProfile(displayName = currentName, secondaryPhone = null, photoAsBase64 = null)` using **named arguments**. On success, update the `ConnectUserRecord.name` in place via `ConnectUserDatabaseUtil.storeUser(...)` (so the new name is reflected on Profile when control returns), then proceed to step 3. On failure, re-enable `btn_save`, surface the standard error via `PersonalIdOrConnectApiErrorHandler.handle(...)`, and **stop** — do not navigate to the OTP screen. The typed name and email are preserved; the user can retry from Save.
   - If name is *not* dirty: skip directly to step 3.

3. **Navigate to the OTP screen.** Hand off via the navigation graph if the OTP screen is a `Fragment` destination (preferred — add the destination and an `action_edit_profile_to_otp` to `nav_graph_personalid_profile.xml`) or via an `ActivityResultLauncher` if it's an `Activity` (fallback). Pass one argument: `newEmail: String` — the email being verified. The OTP screen calls `requestEmailOtp` on its own create; Edit does not.

4. **Register the result listener.** In `onCreate` (before `STARTED`), call `setFragmentResultListener(OTP_RESULT_KEY) { _, bundle -> ... }`. The bundle's expected shape (confirm with the in-flight feature): a single boolean `email_verified`.
   - `email_verified = true`: the OTP screen has committed the new email server-side. Update the local `ConnectUserRecord.email` in place via `ConnectUserDatabaseUtil.storeUser(...)`, show a success toast, and `findNavController().popBackStack()` to land on Profile.
   - `email_verified = false`: the user backed out of the OTP screen without verifying (the OTP screen handled any request/verify errors internally — Edit does not need to show a toast). Re-enable `btn_save`. The typed name and email are still in the form (the ViewModel's `SavedStateHandle` survived the round-trip); the user can retry from Save.

5. **Re-enabling Save on return.** Whether the listener fires or not, the simplest and idempotent guard against a permanently-disabled Save button is: in `onResume`, set `btn_save.isEnabled = viewModel.canSave()`. This covers both the result-listener paths above and the (theoretical) case where the OTP screen pops back without dispatching a result at all.

### 5.6 Discard confirmation on back / cancel

Register an `OnBackPressedCallback` in `onViewCreated` so the system back button routes through the same `handleBack()` as the Cancel button and the toolbar's Up arrow (intercept `android.R.id.home` in `onOptionsItemSelected` and call `handleBack()`).

`handleBack()` logic: if `!viewModel.isModified()`, `popBackStack()` immediately. Otherwise show a `StandardAlertDialog` with title/message from the new `personalid_edit_profile_discard_*` strings. Positive ("Discard") → pop. Negative ("Keep editing") → dismiss and stay.

**Verify.** Build, open Edit, exercise the following matrix:
- Prefilled name and email, Phone disabled.
- Save is disabled until *any* field is modified to a valid value; disabled when name is blanked, disabled when email is malformed or (originally non-empty and now) cleared.
- Invalid email shows the red outline + caption.
- Name-only Save round-trips to backend, updates the local record, toasts, and pops back to Profile.
- Name-only Save with a network failure shows the toast and leaves the form populated.
- Email-only Save shows the OTP confirmation dialog with the new email in the message. Cancel dismisses the dialog with no state change. Continue navigates straight to the OTP screen.
- Combined name+email Save → confirmation dialog → Continue → name save succeeds → OTP screen appears. The new name is already persisted at this point (visible on Profile if you back-stack out before verifying).
- Combined name+email Save with a failing name save: error toast on Edit, no OTP navigation, both typed values still in the form, Save re-enables.
- OTP `email_verified = true` result: local email updates, success toast, pops back to Profile in view mode with the new email reflected.
- OTP `email_verified = false` result (user backed out): Edit resumes with all typed values preserved, no toast (the OTP screen owns request/verify error UX), Save re-enables.
- Cancel/back with any field modified shows the discard modal; no-change back returns immediately.

Run the ViewModel tests with `./gradlew :app:testCommcareDebugUnitTest`.

**Commit the photo extraction in 5.1 alone**, then **commit the Edit screen + ViewModel + tests together** (5.2–5.4), then **commit the OTP confirmation + handoff** (5.5), then **commit the discard-confirmation behavior** (5.6).

---

## Phase 6 — Reveal the feature and clean up

### 6.1 Reveal the drawer link

In `BaseDrawerController.refreshDrawerContent()`, replace the hard-coded `manageProfileLink.visibility = View.GONE` from Phase 1 with the real conditional, matching the existing `profileCard.visibility` line: visible when signed in, gone otherwise.

After this commit the feature is reachable in production. `Forget PersonalID` is still in the Login and Setup 3-dot menus — that's fine and intentional, since 6.2 removes both in a separate commit. `App Manager` stays in the Login menu by design (Profile's overflow is an additional entry point, not a replacement).

Manual test: sign in, open drawer — link appears under the user's name and opens the Profile screen.

### 6.2 Remove the old Forget PersonalID entries

App Manager stays in `LoginActivity` — it must remain reachable for users without PersonalID. Profile keeps its own App Manager menu item as an additional, signed-in-only entry point. Only `Forget PersonalID` is removed from the legacy menus, and it is removed from **both** Login and Setup.

In `LoginActivity.java`:
- Remove the `menu.add(...)` for `MENU_PERSONAL_ID_FORGET` in `onCreateOptionsMenu` ([line 583](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L583)).
- Remove the corresponding `case MENU_PERSONAL_ID_FORGET` block in `onOptionsItemSelected` ([lines 623–628](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L623-L628)).
- Remove the now-unused `MENU_PERSONAL_ID_FORGET` constant and its entry in `createMenuItemToAnalyticsParamMapping`.
- Remove the `MENU_PERSONAL_ID_FORGET` visibility logic in `onPrepareOptionsMenu` ([line ~594](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L594)).
- Leave `MENU_APP_MANAGER`, its `menu.add(...)` ([line 581](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L581)), its `case` block ([lines 615–619](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/LoginActivity.java#L615-L619)), and its analytics mapping entry untouched.

In `CommCareSetupActivity.java`:
- Remove the `menu.add(...)` for `MENU_PERSONAL_ID_FORGET` in `onCreateOptionsMenu` ([line 500](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/CommCareSetupActivity.java#L500)).
- Remove the corresponding `case MENU_PERSONAL_ID_FORGET` block in `onOptionsItemSelected` ([lines 643–644](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/CommCareSetupActivity.java#L643-L644)).
- Remove the now-unused `MENU_PERSONAL_ID_FORGET` constant ([line 132](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/CommCareSetupActivity.java#L132)) and its entry in `createMenuItemToAnalyticsParamMapping` ([line ~660](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/CommCareSetupActivity.java#L660)).
- Remove the `MENU_PERSONAL_ID_FORGET` visibility logic in `onPrepareOptionsMenu` ([line ~508](https://github.com/dimagi/commcare-android/blob/ed81450acba5615de8aeb7bf1da0951eb586f331/app/src/org/commcare/activities/CommCareSetupActivity.java#L508)).

(No unit or instrumentation tests reference these menu constants — confirmed by grep against `app/unit-tests` and `app/instrumentation-tests`.)

Manual test: neither the Login nor the Setup 3-dot menu shows `Forget PersonalID`; the action is reachable from the Profile screen instead. The Login menu still shows `App Manager`.

### 6.3 Docs

Add `docs/personalid/manage_profile.md` with a short overview: how to reach Profile, where user data is read from and written to, which fields are editable in MVP (name and email) vs greyed out (phone), where the photo update flow lives (`PersonalIdPhotoUpdater`), and notes that:
- **Photo updates save independently of the Name/Email form** — a photo persists immediately, even if the user later discards or fails to save the form.
- **Email changes go through an OTP verification screen** owned by a separate email feature. Document the navigation contract: Edit passes only `newEmail`; the OTP screen returns a single `email_verified` boolean via `OTP_RESULT_KEY`. The OTP screen owns the `requestEmailOtp` call, the `verifyEmailOtp` call, and all request/verify error UX.
- **Name and email saves are independent.** When both are dirty, Edit commits the name first via `updateProfile`, then navigates to the OTP screen for the email. A user who saves both and then abandons the OTP flow will see their name change persisted while their email stays at its original value — that's intentional, not a bug.

Do not link to the design doc or internal tickets — this repo is open-source and external readers won't have access.

---

## Out of scope (do not implement)

Per the Path Forward decision in the design doc, plus the scoping calls made in PR review:
- Phone number editing + OTP verification — phone is greyed out only
- The OTP verification screen itself, including the `requestEmailOtp` call, the `verifyEmailOtp` call, and all request/verify error UX — owned by a separate in-flight email feature. This plan integrates with that screen via a navigation contract (`newEmail` in, `email_verified` boolean out) but does not build it.
- Credentials / "View My Earned Certificates" card on the Profile screen — users already reach Work History from the nav drawer
- Rate limiting on profile updates
- Push notification on profile update (V1)
- Credentials & Payments info-only sections on Profile (V1)
- Updating the user's backup code (entirely out of scope)

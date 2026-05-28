# Manage Profile — Implementation Plan

**Design doc:** [Manage Profile](https://docs.google.com/document/d/11B3b8K92_bPQh71CVmNm8VTLVvvKid6ta4G7esSrSVc/edit?tab=t.0) — "Path Forward" section is the source of truth (Option 2 for Profile, Option 1 for Edit Information).
**Branch:** `CCCT-2345-create-manage-profile-screen-plan`

## Goal

A dedicated Profile screen reached from the nav drawer, plus a separate Edit screen for changing editable fields. Users can:
- View name, phone, email, photo
- Update the photo (reusing the existing flow)
- Tap the edit pencil to change **name** and **email** on the Edit screen. Email changes go through OTP verification.
- Open App Manager from a 3-dot menu
- Forget their PersonalID via a destructive CTA with a confirmation modal

Forget PersonalID moves out of the Login *and* Setup 3-dot menus into the new Profile screen. App Manager stays in the Login menu (it must remain reachable for users without PersonalID) and is also exposed from Profile as an additional entry point.

Phone number is **not editable** in this MVP — it renders as a disabled field on the Edit screen.

## Architecture

A new `PersonalIdProfileActivity` hosts a Jetpack Navigation graph with two destinations: `PersonalIdProfileFragment` (view-only) and `PersonalIdEditProfileFragment` (form). Both new fragments live in `org.commcare.fragments.personalId` and are written in Kotlin with ViewBinding + `ViewModelProvider`, matching the pattern used by other PersonalID fragments (see `PersonalIdPhotoCaptureFragment`).

Reads come from `ConnectUserDatabaseUtil.getUser(context)` (returns `ConnectUserRecord`). Name and photo writes go through `PersonalIdApiHandler.updateProfile(...)` followed by `ConnectUserDatabaseUtil.storeUser(...)`. Email writes are owned by the email verification fragment (see below).

The photo update flow currently in `BaseDrawerController` gets extracted into a reusable helper so both the drawer and Edit Profile call the same code path.

**Unlock gate.** Tapping the drawer link routes through `PersonalIdUnlocker.unlock(activity, UnlockPolicy.ALWAYS, ...)` before launching `PersonalIdProfileActivity`.

**Email flow.** On Save with a dirty email, Edit:
1. Shows a confirmation dialog ("we'll send a code to <email>").
2. On Continue: commits any pending name change via `PersonalIdApiHandler.updateProfile(displayName = ...)`. On failure, toast and stop (no OTP).
3. Calls `EmailHelper.sendEmailOtp(activity, newEmail, EmailWorkFlow.EXISTING_USER, sessionData = null, onSuccess, onFailure)`. The helper toasts "A 6-digit passcode has been sent…" on success and the standard error on failure.
4. On send success, navigates to `PersonalIdEmailVerificationFragment` via a nav-graph action passing `email = newEmail, workflow = EXISTING_USER`.

The verification fragment owns the rest: code input, retries, the "proceed without email" dialog after 3 failed attempts, and the success path. On successful verify with `EXISTING_USER`, the fragment writes `ConnectUserRecord.email`, calls `ConnectUserDatabaseUtil.storeUser`, shows the "Email Added" dialog, and calls `requireActivity().finish()` on OK. On user abandon (3 failures → skip, or system back through the dialog), the fragment also calls `finish()`. Either way, `PersonalIdProfileActivity` exits and the user lands at the drawer's host activity. To see the updated state, they reopen Profile.

If the user system-backs out of the verification fragment *without* going through a dialog, the nav stack pops to Edit. `onResume` re-applies `btn_save.isEnabled = viewModel.canSave()` so the form is usable again.

**Name + email ordering caveat.** Name commits before OTP send. A user who Saves both fields and then abandons verification will have the name change persisted but the email unchanged. Documented in Phase 6.3; flagged for QA.

**Dark launch.** The drawer link is hard-coded to `View.GONE` from Phase 1 through Phase 5. Phase 6 swaps it for the real signed-in conditional and removes the legacy `Forget PersonalID` entries from Login and Setup (App Manager stays in Login).

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
- `app/res/layout/nav_drawer_header.xml` — add the "Manage Profile" link
- `app/src/org/commcare/navdrawer/DrawerViewRefs.kt` — bind the new link
- `app/src/org/commcare/navdrawer/BaseDrawerController.kt` — wire the click; delegate photo flow to the helper
- `app/src/org/commcare/activities/LoginActivity.java` — remove `Forget PersonalID` from the 3-dot menu (`App Manager` stays)
- `app/src/org/commcare/activities/CommCareSetupActivity.java` — remove `Forget PersonalID` from the 3-dot menu
- `app/AndroidManifest.xml` — register `PersonalIdProfileActivity`
- `app/res/values/strings.xml` — new strings (see Phase 1)

**Reused (unchanged)**
- `PersonalIdEmailVerificationFragment` + `fragment_personalid_email_verification` (added as a destination in our nav graph)
- `EmailHelper` (`sendEmailOtp`, `isValidEmail`)
- `EmailWorkFlow.EXISTING_USER`

---

## Phase 1 — Sidebar "Manage Profile" link

Goal: a tappable "Manage Profile" subtitle under the user's name in the drawer header that opens the new (empty for now) Profile activity.

1. **Strings.** Add to `app/res/values/strings.xml`, following the `personalid_*` naming convention:
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
   - OTP confirmation modal: `personalid_edit_profile_otp_confirm_title` ("Verify your new email"), `personalid_edit_profile_otp_confirm_message` ("We'll send a 6-digit code to %1$s. Enter it on the next screen to verify." — `%1$s` is the new email; translators must preserve it), `personalid_edit_profile_otp_confirm_positive` ("Send Code"), `personalid_edit_profile_otp_confirm_negative` ("Cancel")

2. **Drawer header layout.** In `nav_drawer_header.xml`, inside the inner vertical `LinearLayout` holding `@id/header_user_name`, add a `TextView` directly below with id `@+id/header_manage_profile`, text `@string/personalid_manage_profile`, small white text, `?attr/selectableItemBackground`. The `<u>` tags render the underline (Fig. 1 in the design doc).

3. **View ref.** Add `val manageProfileLink: TextView = rootView.findViewById(R.id.header_manage_profile)` to `DrawerViewRefs.kt`.

4. **Click + visibility.** In `BaseDrawerController.kt`:
   - In `setupListeners()`, add a click listener on `binding.manageProfileLink`: `closeDrawer()` then `PersonalIdUnlocker.unlock(activity, UnlockPolicy.ALWAYS) { success -> if (success) activity.startActivity(Intent(activity, PersonalIdProfileActivity::class.java)) }`.
   - In `refreshDrawerContent()` (where `profileCard.visibility` is set), hard-code `manageProfileLink.visibility = View.GONE`. Phase 6 swaps this for the real signed-in conditional.

5. **Activity stub.** Create `PersonalIdProfileActivity` extending `CommCareActivity<PersonalIdProfileActivity>` (matches `PersonalIdWorkHistoryActivity`) with an empty `onCreate`. Register it in `AndroidManifest.xml`, mirroring the `PersonalIdWorkHistoryActivity` entry.

6. **Verify.** `./gradlew :app:assembleCommcareDebug`. Install, sign in, open drawer — link should **not** appear. To smoke-test click wiring, temporarily set `View.VISIBLE`, verify the link opens the empty activity, then revert to `View.GONE`.

**Commit** Phase 1 as a single commit.

---

## Phase 2 — Profile activity + nav graph

Goal: replace the stub activity with a proper host that owns the Profile, Edit Profile, and Email Verification destinations.

1. **Nav graph.** Create `nav_graph_personalid_profile.xml` with three `<fragment>` destinations:
   - `personalid_profile_fragment` (start, label `@string/personalid_profile_title`)
   - `personalid_edit_profile_fragment` (label `@string/personalid_edit_profile_title`)
   - `personalid_email_verification_profile` — `android:name="org.commcare.fragments.personalId.PersonalIdEmailVerificationFragment"`, label `@string/personalid_email_verification_appbar_title`, layout `@layout/fragment_personalid_email_verification`. Declare two args: `email` (string) and `workflow` (`org.commcare.fragments.personalId.EmailWorkFlow`).

   Actions:
   - `action_profile_to_edit_profile` (from profile → edit)
   - `action_edit_profile_to_email_verification` (from edit → verification)

   Use a distinct destination id (`personalid_email_verification_profile`) so it doesn't collide with the same fragment's entry in `nav_graph_personalid.xml`. The verification fragment's only outgoing transitions (`navigateToPhotoCapture`, `navigateToMessageDisplay`) are reached only by `REGISTRATION`/`RECOVERY` workflows, so we don't need to add those actions to our graph.

2. **Activity layout.** `activity_personalid_profile.xml` is a vertical `LinearLayout` with `<include layout="@layout/appbar_layout"/>` on top and a `FragmentContainerView` (`@+id/profile_nav_host`, `app:defaultNavHost="true"`, `app:navGraph="@navigation/nav_graph_personalid_profile"`) below. Each destination wraps its own content in a `ScrollView`.

3. **Activity.** Replace the stub with `class PersonalIdProfileActivity : CommCareActivity<PersonalIdProfileActivity>()`. In `onCreate`, look up the `NavHostFragment` by `R.id.profile_nav_host` and call `NavigationUI.setupActionBarWithNavController(this, navController)`. Override `onSupportNavigateUp()` to delegate to `navController.navigateUp()`. `CommCareActivity` provides the `SupportActionBar` from the `appbar_layout` include — no manual `setSupportActionBar` needed.

4. **Placeholder fragments.** Create both Kotlin fragment files with empty `onCreateView` returning a dummy view so the nav graph compiles.

5. **Verify.** Build, open Profile from the drawer — empty fragment renders inside a CommCare-themed toolbar with title "Profile".

**Commit.**

---

## Phase 3 — Profile screen (view-only)

Goal: render photo, name, phone, email, the Forget CTA, and the 3-dot/edit menu (Fig. 6 left in the design doc).

1. **Layout `personalid_profile_screen.xml`** (wrapped in `ScrollView`):
   - Circular photo, no overlay, not clickable — `MaterialCardView` `app:cardCornerRadius="50dp"` wrapping a centered `ImageView` (`@+id/profile_user_image`). Photo edits live on the Edit screen only.
   - Below photo: name (large, centered) and primary phone (small, centered). Ids `profile_name`, `profile_phone_subtitle`.
   - Section header "Personal Information".
   - Three read-only rows (small label above value) for Name, Phone Number, Email Address. Ids `profile_value_name`, `profile_value_phone`, `profile_value_email`.
   - Forget PersonalID button (`@+id/profile_btn_forget_personalid`) styled as a destructive action — `@color/red_700` background, white text, full-width, near bottom.

   The Credentials / "View My Earned Certificates" card from the design doc is omitted in MVP — users already reach Work History from the drawer.

   Toolbar lives in the host activity, not in this layout.

2. **Menu `personalid_profile_menu.xml`**:
   - `@+id/action_edit_profile` — `showAsAction="ifRoom"`, pencil icon (check existing drawables first).
   - `@+id/action_app_manager` — `showAsAction="never"`.

3. **`PersonalIdProfileViewModel`.** Exposes `LiveData<UiState>` with `name`, `primaryPhone`, `email`, `photoBase64`. Loads from `ConnectUserDatabaseUtil.getUser(context)`. Pull the mapping into a pure `companion object` function for testability: `fun toUiState(record: ConnectUserRecord): UiState`.

   **Tests** (`PersonalIdProfileViewModelTest`): one asserts all four fields map correctly; one asserts `null` email falls back to empty string.

4. **`PersonalIdProfileFragment`.** Wire ViewBinding, instantiate the ViewModel, observe the LiveData, render. Load the photo with Glide using the same config as `BaseDrawerController.loadUserPhoto` (placeholder `R.drawable.nav_drawer_person_avatar`). No click listener on the photo. Inflate `personalid_profile_menu.xml` in `onCreateOptionsMenu` after `setHasOptionsMenu(true)`. Hook `action_edit_profile` to `findNavController().navigate(R.id.action_profile_to_edit_profile)`. Leave `action_app_manager` and the Forget button as stubs — Phase 4 wires them.

5. **Verify.** Open Profile; real name/phone/email/photo render. Edit pencil navigates to the empty Edit fragment. `./gradlew :app:testCommcareDebugUnitTest`.

**Commit.**

---

## Phase 4 — Profile screen actions

### 4.1 Forget PersonalID with confirmation modal

Wire `profile_btn_forget_personalid` to a `StandardAlertDialog` (the dialog class used by `BaseDrawerController.showUpdatePhotoConfirmationDialog`). Title/message: `personalid_profile_forget_confirm_title`, `personalid_profile_forget_confirm_message`. Positive button calls `PersonalIdManager.getInstance().forgetUser(reason)`, starts `DispatchActivity` with `Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK`, and finishes the Profile activity. Routing through `DispatchActivity` unwinds any PersonalID-gated activity underneath.

Add a new constant to `AnalyticsParamValue` (e.g. `PERSONAL_ID_FORGOT_USER_PROFILE_PAGE`) for the analytics `reason` so the Profile trigger is distinguishable from the legacy ones.

Manual test: button shows modal; Cancel dismisses; Forget wipes local state and routes through `DispatchActivity`, landing on Login/Setup with the drawer in its signed-out appearance. Verify back-stack from a PersonalID-gated screen (e.g. Messaging) cannot return to it.

### 4.2 App Manager

`action_app_manager` starts `AppManagerActivity` with `Intent.FLAG_ACTIVITY_NEW_TASK`, mirroring `LoginActivity.java` (the existing `MENU_APP_MANAGER` case block).

**Commit 4.1 and 4.2 separately.**

---

## Phase 5 — Edit Profile screen

Goal: a form where the user changes name and/or email and saves. Phone is shown but disabled. Email validation fails inline with a red outline. Saving with a new email runs through the OTP confirmation dialog and then the existing email verification fragment.

### 5.1 Extract the photo update flow into `PersonalIdPhotoUpdater`

The photo update flow lives in `BaseDrawerController.kt` (`showUpdatePhotoConfirmationDialog`, `initTakePhotoLauncher`, `launchCameraForPhotoEdit`, `uploadUserPhoto`, plus the two `USER_PHOTO_MAX_*` constants). `loadUserPhoto` stays in `BaseDrawerController` — the helper does not centralize photo rendering.

Move the update flow into a new `PersonalIdPhotoUpdater` in `org.commcare.connect.photo`. API:

```kotlin
class PersonalIdPhotoUpdater {
    fun register(caller: ActivityResultCaller)   // call from onCreate
    fun show(
        onSuccess: (photoBase64: String) -> Unit,
        onFailure: (PersonalIdOrConnectApiErrorCodes, Throwable?) -> Unit,
    )
}
```

`ActivityResultCaller` is implemented by both `ComponentActivity` and `Fragment`. `register` **must** run before the caller reaches `STARTED`.

Refactor `BaseDrawerController` to construct + register an updater and call `show` from the existing photo-tap listener. The drawer keeps its existing drawer-specific behavior (flipping `lastPhotoUploadFailed`, swapping the overlay icon, toasts). Each consumer keeps its own one-line Glide call.

Keep this commit focused on the move + reuse, no behavior changes.

### 5.2 Layout `personalid_edit_profile_screen.xml`

Match Fig. 4 / page 8 (right side). Wrap in a `ScrollView`. Top to bottom:
- Circular photo **with camera-icon overlay** — reuse the `MaterialCardView` + black-60 overlay pattern from `nav_drawer_header.xml`. Ids `@+id/user_image` and `@+id/user_image_overlay_icon`. Tapping the photo launches the updater (wired in 5.4).
- Name (large) and phone (small) underneath, identical to Profile.
- Section header "Personal Information".
- **Name** — `TextInputLayout` wrapping a `TextInputEditText`, id `@+id/profile_input_name`, enabled.
- **Phone Number** — `TextInputLayout`, id `@+id/profile_input_phone`, `android:enabled="false"`, with `helperText` explaining it can't be changed. Match the greyed-out background in Figma.
- **Email Address** — directly above the input wrapper, a `TextView` `@+id/profile_email_otp_notice` with `android:text="@string/personalid_edit_profile_email_otp_notice"`, `android:drawableStart="@drawable/ic_personalid_warning"`, small `drawablePadding`, gray text + `app:drawableTint`, small text size. Below the notice, `TextInputLayout` `@+id/profile_input_email`, enabled, `android:inputType="textEmailAddress"`. Inline validation errors via `TextInputLayout.error`.
- Bottom row: `Cancel` text button (`@+id/btn_cancel`) on the left, filled `Save` button (`@+id/btn_save`) on the right. Save starts disabled.

### 5.3 `PersonalIdEditProfileViewModel`

Constructor takes a `SavedStateHandle`. Store `originalName`, `originalEmail`, `currentName`, `currentEmail` as `LiveData` via `savedStateHandle.getLiveData(KEY_...)` so values survive rotation, process death, and the round-trip to the verification fragment. Phone is display-only — the ViewModel never reads or writes it.

Derived booleans:
- `isNameModified() = currentName != originalName`
- `isEmailModified() = currentEmail != originalEmail`
- `isModified() = isNameModified() || isEmailModified()`
- `isNameValid() = currentName.isNotBlank()`
- `isEmailValid()`:
  - if `originalEmail` is empty: `currentEmail` is empty OR `EmailHelper.isValidEmail(currentEmail)`
  - if `originalEmail` is non-empty: `currentEmail` is non-empty AND `EmailHelper.isValidEmail(currentEmail)` (clearing an existing email is not allowed in MVP)
- `canSave() = isModified() && isNameValid() && isEmailValid()`

Expose `initialize(name, email)` (idempotent — only seeds originals if the handle is empty), `onNameChanged(String)`, `onEmailChanged(String)`.

The fragment surfaces `personalid_edit_profile_error_email_required` when the email is invalid because it's empty but original was non-empty; `personalid_edit_profile_error_email_invalid` for malformed non-empty input.

**Tests** — `@RunWith(AndroidJUnit4::class) @Config(application = CommCareTestApplication::class)` (Robolectric, matches `ConnectJobsListViewModelTest`):
- `isNameModified()` / `isEmailModified()` flip independently when their field changes, and back when reverted. `isModified()` is their OR.
- `isNameValid()` is false for blank, true otherwise.
- `isEmailValid()` when original empty: true for empty + well-formed, false for malformed.
- `isEmailValid()` when original non-empty: clearing is invalid; only non-empty matching addresses are valid.
- `canSave()` is the AND of `isModified()`, `isNameValid()`, `isEmailValid()`.
- `initialize()` called twice is a no-op; in-progress edits preserved.
- A ViewModel constructed from a populated `SavedStateHandle` restores both originals and current values; a subsequent `initialize` does not overwrite them.

### 5.4 `PersonalIdEditProfileFragment`

- Obtain the ViewModel with `by viewModels()`.
- In `onViewCreated`: load `ConnectUserRecord`, call `viewModel.initialize(user.name, user.email ?: "")` (idempotent), pre-fill Name and Email inputs from the LiveData, populate the disabled Phone field directly from the record.
- Render the photo with Glide (same config as Profile). Instantiate `PersonalIdPhotoUpdater` as a fragment field; call `updater.register(this)` in `onCreate`. Wire the photo and overlay click listeners to `updater.show(onSuccess, onFailure)` — `onSuccess` reloads the photo + success toast; `onFailure` calls `PersonalIdOrConnectApiErrorHandler.handle(...)`. Photo edits save independently of the form and do not affect `isModified()`.
- Attach `TextWatcher`s on Name and Email that forward to `onNameChanged` / `onEmailChanged`.
- Observe state on every change: `btn_save.isEnabled = viewModel.canSave()`. When email is invalid, set `profile_input_email.error` to the required/invalid string from Phase 1; otherwise clear it.
- `btn_save` click branches on `viewModel.isEmailModified()`:
   - **Name-only path** (false): disable the button; call `PersonalIdApiHandler.updateProfile(displayName = newName, secondaryPhone = null, photoAsBase64 = null)` using **named arguments**. On success, update `ConnectUserRecord` in place, `ConnectUserDatabaseUtil.storeUser`, success toast, `findNavController().popBackStack()`. On failure, re-enable the button and call `PersonalIdOrConnectApiErrorHandler.handle(...)`.
   - **Email path** (true): hand off to 5.5.
- `btn_cancel` and the toolbar back arrow both call `handleBack()` (see 5.6).
- In `onResume`: `btn_save.isEnabled = viewModel.canSave()`. Covers the case where the user system-backs out of the verification fragment without a result.

### 5.5 OTP confirmation dialog and handoff to `PersonalIdEmailVerificationFragment`

Save-tap path when `viewModel.isEmailModified()` is true.

1. **Confirmation dialog.** `StandardAlertDialog` from the Phase 1 strings:
   - Title: `personalid_edit_profile_otp_confirm_title`
   - Message: `getString(personalid_edit_profile_otp_confirm_message, currentEmail)`
   - Positive: `personalid_edit_profile_otp_confirm_positive` → step 2.
   - Negative: `personalid_edit_profile_otp_confirm_negative` → dismiss. Form state preserved; Save stays enabled.

2. **Disable Save** immediately on Continue.

3. **Commit the name change first (if dirty).** If `viewModel.isNameModified()`: call `PersonalIdApiHandler.updateProfile(displayName = currentName, secondaryPhone = null, photoAsBase64 = null)` with **named arguments**. On success, update `ConnectUserRecord.name` and `ConnectUserDatabaseUtil.storeUser(...)`, then continue to step 4. On failure, re-enable Save, `PersonalIdOrConnectApiErrorHandler.handle(...)`, **stop**. Form values preserved.

4. **Send the OTP.** Call `EmailHelper.sendEmailOtp(requireActivity(), currentEmail, EmailWorkFlow.EXISTING_USER, sessionData = null, onSuccess = { navigate to verification }, onFailure = { failureCode, t -> PersonalIdOrConnectApiErrorHandler.handle(requireActivity(), failureCode, t); btn_save.isEnabled = true })`. The helper toasts "A 6-digit passcode has been sent to your email address." on success.

5. **Navigate** to the verification destination via the generated action:
   ```kotlin
   findNavController().navigate(
       PersonalIdEditProfileFragmentDirections
           .actionEditProfileToEmailVerification(currentEmail, EmailWorkFlow.EXISTING_USER)
   )
   ```

The verification fragment owns the rest. On successful verify it writes `ConnectUserRecord.email`, shows the "Email Added" dialog, and finishes the activity on OK. On 3 failed attempts, the fragment shows its "proceed without email" dialog; "Proceed without email" finishes the activity. The user lands at the drawer's host activity and reopens Profile to see the new state.

If the user system-backs out of the verification fragment without going through a dialog, the nav stack pops back to Edit and `onResume` re-applies `canSave()`. The typed name and email are preserved (`SavedStateHandle`).

### 5.6 Discard confirmation on back / cancel

Register an `OnBackPressedCallback` in `onViewCreated` so system back routes through `handleBack()`. Intercept `android.R.id.home` in `onOptionsItemSelected` and call `handleBack()`.

`handleBack()`: if `!viewModel.isModified()`, `popBackStack()`. Otherwise show a `StandardAlertDialog` with the `personalid_edit_profile_discard_*` strings. Positive ("Discard") → pop. Negative → dismiss.

**Verify.** Open Edit, exercise:
- Prefilled name and email, Phone disabled.
- Save disabled until any field is modified to a valid value; disabled on blank name, malformed email, or cleared-from-non-empty email.
- Invalid email shows the red outline + caption.
- Name-only Save → updates record, toast, pops to Profile.
- Name-only Save with a network failure → toast, form stays populated, Save re-enables.
- Email-only Save → confirmation dialog → Cancel dismisses with no state change; Continue calls `sendEmailOtp` (helper toasts) and navigates to the verification fragment showing the 6-digit input.
- Combined name+email Save → confirmation → Continue → name save succeeds → OTP sent → verification fragment. New name already persisted (back-stacking out would show it on Profile).
- Combined name+email Save with a failing name save → toast on Edit, no OTP, form preserved, Save re-enables.
- Verify code → "Email Added" dialog → OK → activity finishes → reopen Profile to see the new email.
- Fail OTP 3x → "Proceed without email" → activity finishes.
- System back from the verification fragment (no dialog) → Edit resumes with typed values preserved, Save re-enables in `onResume`.
- Cancel/back with any field modified shows the discard modal; no-change back returns immediately.

Run `./gradlew :app:testCommcareDebugUnitTest`.

**Commit 5.1 alone**; **5.2–5.4 together**; **5.5 alone**; **5.6 alone**.

---

## Phase 6 — Reveal the feature and clean up

### 6.1 Reveal the drawer link

In `BaseDrawerController.refreshDrawerContent()`, replace the hard-coded `manageProfileLink.visibility = View.GONE` from Phase 1 with the real conditional, matching the existing `profileCard.visibility` line (visible when signed in, gone otherwise).

Manual test: sign in, open drawer — link appears and opens the Profile screen.

### 6.2 Remove the old Forget PersonalID entries

App Manager stays in `LoginActivity` (must remain reachable without PersonalID). Profile's overflow App Manager is an additional entry point. Only `Forget PersonalID` is removed, from **both** Login and Setup.

In `LoginActivity.java`:
- Remove `menu.add(...)` for `MENU_PERSONAL_ID_FORGET` in `onCreateOptionsMenu`.
- Remove the corresponding `case MENU_PERSONAL_ID_FORGET` block in `onOptionsItemSelected`.
- Remove the now-unused `MENU_PERSONAL_ID_FORGET` constant and its entry in `createMenuItemToAnalyticsParamMapping`.
- Remove the `MENU_PERSONAL_ID_FORGET` visibility logic in `onPrepareOptionsMenu`.
- Leave `MENU_APP_MANAGER` and everything associated with it untouched.

In `CommCareSetupActivity.java`: same removals (`MENU_PERSONAL_ID_FORGET` constant, menu add, case block, analytics mapping entry, `onPrepareOptionsMenu` visibility logic).

(No unit or instrumentation tests reference these menu constants — confirm with grep before committing.)

Manual test: neither Login nor Setup shows `Forget PersonalID`; the action is reachable from Profile. Login still shows `App Manager`.

### 6.3 Docs

Add `docs/personalid/manage_profile.md` with a short overview: how to reach Profile, where data is read from / written to, which fields are editable in MVP (name, email) vs disabled (phone), where the photo flow lives (`PersonalIdPhotoUpdater`), and:
- **Photo updates save independently** of the Name/Email form — a photo persists immediately even if the user later discards or fails to save the form.
- **Email changes go through `PersonalIdEmailVerificationFragment`** (`EmailWorkFlow.EXISTING_USER`). Edit calls `EmailHelper.sendEmailOtp` before navigating; the verification fragment writes `ConnectUserRecord.email`, shows "Email Added", and finishes `PersonalIdProfileActivity` on OK.
- **Name and email saves are independent.** When both are dirty, Edit commits the name first, then OTP. A user who abandons OTP after a successful name save will see the name persisted but the email unchanged — intentional, not a bug.

Do not link to the design doc or internal tickets (this repo is open-source).

---

## Out of scope

Per the Path Forward decision and PR-review scoping:
- Phone number editing + OTP verification — phone is greyed out only
- Credentials / "View My Earned Certificates" card on Profile
- Rate limiting on profile updates
- Push notification on profile update (V1)
- Credentials & Payments info-only sections on Profile (V1)
- Updating the user's backup code

# Manage Profile — Implementation Plan

**Design doc:** [Manage Profile](https://docs.google.com/document/d/11B3b8K92_bPQh71CVmNm8VTLVvvKid6ta4G7esSrSVc/edit?tab=t.0) — the "Path Forward" section is the source of truth (Option 2 for Profile, Option 1 for Edit Information).
**Branch:** `CCCT-2345-create-manage-profile-screen-plan`

## Goal

Give a signed-in PersonalID user a dedicated Profile screen reached from the nav drawer, plus a separate Edit screen for changing editable fields. They can:
- View name, phone, email, and their photo
- Update the photo (reusing the existing flow)
- Tap an edit pencil to change their **name** on a separate Edit screen
- Open App Manager from a 3-dot menu
- Forget their PersonalID via a destructive CTA with a confirmation modal

Forget PersonalID moves out of the Login *and* Setup 3-dot menus into the new Profile screen. App Manager stays in the Login menu — it must remain reachable for users without PersonalID — and is *also* exposed from Profile as an additional entry point.

Phone and email are **not editable** in this MVP — both render as greyed-out, disabled fields on the Edit screen. Phone is permanently not-editable per the Path Forward; email editing is deferred to a future implementation ticket.

## Architecture

A new `PersonalIdProfileActivity` hosts a small Jetpack Navigation graph with two destinations: `PersonalIdProfileFragment` (view-only) and `PersonalIdEditProfileFragment` (form). Both new fragments live in `org.commcare.fragments.personalId` and are written in Kotlin with ViewBinding + `ViewModelProvider`, matching the pattern already used by other PersonalID fragments (see `PersonalIdPhotoCaptureFragment` for reference).

The screen reads from `ConnectUserDatabaseUtil.getUser(context)` (returns a `ConnectUserRecord`) and writes via `ApiPersonalId.updateUserProfile(...)`. On a successful save we update the local `ConnectUserRecord` and `ConnectUserDatabaseUtil.storeUser(...)`.

The photo update flow that lives in `BaseDrawerController` today gets extracted into a reusable helper so both the drawer and the Edit Profile screen call the same code path.

**Unlock gate.** Tapping the drawer link runs through `PersonalIdUnlocker.unlock(activity, UnlockPolicy.ALWAYS, ...)` before launching `PersonalIdProfileActivity`.

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
   - Forget modal: `personalid_profile_forget_confirm_title`, `personalid_profile_forget_confirm_message`
   - Discard modal: `personalid_edit_profile_discard_title` ("Discard your changes?"), `personalid_edit_profile_discard_message` ("Your unsaved changes will be lost."), `personalid_edit_profile_discard_positive` ("Discard"), `personalid_edit_profile_discard_negative` ("Keep editing")

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

Goal: a form where the user changes their name and saves. Phone and email are both shown but greyed out and disabled — email editing is deferred to a future implementation ticket. Cancelling or backing out with unsaved changes triggers a discard modal.

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
- **Name** — `TextInputLayout` wrapping a `TextInputEditText`, id `@+id/profile_input_name`, enabled. This is the only editable text field in MVP.
- **Phone Number** — `TextInputLayout`, id `@+id/profile_input_phone`, `android:enabled="false"`, with a `helperText` that explains it cannot be changed. Match the greyed-out background in the Figma.
- **Email Address** — `TextInputLayout`, id `@+id/profile_input_email`, `android:enabled="false"`, same greyed-out treatment as phone. No OTP notice TextView — the OTP / email-update copy will be added by the future email-editing ticket.
- Bottom row: a `Cancel` text button on the left (`@+id/btn_cancel`) and a filled `Save` button on the right (`@+id/btn_save`). Save starts disabled.

### 5.3 `PersonalIdEditProfileViewModel`

Constructor takes a `SavedStateHandle`; the original name and current name are stored as `LiveData` via `savedStateHandle.getLiveData(KEY_...)`. This survives both rotation and process death without losing in-progress edits. Email and phone are not tracked here — they are display-only and the ViewModel never reads or writes them.

Derived booleans:
- `isModified()` — `currentName != originalName`
- `canSave() = isModified() && currentName.isNotBlank()` — name cannot be cleared to empty

Expose `initialize(name)` (idempotent — only seeds the original if the handle is empty) and `onNameChanged(String)`.

**Tests** — `@RunWith(AndroidJUnit4::class) @Config(application = CommCareTestApplication::class)` (Robolectric, matches `ConnectJobsListViewModelTest`):
- `isModified()` flips true when the name changes, and back to false when it is edited back to its original.
- `canSave()` is false when the name has not been changed, false when the edited name is blank, and true when the name is changed to a non-blank value.
- `initialize()` called a second time is a no-op; an in-progress edit to the name is preserved.
- A ViewModel constructed from an already-populated `SavedStateHandle` restores both the original and the current name; a subsequent `initialize` does not overwrite them.

### 5.4 `PersonalIdEditProfileFragment`

- Obtain the ViewModel with `by viewModels()` (the Kotlin delegate provides a `SavedStateHandle` automatically).
- Load the `ConnectUserRecord` in `onViewCreated`, call `viewModel.initialize(user.name)` (idempotent — safe on rotation), pre-fill the Name input from the LiveData, and populate the disabled Phone and Email fields directly from the record (no LiveData needed — they never change on this screen).
- Render the photo with Glide (same configuration as the Profile screen). Instantiate `PersonalIdPhotoUpdater` as a fragment field and call `updater.register(this)` from `onCreate` so the `ActivityResultLauncher` is registered before `STARTED`. Call `updater.show(onSuccess, onFailure)` from the photo and overlay click listeners — `onSuccess` reloads the photo via Glide and shows a success toast; `onFailure` shows the error toast via `PersonalIdOrConnectApiErrorHandler.handle(...)`. Photo changes save independently of the Name form — they do not affect `isModified()` or the Save button.
- Attach a `TextWatcher` on the Name `EditText` that forwards to `onNameChanged`.
- Observe state on every change: `btn_save.isEnabled = viewModel.canSave()`.
- `btn_save` click: disable the button immediately and re-enable it in both success and failure paths (prevents double-submission). Call `PersonalIdApiHandler.updateProfile` (the wrapper, same path the drawer uses) using **named arguments** — `displayName = newName, secondaryPhone = null, photoAsBase64 = null` — so adjacent nulls cannot silently swap. On success, update the `ConnectUserRecord` in place, call `ConnectUserDatabaseUtil.storeUser(...)`, show a success toast, and `findNavController().popBackStack()`. On failure, surface the standard error via `PersonalIdOrConnectApiErrorHandler.handle(...)`.
- `btn_cancel` and the toolbar back arrow both call a single `handleBack()` method (see 5.5).

### 5.5 Discard confirmation on back / cancel

Register an `OnBackPressedCallback` in `onViewCreated` so the system back button routes through the same `handleBack()` as the Cancel button and the toolbar's Up arrow (intercept `android.R.id.home` in `onOptionsItemSelected` and call `handleBack()`).

`handleBack()` logic: if `!viewModel.isModified()`, `popBackStack()` immediately. Otherwise show a `StandardAlertDialog` with title/message from the new `personalid_edit_profile_discard_*` strings. Positive ("Discard") → pop. Negative ("Keep editing") → dismiss and stay.

**Verify.** Build, open Edit, exercise: prefilled name, disabled phone and email, Save disabled until the name is changed, Save disabled when the name is blanked, valid Save round-trips to backend and updates UI, network failure shows toast and leaves the form populated, cancel/back with changes shows discard modal, no-change back returns immediately. Run the ViewModel tests.

**Commit the photo extraction in 5.1 alone**, then **commit the Edit screen + ViewModel + tests together** (5.2–5.4), then **commit the discard-confirmation behavior** (5.5).

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

Add `docs/personalid/manage_profile.md` with a short overview: how to reach Profile, where user data is read from and written to, which fields are editable in MVP (name only) vs greyed out (phone, email), where the photo update flow lives (`PersonalIdPhotoUpdater`), and a note that **photo updates and the Name Save are independent network calls** — a photo persists immediately, even if the user later discards or fails to save the form. Call out that email editing is deferred to a future implementation ticket and is intentionally not wired here. Do not link to the design doc or internal tickets — this repo is open-source and external readers won't have access.

---

## Out of scope (do not implement)

Per the Path Forward decision in the design doc, plus the scoping calls made in PR review:
- Phone number editing + OTP verification — phone is greyed out only
- Email editing — deferred to a future implementation ticket, which will land the `updateUserProfile` parameter, the OTP verification flow, and any related copy. Email is greyed out on the Edit screen in this MVP.
- Credentials / "View My Earned Certificates" card on the Profile screen — users already reach Work History from the nav drawer
- Rate limiting on profile updates
- Push notification on profile update (V1)
- Credentials & Payments info-only sections on Profile (V1)
- Updating the user's backup code (entirely out of scope)

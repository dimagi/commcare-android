# Design: Unit tests for PersonalIdPhotoCaptureFragment

**Ticket:** CCCT-2303
**Branch:** `CCCT-2303-personalid_photo_page_tests`
**Date:** 2026-05-18

## Goal

Add Robolectric-based unit tests for `PersonalIdPhotoCaptureFragment` (Java), modeled on `PersonalIdBiometricConfigFragmentTest`. The tests must exercise the real fragment instantiated inside a real `PersonalIdActivity` with a `TestNavHostController`, while isolating it from network calls, the database, and analytics.

## Files to add

```
app/unit-tests/src/org/commcare/fragments/personalId/
├── BasePersonalIdPhotoCaptureFragmentTest.kt      (new)  — abstract base + TestablePersonalIdPhotoCaptureFragment
└── PersonalIdPhotoCaptureFragmentTest.kt          (new)  — @Test methods
```

No production code changes. The fragment stays as-is in Java.

## Test infrastructure

### `BasePersonalIdPhotoCaptureFragmentTest`

Open abstract base providing:

- `mocksCloseable: AutoCloseable` — Mockito lifecycle.
- `activityController`, `activity`, `fragment`, `navController` — same shape as the biometric base.
- `setUpPhotoCaptureFragment(sessionData: PersonalIdSessionData = defaultSessionData())` — builds `PersonalIdActivity`, seeds `PersonalIdSessionDataViewModel`, sets `TestNavHostController` to `R.id.personalid_photo_capture`, swaps in `TestablePersonalIdPhotoCaptureFragment` via `replace(...).commitNow()` on the nav host's child fragment manager. Mirrors the biometric base's setup pattern.
- `@After tearDown()` — closes static mocks and the activity controller.

### Static `MockedStatic` registrations in `@Before`

Required because the fragment calls these as static utilities:

| Static | Why it's mocked |
|---|---|
| `ConnectDatabaseHelper.handleReceivedDbPassphrase` | No-op — avoids real DB init. |
| `ConnectUserDatabaseUtil.storeUser` | Captured for verification on success path. |
| `FirebaseAnalyticsUtil.reportPersonalIdAccountCreated` | Captured for verification on success path. |
| `MediaUtil.decodeBase64EncodedBitmap` | Returns a stub `Bitmap` so `ImageView.setImageBitmap` doesn't NPE. |
| `PersonalIdOrConnectApiErrorHandler.handle` | Returns a known error string so we can assert it surfaces in the error TextView. |
| `ApiPersonalId.setPhotoAndCompleteProfile` | No-op stub so save-click doesn't hit the network. Used to verify the call was made with the expected args. |

### `TestablePersonalIdPhotoCaptureFragment`

Inner class in the base file. Subclasses `PersonalIdPhotoCaptureFragment` and:

- Accepts an optional `NavController` and overrides `getNavController()` to return it (matches biometric pattern).
- Exposes test helpers that use reflection on the private fragment members:
  - `simulatePhotoResult(resultCode: Int, photoBase64: String?)` — gets the `takePhotoLauncher: ActivityResultLauncher<Intent>` field, but rather than invoking the launcher's internal callback, the helper invokes the **registered callback** that was passed to `registerForActivityResult`. Because that callback is captured inside a lambda stored only as the launcher's callback, the cleanest way is to reflect into `takePhotoLauncher` and call its `mActivityResultCallback`. Implementation note: if access to the internal callback proves brittle, the fallback is to directly invoke the private `displayImage` + button-state methods via reflection — but the launcher-callback path is preferred because it actually exercises the registered lambda.
  - `invokePhotoUploadSuccess(photoBase64: String)` — reflects and calls the private `onPhotoUploadSuccess(String)`.
  - `invokeCompleteProfileFailure(failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?)` — reflects and calls the private `onCompleteProfileFailure(...)`.
  - `getPhotoAsBase64()` / `setPhotoAsBase64(String)` — reflects the `photoAsBase64` field, used to set up state before invoking the success/failure callbacks.

## Test cases

### UI initial state (4)

1. `testInitialState_titleContainsUserName` — verify `R.id.title` text matches `getString(personalid_photo_capture_title, "Test User")`.
2. `testInitialState_savePhotoButtonDisabled` — `R.id.save_photo_button.isEnabled == false`.
3. `testInitialState_takePhotoButtonEnabled` — `R.id.take_photo_button.isEnabled == true`.
4. `testInitialState_errorTextViewHidden` — `R.id.errorTextView.visibility == View.GONE`.

### Take photo flow (1)

5. `testTakePhotoClick_disablesButtonAndLaunchesMicroImageActivity` —
   - Click `R.id.take_photo_button`.
   - Assert take-photo button now disabled.
   - Use `Shadows.shadowOf(activity).peekNextStartedActivity()` (or `ShadowApplication.getNextStartedActivity()` equivalent) to inspect the launched intent.
   - Assert component is `MicroImageActivity` and extras contain `MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA=160`, `MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA=102400`.

### Photo result handling (2)

6. `testPhotoResult_onSuccess_displaysImageAndEnablesSaveButton` —
   - Stub `MediaUtil.decodeBase64EncodedBitmap` to return a stub Bitmap.
   - Call `testableFragment.simulatePhotoResult(RESULT_OK, "fake-base64")`.
   - Assert save button enabled, take-photo button re-enabled, `ImageView` drawable non-null.
7. `testPhotoResult_onCancel_keepsSaveDisabled` —
   - Call `testableFragment.simulatePhotoResult(RESULT_CANCELED, null)`.
   - Assert save button still disabled, take-photo button re-enabled, image unchanged.

### Save / completeProfile (4)

8. `testSavePhotoClick_callsCompleteProfileWithCorrectArgs` —
   - Set `photoAsBase64` to `"fake-base64"` via the testable helper.
   - Click `R.id.save_photo_button`.
   - Verify `ApiPersonalId.setPhotoAndCompleteProfile` was called with the session's username, `"fake-base64"`, the session's backup code, and the session's token.
   - Verify save button + take-photo button both disabled while in flight.
   - Verify error view cleared (`visibility == GONE`).
9. `testCompleteProfile_onSuccess_storesUserAndNavigatesToSuccess` —
   - Call `testableFragment.invokePhotoUploadSuccess("fake-base64")`.
   - Verify `ConnectDatabaseHelper.handleReceivedDbPassphrase` and `ConnectUserDatabaseUtil.storeUser` were called.
   - Verify `FirebaseAnalyticsUtil.reportPersonalIdAccountCreated` was called.
   - Assert `navController.currentDestination?.id == R.id.personalid_message_display`.
   - Assert nav args: `title == getString(R.string.connect_register_success_title)`, `isCancellable == false`, `callingClass == ConnectConstants.PERSONALID_REGISTRATION_SUCCESS`.
10. `testCompleteProfile_onAccountLocked_navigatesToFailureMessageDisplay` —
    - Call `testableFragment.invokeCompleteProfileFailure(PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR, null)`.
    - Assert navController moves to `R.id.personalid_message_display` with `title == getString(R.string.personalid_configuration_process_failed_title)`, `isCancellable == false`.
11. `testCompleteProfile_onRetryableFailure_reenablesButtons_andShowsError` —
    - Stub `PersonalIdOrConnectApiErrorHandler.handle` to return `"network error"`.
    - Call `testableFragment.invokeCompleteProfileFailure(PersonalIdOrConnectApiErrorCodes.SERVER_ERROR, RuntimeException())` (SERVER_ERROR's `shouldAllowRetry()` returns true).
    - Assert no navigation (still on `R.id.personalid_photo_capture`).
    - Assert error TextView visible with text `"network error"`.
    - Assert both buttons re-enabled.
12. `testCompleteProfile_onNonRetryableFailure_buttonsStayDisabled` —
    - Choose a failure code whose `shouldAllowRetry()` returns false and that isn't ACCOUNT_LOCKED (verify against `PersonalIdOrConnectApiErrorCodes` enum; candidates: `INTEGRITY_ERROR`, `TOKEN_INVALID_ERROR`).
    - Call `testableFragment.invokeCompleteProfileFailure(thatCode, null)`.
    - Assert no navigation, error visible, both buttons remain disabled.

> **Note on retryable vs non-retryable selection:** the actual enum values must be inspected when writing the plan to confirm which codes have `shouldAllowRetry() == true/false`. The plan step that writes these tests will read `PersonalIdOrConnectApiErrorCodes` first and pick concrete codes.

## Risks and open items

- **Reflecting into `ActivityResultLauncher` internals** is the brittlest part. If it proves unreliable across Robolectric versions, the fallback is to directly invoke the lambda's body via `displayImage` + `enableSaveButton` + `enableTakePhotoButton` private methods. The test still verifies user-visible behavior.
- The fragment uses `ViewModelProvider(requireActivity())` for `PersonalIdSessionDataViewModel`. The session data must be seeded **before** `commitNow()` swaps in the fragment, identical to how the biometric base does it.
- `PersonalIdActivity`'s startup may have its own dependencies (PersonalIdManager singleton, etc.). The biometric base mocks `PersonalIdManager` static — the photo base should do the same so activity startup succeeds.

## Non-goals

- No conversion of `PersonalIdPhotoCaptureFragment.java` to Kotlin.
- No production code changes (no extracted factory method, no testability hooks on the fragment itself).
- No instrumentation/end-to-end tests.
- No coverage of `MicroImageActivity` itself.

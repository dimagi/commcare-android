# PersonalIdPhotoCaptureFragment Unit Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Robolectric-based unit tests for `PersonalIdPhotoCaptureFragment` (Java), mirroring the pattern established by `PersonalIdBiometricConfigFragmentTest`.

**Architecture:** Two new Kotlin files in `app/unit-tests/src/org/commcare/fragments/personalId/`: an abstract `BasePersonalIdPhotoCaptureFragmentTest` (setup + `TestablePersonalIdPhotoCaptureFragment` helper) and a concrete `PersonalIdPhotoCaptureFragmentTest` with 11 `@Test` methods. No production code changes.

**Tech Stack:** JUnit 4, Robolectric 4.8.2, AndroidX Navigation Testing, Mockito 5 (`MockedStatic`), Kotlin.

**Spec:** `docs/superpowers/specs/2026-05-18-personalid-photo-capture-fragment-tests-design.md`

---

## File structure

```
app/unit-tests/src/org/commcare/fragments/personalId/
├── BasePersonalIdPhotoCaptureFragmentTest.kt      (new, ~180 lines)
└── PersonalIdPhotoCaptureFragmentTest.kt          (new, ~250 lines)
```

`BasePersonalIdPhotoCaptureFragmentTest.kt` contains:
- The abstract base class with `@Before`/`@After`, all static mocks, ViewModel seeding, fragment swap-in.
- The `TestablePersonalIdPhotoCaptureFragment` inner class with reflection helpers.

`PersonalIdPhotoCaptureFragmentTest.kt` contains only `@Test` methods that drive the fragment and assert on outcomes.

---

## Concrete values used throughout the tests

| Field | Value |
|---|---|
| `userName` | `"Test User"` |
| `phoneNumber` | `"+11234567890"` |
| `personalId` | `"test-personal-id"` |
| `oauthPassword` | `"test-oauth-pwd"` |
| `backupCode` | `"123456"` |
| `token` | `"test-token"` |
| `dbKey` | `"test-db-key"` |
| `requiredLock` | `PersonalIdSessionData.PIN` |
| `demoUser` | `false` |
| `invitedUser` | `false` |
| Stub photo base64 | `"fake-base64-photo"` |
| Stub error string | `"Network error occurred"` |
| Retryable failure code | `PersonalIdOrConnectApiErrorCodes.SERVER_ERROR` (`shouldAllowRetry() == true`) |
| Non-retryable failure code | `PersonalIdOrConnectApiErrorCodes.TOKEN_INVALID_ERROR` (not in `shouldAllowRetry` allow-list, and not handled by `handleCommonSignupFailures`) |

---

## Task 1: Scaffold the base test class and TestablePersonalIdPhotoCaptureFragment

**Files:**
- Create: `app/unit-tests/src/org/commcare/fragments/personalId/BasePersonalIdPhotoCaptureFragmentTest.kt`
- Create: `app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt`

This task creates the test infrastructure. The smoke test in Step 3 verifies the activity boots, the fragment inflates, and the navController is wired before adding behavior tests in later tasks.

- [ ] **Step 1: Write `BasePersonalIdPhotoCaptureFragmentTest.kt`**

```kotlin
package org.commcare.fragments.personalId

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ApiPersonalId
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.dalvik.R
import org.commcare.fragments.MicroImageActivity
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MediaUtil
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.junit.After
import org.junit.Before
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdPhotoCaptureFragment tests.
 * Owns setup/teardown of the activity, the fragment, the test NavController,
 * and the static mocks that prevent the fragment from hitting the network,
 * database, analytics, and bitmap decoding during tests.
 */
abstract class BasePersonalIdPhotoCaptureFragmentTest {
    protected lateinit var mocksCloseable: AutoCloseable
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: TestablePersonalIdPhotoCaptureFragment
    protected lateinit var navController: TestNavHostController

    protected lateinit var personalIdManagerMock: MockedStatic<PersonalIdManager>
    protected lateinit var connectDatabaseHelperMock: MockedStatic<ConnectDatabaseHelper>
    protected lateinit var connectUserDatabaseUtilMock: MockedStatic<ConnectUserDatabaseUtil>
    protected lateinit var firebaseAnalyticsUtilMock: MockedStatic<FirebaseAnalyticsUtil>
    protected lateinit var mediaUtilMock: MockedStatic<MediaUtil>
    protected lateinit var apiPersonalIdMock: MockedStatic<ApiPersonalId>
    protected lateinit var errorHandlerMock: MockedStatic<PersonalIdOrConnectApiErrorHandler>

    @Mock
    protected lateinit var mockPersonalIdManager: PersonalIdManager

    @Mock
    protected lateinit var mockBitmap: Bitmap

    protected val testSessionData =
        PersonalIdSessionData(
            requiredLock = PersonalIdSessionData.PIN,
            demoUser = false,
            token = "test-token",
            personalId = "test-personal-id",
            dbKey = "test-db-key",
            oauthPassword = "test-oauth-pwd",
            userName = "Test User",
            phoneNumber = "+11234567890",
            backupCode = "123456",
            invitedUser = false,
        )

    @Before
    open fun setUp() {
        mocksCloseable = MockitoAnnotations.openMocks(this)
        MockAndroidKeyStoreProvider.registerProvider()
        openStaticMocks()
        setUpPhotoCaptureFragment()
    }

    private fun openStaticMocks() {
        personalIdManagerMock = Mockito.mockStatic(PersonalIdManager::class.java)
        personalIdManagerMock
            .`when`<PersonalIdManager> { PersonalIdManager.getInstance() }
            .thenReturn(mockPersonalIdManager)

        connectDatabaseHelperMock = Mockito.mockStatic(ConnectDatabaseHelper::class.java)
        connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
        firebaseAnalyticsUtilMock = Mockito.mockStatic(FirebaseAnalyticsUtil::class.java)
        mediaUtilMock = Mockito.mockStatic(MediaUtil::class.java)
        mediaUtilMock
            .`when`<Bitmap> { MediaUtil.decodeBase64EncodedBitmap(any()) }
            .thenReturn(mockBitmap)

        apiPersonalIdMock = Mockito.mockStatic(ApiPersonalId::class.java)
        // setPhotoAndCompleteProfile is a no-op stub; tests verify invocation args.

        errorHandlerMock = Mockito.mockStatic(PersonalIdOrConnectApiErrorHandler::class.java)
        errorHandlerMock
            .`when`<String> {
                PersonalIdOrConnectApiErrorHandler.handle(
                    any(),
                    any(),
                    Mockito.nullable(Throwable::class.java),
                )
            }.thenReturn("Network error occurred")
    }

    protected fun setUpPhotoCaptureFragment(sessionData: PersonalIdSessionData = testSessionData) {
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity = activityController.create().start().resume().get()

        // Seed the ViewModel before swapping the fragment in; onCreateView reads it.
        activity.runOnUiThread {
            val viewModel = ViewModelProvider(activity)[PersonalIdSessionDataViewModel::class.java]
            viewModel.setPersonalIdSessionData(sessionData)
        }
        ShadowLooper.idleMainLooper()

        val navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment

        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.setGraph(R.navigation.nav_graph_personalid)
        navController.setCurrentDestination(R.id.personalid_photo_capture)

        activity.runOnUiThread {
            Navigation.setViewNavController(navHostFragment.requireView(), navController)
            val testableFragment = TestablePersonalIdPhotoCaptureFragment(navController)
            navHostFragment.childFragmentManager
                .beginTransaction()
                .replace(R.id.nav_host_fragment_connectid, testableFragment)
                .commitNow()
            fragment = testableFragment
        }
        ShadowLooper.idleMainLooper()
    }

    @After
    open fun tearDown() {
        activityController.pause().stop().destroy()
        errorHandlerMock.close()
        apiPersonalIdMock.close()
        mediaUtilMock.close()
        firebaseAnalyticsUtilMock.close()
        connectUserDatabaseUtilMock.close()
        connectDatabaseHelperMock.close()
        personalIdManagerMock.close()
        mocksCloseable.close()
        MockAndroidKeyStoreProvider.deregisterProvider()
    }
}

/**
 * Testable subclass of PersonalIdPhotoCaptureFragment that exposes:
 *  - an injectable NavController (matches PersonalIdBiometricConfigFragment pattern)
 *  - reflection helpers to drive the private callback methods
 *  - a helper to replace the takePhotoLauncher with a mock for intent-capture tests
 *  - a setter for the private photoAsBase64 field
 */
class TestablePersonalIdPhotoCaptureFragment(
    private val testNavController: NavController? = null,
) : PersonalIdPhotoCaptureFragment() {

    override fun getNavController(): NavController =
        testNavController ?: super.getNavController()

    fun replaceTakePhotoLauncher(launcher: ActivityResultLauncher<Intent>) {
        val field = PersonalIdPhotoCaptureFragment::class.java.getDeclaredField("takePhotoLauncher")
        field.isAccessible = true
        field.set(this, launcher)
    }

    @Suppress("UNCHECKED_CAST")
    fun getTakePhotoLauncher(): ActivityResultLauncher<Intent> {
        val field = PersonalIdPhotoCaptureFragment::class.java.getDeclaredField("takePhotoLauncher")
        field.isAccessible = true
        return field.get(this) as ActivityResultLauncher<Intent>
    }

    /**
     * Invokes the registered ActivityResult callback directly so we don't have to
     * round-trip through Robolectric's ActivityResultRegistry.
     */
    fun simulatePhotoResult(resultCode: Int, photoBase64: String?) {
        // The launcher returned by registerForActivityResult is an internal
        // ActivityResultLauncher whose mCallback field holds the registered lambda.
        val launcher = getTakePhotoLauncher()
        val callbackFieldOnLauncher =
            launcher.javaClass.getDeclaredField("mCallback")
        callbackFieldOnLauncher.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val callback = callbackFieldOnLauncher.get(launcher) as ActivityResultCallback<ActivityResult>
        val resultIntent =
            if (photoBase64 != null) {
                Intent().putExtra(MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY, photoBase64)
            } else {
                null
            }
        callback.onActivityResult(ActivityResult(resultCode, resultIntent))
    }

    fun invokePhotoUploadSuccess(photoBase64: String) {
        setPhotoAsBase64(photoBase64)
        val method = PersonalIdPhotoCaptureFragment::class.java
            .getDeclaredMethod("onPhotoUploadSuccess", String::class.java)
        method.isAccessible = true
        method.invoke(this, photoBase64)
    }

    fun invokeCompleteProfileFailure(
        failureCode: PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    ) {
        val method = PersonalIdPhotoCaptureFragment::class.java
            .getDeclaredMethod(
                "onCompleteProfileFailure",
                PersonalIdOrConnectApiErrorCodes::class.java,
                Throwable::class.java,
            )
        method.isAccessible = true
        method.invoke(this, failureCode, t)
    }

    fun setPhotoAsBase64(photoBase64: String?) {
        val field = PersonalIdPhotoCaptureFragment::class.java.getDeclaredField("photoAsBase64")
        field.isAccessible = true
        field.set(this, photoBase64)
    }
}
```

> **Note on `simulatePhotoResult`:** the AndroidX Activity library generates the launcher returned by `registerForActivityResult` with an `mCallback` field that holds the registered `ActivityResultCallback`. If a future AndroidX version renames this field, the fallback is to look it up by type via `javaClass.declaredFields.first { it.type == ActivityResultCallback::class.java }`.

- [ ] **Step 2: Write `PersonalIdPhotoCaptureFragmentTest.kt` with one smoke test**

```kotlin
package org.commcare.fragments.personalId

import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdPhotoCaptureFragmentTest : BasePersonalIdPhotoCaptureFragmentTest() {

    @Test
    fun testFragmentSmokeTest_fragmentViewInflated() {
        assertNotNull("Fragment view should be inflated", fragment.view)
        val title = fragment.view!!.findViewById<TextView>(R.id.title)
        assertNotNull("Title TextView should exist", title)
        assertEquals(
            R.id.personalid_photo_capture,
            navController.currentDestination?.id,
        )
    }
}
```

- [ ] **Step 3: Run the smoke test to confirm setup works**

Run (from repo root):

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest.testFragmentSmokeTest_fragmentViewInflated"
```

Expected: PASS. If it fails:
- `ClassNotFoundException` on `MockAndroidKeyStoreProvider` → check the import is correct against the project's existing usage in `BasePersonalIdBiometricConfigFragmentTest.kt`.
- NPE seeding the ViewModel → confirm `setPersonalIdSessionData` is being called before `commitNow`.
- `LayoutInflaterException` decoding the photo drawable → confirm the `MediaUtil` static mock is registered before fragment swap-in.

- [ ] **Step 4: Format with ktlint**

```bash
ktlint --format app/unit-tests/src/org/commcare/fragments/personalId/BasePersonalIdPhotoCaptureFragmentTest.kt app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
ktlint app/unit-tests/src/org/commcare/fragments/personalId/BasePersonalIdPhotoCaptureFragmentTest.kt app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
```

Expected: no errors reported on the second invocation.

- [ ] **Step 5: Commit**

```bash
git add app/unit-tests/src/org/commcare/fragments/personalId/BasePersonalIdPhotoCaptureFragmentTest.kt \
        app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
git commit -m "Scaffold PersonalIdPhotoCaptureFragment test infrastructure [AI]"
```

---

## Task 2: UI initial state tests (4 tests)

**File:**
- Modify: `app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt`

- [ ] **Step 1: Replace the smoke test with the 4 initial-state tests**

Replace the existing `testFragmentSmokeTest_fragmentViewInflated` body and add three more tests. The class body becomes:

```kotlin
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdPhotoCaptureFragmentTest : BasePersonalIdPhotoCaptureFragmentTest() {

    // ========== UI initial state ==========

    @Test
    fun testInitialState_titleContainsUserName() {
        val title = fragment.view!!.findViewById<TextView>(R.id.title)
        assertEquals(
            fragment.getString(R.string.personalid_photo_capture_title, "Test User"),
            title.text.toString(),
        )
    }

    @Test
    fun testInitialState_savePhotoButtonDisabled() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        assertFalse("Save button should be disabled initially", saveButton.isEnabled)
    }

    @Test
    fun testInitialState_takePhotoButtonEnabled() {
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        assertTrue("Take photo button should be enabled initially", takeButton.isEnabled)
    }

    @Test
    fun testInitialState_errorTextViewHidden() {
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)
        assertEquals(View.GONE, errorView.visibility)
    }
}
```

Add the imports needed:

```kotlin
import android.widget.Button
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
```

- [ ] **Step 2: Run all four tests**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest.testInitialState_*"
```

Expected: 4 tests pass.

- [ ] **Step 3: ktlint format + verify**

```bash
ktlint --format app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
ktlint app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
```

- [ ] **Step 4: Commit**

```bash
git add app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
git commit -m "Add UI initial-state tests for PersonalIdPhotoCaptureFragment [AI]"
```

---

## Task 3: Take photo flow test (1 test)

**File:**
- Modify: `app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt`

This test verifies that clicking "Take Photo" disables the button and invokes `takePhotoLauncher.launch(intent)` with the expected `Intent` extras. Because `ActivityResultLauncher.launch` going through `ActivityResultRegistry` is not reliably observable via `ShadowActivity.peekNextStartedActivity()`, we replace the launcher with a mock and verify directly.

- [ ] **Step 1: Add the test method**

Append to the test class body (above the closing brace):

```kotlin
    // ========== Take photo flow ==========

    @Test
    fun testTakePhotoClick_disablesButtonAndLaunchesMicroImageActivity() {
        // Arrange: swap in a mock launcher so we can capture the intent.
        @Suppress("UNCHECKED_CAST")
        val mockLauncher = Mockito.mock(ActivityResultLauncher::class.java)
            as ActivityResultLauncher<Intent>
        fragment.replaceTakePhotoLauncher(mockLauncher)

        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)

        // Act
        activity.runOnUiThread { takeButton.performClick() }
        ShadowLooper.idleMainLooper()

        // Assert: button disabled and launcher invoked with the right Intent.
        assertFalse("Take photo button should be disabled after click", takeButton.isEnabled)
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        Mockito.verify(mockLauncher).launch(intentCaptor.capture())
        val launched = intentCaptor.value
        assertEquals(
            MicroImageActivity::class.java.name,
            launched.component?.className,
        )
        assertEquals(
            160,
            launched.getIntExtra(MicroImageActivity.MICRO_IMAGE_MAX_DIMENSION_PX_EXTRA, -1),
        )
        assertEquals(
            100 * 1024,
            launched.getIntExtra(MicroImageActivity.MICRO_IMAGE_MAX_SIZE_BYTES_EXTRA, -1),
        )
    }
```

Add these imports at the top of the file:

```kotlin
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import org.commcare.fragments.MicroImageActivity
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.robolectric.shadows.ShadowLooper
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest.testTakePhotoClick_disablesButtonAndLaunchesMicroImageActivity"
```

Expected: PASS.

If the launcher mock cast fails with `ClassCastException` due to type erasure on Mockito mocks, switch the mock construction to:

```kotlin
val mockLauncher = Mockito.mock(ActivityResultLauncher::class.java)
        as ActivityResultLauncher<Intent>
```

(already shown above; this is for reference).

- [ ] **Step 3: ktlint format + verify**

```bash
ktlint --format app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
ktlint app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
```

- [ ] **Step 4: Commit**

```bash
git add app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
git commit -m "Add take-photo button click test for PersonalIdPhotoCaptureFragment [AI]"
```

---

## Task 4: Photo result handling tests (2 tests)

**File:**
- Modify: `app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt`

Drives the registered `ActivityResultCallback` via `TestablePersonalIdPhotoCaptureFragment.simulatePhotoResult(...)` and asserts on the resulting button state and image-set call.

- [ ] **Step 1: Add the two tests**

Append to the test class body:

```kotlin
    // ========== Photo result handling ==========

    @Test
    fun testPhotoResult_onSuccess_displaysImageAndEnablesSaveButton() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val photoView = fragment.view!!.findViewById<ImageView>(R.id.photo_image_view)
        // Click first so the take-photo button gets disabled (the path under test re-enables it).
        activity.runOnUiThread {
            fragment.replaceTakePhotoLauncher(Mockito.mock(ActivityResultLauncher::class.java) as ActivityResultLauncher<Intent>)
            takeButton.performClick()
        }
        ShadowLooper.idleMainLooper()

        // Act: simulate the user finishing photo capture.
        activity.runOnUiThread {
            fragment.simulatePhotoResult(Activity.RESULT_OK, "fake-base64-photo")
        }
        ShadowLooper.idleMainLooper()

        // Assert
        assertTrue("Save button should be enabled after successful capture", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable after capture", takeButton.isEnabled)
        mediaUtilMock.verify {
            MediaUtil.decodeBase64EncodedBitmap("fake-base64-photo")
        }
        // photo_image_view should now have a drawable set from the stub bitmap.
        assertNotNull("ImageView drawable should be set", photoView.drawable)
    }

    @Test
    fun testPhotoResult_onCancel_keepsSaveDisabledAndReenablesTakeButton() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        activity.runOnUiThread {
            fragment.replaceTakePhotoLauncher(Mockito.mock(ActivityResultLauncher::class.java) as ActivityResultLauncher<Intent>)
            takeButton.performClick()
        }
        ShadowLooper.idleMainLooper()

        // Act: simulate cancellation.
        activity.runOnUiThread {
            fragment.simulatePhotoResult(Activity.RESULT_CANCELED, null)
        }
        ShadowLooper.idleMainLooper()

        // Assert
        assertFalse("Save button should remain disabled on cancel", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable on cancel", takeButton.isEnabled)
        mediaUtilMock.verify(
            { MediaUtil.decodeBase64EncodedBitmap(Mockito.anyString()) },
            Mockito.never(),
        )
    }
```

Add imports:

```kotlin
import android.app.Activity
import android.widget.ImageView
```

- [ ] **Step 2: Run both tests**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest.testPhotoResult_*"
```

Expected: 2 tests pass.

- [ ] **Step 3: ktlint format + verify**

```bash
ktlint --format app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
ktlint app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
```

- [ ] **Step 4: Commit**

```bash
git add app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
git commit -m "Add photo-result handling tests for PersonalIdPhotoCaptureFragment [AI]"
```

---

## Task 5: Save click → completeProfile call test (1 test)

**File:**
- Modify: `app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt`

Verifies that pressing "Save Photo" with a captured photo invokes `ApiPersonalId.setPhotoAndCompleteProfile` with the right args and that both buttons disable + error clears.

- [ ] **Step 1: Add the test**

Append:

```kotlin
    // ========== Save / completeProfile ==========

    @Test
    fun testSavePhotoClick_callsCompleteProfileWithCorrectArgs() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)

        // Arrange: simulate a captured photo so the save button is enabled and
        // photoAsBase64 is populated.
        activity.runOnUiThread {
            fragment.replaceTakePhotoLauncher(Mockito.mock(ActivityResultLauncher::class.java) as ActivityResultLauncher<Intent>)
            takeButton.performClick()
            fragment.simulatePhotoResult(Activity.RESULT_OK, "fake-base64-photo")
            // Seed a visible error so we can verify clearError() fires on save click.
            errorView.visibility = View.VISIBLE
            errorView.text = "stale error"
        }
        ShadowLooper.idleMainLooper()

        // Act
        activity.runOnUiThread { saveButton.performClick() }
        ShadowLooper.idleMainLooper()

        // Assert: API called with the session's args.
        apiPersonalIdMock.verify {
            ApiPersonalId.setPhotoAndCompleteProfile(
                Mockito.any(),
                Mockito.eq("Test User"),
                Mockito.eq("fake-base64-photo"),
                Mockito.eq("123456"),
                Mockito.eq("test-token"),
                Mockito.any(),
            )
        }
        // Buttons disabled while in flight.
        assertFalse("Save button disabled while save in flight", saveButton.isEnabled)
        assertFalse("Take photo button disabled while save in flight", takeButton.isEnabled)
        // Error cleared.
        assertEquals(View.GONE, errorView.visibility)
        assertEquals("", errorView.text.toString())
    }
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest.testSavePhotoClick_callsCompleteProfileWithCorrectArgs"
```

Expected: PASS.

If `Mockito.eq(...)` arguments mix with `Mockito.any()` and Mockito complains "Invalid use of argument matchers", make sure every parameter to `setPhotoAndCompleteProfile` is wrapped in a matcher (no raw literals).

- [ ] **Step 3: ktlint + verify**

```bash
ktlint --format app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
ktlint app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
```

- [ ] **Step 4: Commit**

```bash
git add app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
git commit -m "Add save-click completeProfile invocation test for PersonalIdPhotoCaptureFragment [AI]"
```

---

## Task 6: Success path test — stores user, fires analytics, navigates (1 test)

**File:**
- Modify: `app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt`

Invokes the private `onPhotoUploadSuccess` via the testable helper and asserts on all the side effects.

- [ ] **Step 1: Add the test**

```kotlin
    @Test
    fun testCompleteProfile_onSuccess_storesUserAndNavigatesToSuccess() {
        // Act
        activity.runOnUiThread {
            fragment.invokePhotoUploadSuccess("fake-base64-photo")
        }
        ShadowLooper.idleMainLooper()

        // Assert: passphrase stored.
        connectDatabaseHelperMock.verify {
            ConnectDatabaseHelper.handleReceivedDbPassphrase(Mockito.any(), Mockito.eq("test-db-key"))
        }

        // Assert: user stored.
        val userCaptor = ArgumentCaptor.forClass(ConnectUserRecord::class.java)
        connectUserDatabaseUtilMock.verify {
            ConnectUserDatabaseUtil.storeUser(Mockito.any(), userCaptor.capture())
        }
        val storedUser = userCaptor.value
        assertEquals("Test User", storedUser.name)
        assertEquals("test-personal-id", storedUser.userId)
        assertEquals("fake-base64-photo", storedUser.photo)
        assertEquals("+11234567890", storedUser.primaryPhone)

        // Assert: analytics fired.
        firebaseAnalyticsUtilMock.verify {
            FirebaseAnalyticsUtil.reportPersonalIdAccountCreated()
        }

        // Assert: navigated to success message.
        assertEquals(R.id.personalid_message_display, navController.currentDestination?.id)
        val args = navController.backStack.last().arguments
        assertEquals(
            fragment.getString(R.string.connect_register_success_title),
            args?.getString("title"),
        )
        assertEquals(
            fragment.getString(R.string.connect_register_success_message),
            args?.getString("message"),
        )
        assertEquals(false, args?.getBoolean("isCancellable"))
        assertEquals(
            ConnectConstants.PERSONALID_REGISTRATION_SUCCESS,
            args?.getInt("callingClass"),
        )
    }
```

Add imports:

```kotlin
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.ConnectConstants
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
```

- [ ] **Step 2: Run the test**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest.testCompleteProfile_onSuccess_storesUserAndNavigatesToSuccess"
```

Expected: PASS.

- [ ] **Step 3: ktlint + verify**

```bash
ktlint --format app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
ktlint app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
```

- [ ] **Step 4: Commit**

```bash
git add app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
git commit -m "Add success-path test for PersonalIdPhotoCaptureFragment [AI]"
```

---

## Task 7: Failure path tests (3 tests)

**File:**
- Modify: `app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt`

Covers the three distinct branches in `onCompleteProfileFailure`:
1. `ACCOUNT_LOCKED_ERROR` → handled by `handleCommonSignupFailures` → navigates to error message.
2. Retryable code (`SERVER_ERROR`) → no nav, error shown, buttons re-enabled.
3. Non-retryable code (`TOKEN_INVALID_ERROR`) → no nav, error shown, buttons stay disabled.

- [ ] **Step 1: Add the three tests**

```kotlin
    @Test
    fun testCompleteProfile_onAccountLocked_navigatesToFailureMessageDisplay() {
        // Act
        activity.runOnUiThread {
            fragment.invokeCompleteProfileFailure(
                PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR,
                null,
            )
        }
        ShadowLooper.idleMainLooper()

        // Assert: navigated to the configuration-failed message display.
        assertEquals(R.id.personalid_message_display, navController.currentDestination?.id)
        val args = navController.backStack.last().arguments
        assertEquals(
            fragment.getString(R.string.personalid_configuration_process_failed_title),
            args?.getString("title"),
        )
        assertEquals(false, args?.getBoolean("isCancellable"))
    }

    @Test
    fun testCompleteProfile_onRetryableFailure_reenablesButtonsAndShowsError() {
        // Arrange: simulate a captured photo so we have a non-empty button-state baseline.
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)
        activity.runOnUiThread {
            fragment.replaceTakePhotoLauncher(Mockito.mock(ActivityResultLauncher::class.java) as ActivityResultLauncher<Intent>)
            takeButton.performClick()
            fragment.simulatePhotoResult(Activity.RESULT_OK, "fake-base64-photo")
            saveButton.performClick() // disables both buttons
        }
        ShadowLooper.idleMainLooper()

        // Act: simulate the API failing with a retryable error.
        activity.runOnUiThread {
            fragment.invokeCompleteProfileFailure(
                PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                RuntimeException("boom"),
            )
        }
        ShadowLooper.idleMainLooper()

        // Assert: still on photo capture screen.
        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)
        // Error visible with the handler's returned text.
        assertEquals(View.VISIBLE, errorView.visibility)
        assertEquals("Network error occurred", errorView.text.toString())
        // Both buttons re-enabled.
        assertTrue("Save button should re-enable on retryable failure", saveButton.isEnabled)
        assertTrue("Take photo button should re-enable on retryable failure", takeButton.isEnabled)
    }

    @Test
    fun testCompleteProfile_onNonRetryableFailure_buttonsStayDisabled() {
        val saveButton = fragment.view!!.findViewById<Button>(R.id.save_photo_button)
        val takeButton = fragment.view!!.findViewById<Button>(R.id.take_photo_button)
        val errorView = fragment.view!!.findViewById<TextView>(R.id.errorTextView)
        activity.runOnUiThread {
            fragment.replaceTakePhotoLauncher(Mockito.mock(ActivityResultLauncher::class.java) as ActivityResultLauncher<Intent>)
            takeButton.performClick()
            fragment.simulatePhotoResult(Activity.RESULT_OK, "fake-base64-photo")
            saveButton.performClick() // disables both buttons
        }
        ShadowLooper.idleMainLooper()

        // Act: non-retryable failure (not handled by handleCommonSignupFailures).
        activity.runOnUiThread {
            fragment.invokeCompleteProfileFailure(
                PersonalIdOrConnectApiErrorCodes.TOKEN_INVALID_ERROR,
                null,
            )
        }
        ShadowLooper.idleMainLooper()

        // Assert: no nav, error shown, buttons stay disabled.
        assertEquals(R.id.personalid_photo_capture, navController.currentDestination?.id)
        assertEquals(View.VISIBLE, errorView.visibility)
        assertEquals("Network error occurred", errorView.text.toString())
        assertFalse("Save button should stay disabled on non-retryable failure", saveButton.isEnabled)
        assertFalse("Take photo button should stay disabled on non-retryable failure", takeButton.isEnabled)
    }
```

Add import:

```kotlin
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
```

- [ ] **Step 2: Run all three failure tests**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest.testCompleteProfile_on*Failure*" \
    --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest.testCompleteProfile_onAccountLocked_navigatesToFailureMessageDisplay"
```

Expected: 3 tests pass.

- [ ] **Step 3: ktlint + verify**

```bash
ktlint --format app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
ktlint app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
```

- [ ] **Step 4: Commit**

```bash
git add app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
git commit -m "Add failure-path tests for PersonalIdPhotoCaptureFragment [AI]"
```

---

## Task 8: Run full test class + final verification

- [ ] **Step 1: Run the full test class**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdPhotoCaptureFragmentTest"
```

Expected: 11 tests pass.

- [ ] **Step 2: Run the sibling tests too as a regression check**

```bash
./gradlew :app:testCommcareDebugUnitTest --tests \
    "org.commcare.fragments.personalId.PersonalIdBiometricConfigFragmentTest" --tests \
    "org.commcare.fragments.personalId.PersonalIdPhoneFragmentTest"
```

Expected: existing tests still pass — no regression from the shared static mock setup.

- [ ] **Step 3: Final ktlint check on both new files**

```bash
ktlint app/unit-tests/src/org/commcare/fragments/personalId/BasePersonalIdPhotoCaptureFragmentTest.kt app/unit-tests/src/org/commcare/fragments/personalId/PersonalIdPhotoCaptureFragmentTest.kt
```

Expected: no errors.

- [ ] **Step 4: If steps 1–3 all pass, no commit needed (final test was already committed in Task 7). If any fix-ups were made, commit them as a final clean-up commit.**

---

## Coverage check (self-review)

| Spec test case | Plan task | Test name |
|---|---|---|
| 1. Title contains userName | Task 2 | `testInitialState_titleContainsUserName` |
| 2. Save button disabled | Task 2 | `testInitialState_savePhotoButtonDisabled` |
| 3. Take photo button enabled | Task 2 | `testInitialState_takePhotoButtonEnabled` |
| 4. Error TextView hidden | Task 2 | `testInitialState_errorTextViewHidden` |
| 5. Take photo click + intent | Task 3 | `testTakePhotoClick_disablesButtonAndLaunchesMicroImageActivity` |
| 6. Photo result success | Task 4 | `testPhotoResult_onSuccess_displaysImageAndEnablesSaveButton` |
| 7. Photo result cancel | Task 4 | `testPhotoResult_onCancel_keepsSaveDisabledAndReenablesTakeButton` |
| 8. Save click → API call | Task 5 | `testSavePhotoClick_callsCompleteProfileWithCorrectArgs` |
| 9. Success → store + nav | Task 6 | `testCompleteProfile_onSuccess_storesUserAndNavigatesToSuccess` |
| 10. Account locked failure | Task 7 | `testCompleteProfile_onAccountLocked_navigatesToFailureMessageDisplay` |
| 11. Retryable failure | Task 7 | `testCompleteProfile_onRetryableFailure_reenablesButtonsAndShowsError` |
| 12. Non-retryable failure | Task 7 | `testCompleteProfile_onNonRetryableFailure_buttonsStayDisabled` |

All 11 spec test cases (plus the one extra non-retryable case added during brainstorming) have a corresponding task. No production code changes were required.

# Form Exit Warning on Push Notification Tap — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Insert the existing quit-form warning dialog into the navigation flow when a tap on a navigation push notification would otherwise tear down `FormEntryActivity` with an unsaved form.

**Architecture:** A new transparent `PushNotificationLaunchActivity` becomes the `PendingIntent` target for every navigation push notification. At tap time it queries `FormEntryActivity.isFormEntryInProgress()`. If no form is active it dispatches the original target as today. If a form is active it re-enters `FormEntryActivity` in `singleTop` carrying the wrapped target as an Intent extra; `onNewIntent` then routes through a new `triggerUserQuitInputForExternalNav` that reuses the existing quit dialog but additionally starts the wrapped Intent on Discard/Save.

**Tech Stack:** Android (Kotlin for new code, Java for edits to existing Java files), Robolectric + JUnit4 for unit tests, Mockito for static mocking. Built with Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-05-11-form-exit-warning-on-push-design.md`

---

## File Structure

**New files:**

- `app/src/org/commcare/activities/PushNotificationLaunchActivity.kt` — transparent dispatcher activity. Reads wrapped Intent extra, branches on `FormEntryActivity.isFormEntryInProgress()`, dispatches, finishes.
- `app/unit-tests/src/org/commcare/activities/PushNotificationLaunchActivityTest.kt` — Robolectric tests for the two dispatch branches.
- `app/unit-tests/src/org/commcare/activities/components/FormEntryDialogsExternalNavTest.kt` — unit tests for the new `createQuitDialog` overload.
- `app/unit-tests/src/org/commcare/utils/FirebaseMessagingUtilBuildNotificationTest.kt` — unit test asserting the PendingIntent now targets the launch activity and preserves the wrapped target.

**Modified files:**

- `app/src/org/commcare/utils/FirebaseMessagingUtil.java` — change `buildNotification` to wrap the target Intent and target the launch activity.
- `app/src/org/commcare/activities/components/FormEntryDialogs.java` — add `createQuitDialog(activity, isIncompleteEnabled, pendingNav)` overload.
- `app/src/org/commcare/activities/FormEntryActivity.java` — add `EXTRA_PENDING_NAV_INTENT` constant, `onNewIntent` override, `triggerUserQuitInputForExternalNav`, `mPendingNavAfterSave` field + persist in `onSaveInstanceState`/restore, consume in `savingComplete`.
- `app/src/org/commcare/google/services/analytics/AnalyticsParamValue.java` — add `PUSH_NOTIFICATION_TAP` constant.
- `app/AndroidManifest.xml` — register `PushNotificationLaunchActivity`.

---

## Task 1: Add `PUSH_NOTIFICATION_TAP` analytics constant

**Files:**
- Modify: `app/src/org/commcare/google/services/analytics/AnalyticsParamValue.java`

- [ ] **Step 1: Add the constant**

In `AnalyticsParamValue.java`, alongside `BACK_BUTTON_PRESS` and `NAV_BUTTON_PRESS` (around line 17–18), add:

```java
public static final String PUSH_NOTIFICATION_TAP = "push_notification_tap";
```

- [ ] **Step 2: Build to verify**

Run from `C:\CODE\Work\commcare-android`:

```
./gradlew :app:compileCommcareDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```
git add app/src/org/commcare/google/services/analytics/AnalyticsParamValue.java
git commit -m "Add PUSH_NOTIFICATION_TAP analytics param value [AI]"
```

---

## Task 2: Add `createQuitDialog` overload with pending nav intent

This task is test-first.

**Files:**
- Create: `app/unit-tests/src/org/commcare/activities/components/FormEntryDialogsExternalNavTest.kt`
- Modify: `app/src/org/commcare/activities/components/FormEntryDialogs.java`

### Step 1: Write failing tests

- [ ] **Create the test file**

Create `app/unit-tests/src/org/commcare/activities/components/FormEntryDialogsExternalNavTest.kt`:

```kotlin
package org.commcare.activities.components

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.FormEntryActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FormEntryDialogsExternalNavTest {

    private fun newPendingNav(): Intent {
        val ctx = CommCareTestApplication.instance()
        return Intent(ctx, FormEntryActivity::class.java).putExtra("marker", "x")
    }

    @Test
    fun discardChoice_runsDiscardAndStartsPendingNav() {
        val activity = mock(FormEntryActivity::class.java)
        val pendingNav = newPendingNav()

        FormEntryDialogs.createQuitDialog(activity, true, pendingNav)

        // Locate the discard listener via the dialog Spy is complex; assert via behavior:
        // We invoke the helper exposed for testing.
        FormEntryDialogs.invokeDiscardListenerForTest(activity, pendingNav)

        verify(activity, times(1)).discardChangesAndExit()
        val captor = ArgumentCaptor.forClass(Intent::class.java)
        verify(activity, times(1)).startActivity(captor.capture())
        assertEquals("x", captor.value.getStringExtra("marker"))
    }

    @Test
    fun saveChoice_stashesPendingNavThenSaves() {
        val activity = mock(FormEntryActivity::class.java)
        val pendingNav = newPendingNav()

        FormEntryDialogs.invokeSaveListenerForTest(activity, pendingNav)

        verify(activity, times(1)).setPendingNavAfterSave(pendingNav)
        verify(activity, times(1)).saveFormToDisk(FormEntryConstants.EXIT)
        verify(activity, never()).startActivity(org.mockito.kotlin.any())
    }

    @Test
    fun stayChoice_doesNothingToActivity() {
        val activity = mock(FormEntryActivity::class.java)
        val pendingNav = newPendingNav()

        FormEntryDialogs.invokeStayListenerForTest(activity, pendingNav)

        verify(activity, never()).discardChangesAndExit()
        verify(activity, never()).saveFormToDisk(org.mockito.kotlin.any())
        verify(activity, never()).startActivity(org.mockito.kotlin.any())
    }
}
```

> Rationale: the existing dialog code uses `View.OnClickListener` wired into `PaneledChoiceDialog`. Reaching those listeners through the actual dialog in unit tests is brittle — the dialog inflates views that depend on Robolectric setup not present in the existing dialog tests. Instead we expose three package-private test helpers (`invokeDiscardListenerForTest`, `invokeSaveListenerForTest`, `invokeStayListenerForTest`) on `FormEntryDialogs` that contain the exact body of each listener. Production code calls these from inside the listener lambdas, so production and tests exercise the same branch.

- [ ] **Run the tests to confirm they fail**

Run from `C:\CODE\Work\commcare-android`:

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.activities.components.FormEntryDialogsExternalNavTest"
```

Expected: COMPILATION FAILURE (the overload + helpers don't exist yet) or test failure on missing symbols.

### Step 2: Implement

- [ ] **Add the overload and test helpers to `FormEntryDialogs.java`**

Open `app/src/org/commcare/activities/components/FormEntryDialogs.java`. After the existing `createQuitDialog(FormEntryActivity, boolean)` method (ends around line 73), add:

```java
/**
 * Quit dialog variant for the case where exiting the form would also navigate the user
 * elsewhere (e.g., tapping a push notification while the form is open).
 * Stay dismisses and drops pendingNav. Discard calls discardChangesAndExit() then starts
 * pendingNav. Save stashes pendingNav on the activity and triggers an EXIT-flavored save;
 * the activity dispatches pendingNav in savingComplete after the save succeeds.
 */
public static void createQuitDialog(final FormEntryActivity activity,
                                    boolean isIncompleteEnabled,
                                    final Intent pendingNav) {
    final PaneledChoiceDialog dialog = new PaneledChoiceDialog(activity,
            StringUtils.getStringRobust(activity, R.string.quit_form_title));

    View.OnClickListener stayInFormListener = v -> {
        invokeStayListenerForTest(activity, pendingNav);
        dialog.dismiss();
    };
    DialogChoiceItem stayInFormItem = new DialogChoiceItem(
            StringUtils.getStringRobust(activity, R.string.do_not_exit),
            LocalePreferences.isLocaleRTL() ? R.drawable.ic_blue_backward : R.drawable.ic_blue_forward,
            stayInFormListener);

    View.OnClickListener exitFormListener = v -> {
        dialog.dismiss();
        hideVirtualKeyboard(activity);
        invokeDiscardListenerForTest(activity, pendingNav);
    };
    DialogChoiceItem quitFormItem = new DialogChoiceItem(
            StringUtils.getStringRobust(activity, R.string.do_not_save),
            R.drawable.icon_exit_form,
            exitFormListener);

    DialogChoiceItem[] items;
    if (isIncompleteEnabled) {
        View.OnClickListener saveIncompleteListener = v -> {
            invokeSaveListenerForTest(activity, pendingNav);
            dialog.dismiss();
        };
        DialogChoiceItem saveIncompleteItem = new DialogChoiceItem(
                StringUtils.getStringRobust(activity, R.string.keep_changes),
                R.drawable.ic_incomplete_orange,
                saveIncompleteListener);
        items = new DialogChoiceItem[]{stayInFormItem, quitFormItem, saveIncompleteItem};
    } else {
        items = new DialogChoiceItem[]{stayInFormItem, quitFormItem};
    }
    dialog.setChoiceItems(items);
    activity.showAlertDialog(dialog);
}

// Package-private hooks so unit tests can exercise the same code each listener runs.
// These contain the entire body of the corresponding listener (apart from dialog.dismiss()
// and keyboard hiding, which are UI concerns not behavior-relevant for the activity).

static void invokeStayListenerForTest(FormEntryActivity activity, Intent pendingNav) {
    // Stay deliberately drops pendingNav; method exists so tests can assert the no-op.
}

static void invokeDiscardListenerForTest(FormEntryActivity activity, Intent pendingNav) {
    activity.discardChangesAndExit();
    activity.startActivity(pendingNav);
}

static void invokeSaveListenerForTest(FormEntryActivity activity, Intent pendingNav) {
    activity.setPendingNavAfterSave(pendingNav);
    activity.saveFormToDisk(FormEntryConstants.EXIT);
}
```

Add the import `import android.content.Intent;` near the top of the file if not already imported (the existing file imports `android.content.DialogInterface` so `android.content` is present).

> Note: `setPendingNavAfterSave` does not exist on `FormEntryActivity` yet — Task 5 adds it. Until then the code in `FormEntryDialogs.java` will fail to compile. That's expected; the next steps in this task only check that the test file's structure is correct via a partial build. The full build is verified after Task 5.

- [ ] **Stub the method on FormEntryActivity so this task compiles in isolation**

In `app/src/org/commcare/activities/FormEntryActivity.java`, add a placeholder method near the other public helpers (just above `isFormEntryInProgress()` at line 1770 is a good spot):

```java
public void setPendingNavAfterSave(android.content.Intent pendingNav) {
    // Will be implemented in a later task.
}
```

This is a temporary stub. Task 5 replaces it with the real implementation and removes this comment.

- [ ] **Run the tests**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.activities.components.FormEntryDialogsExternalNavTest"
```

Expected: BUILD SUCCESSFUL, 3 tests passing.

- [ ] **Commit**

```
git add app/src/org/commcare/activities/components/FormEntryDialogs.java
git add app/src/org/commcare/activities/FormEntryActivity.java
git add app/unit-tests/src/org/commcare/activities/components/FormEntryDialogsExternalNavTest.kt
git commit -m "Add createQuitDialog overload with pending nav intent [AI]"
```

---

## Task 3: Add `PushNotificationLaunchActivity` (form-inactive branch)

This task introduces the new activity with only the form-inactive dispatch path implemented and tested. The form-active branch is added in Task 4. Splitting keeps the diffs small.

**Files:**
- Create: `app/src/org/commcare/activities/PushNotificationLaunchActivity.kt`
- Create: `app/unit-tests/src/org/commcare/activities/PushNotificationLaunchActivityTest.kt`
- Modify: `app/AndroidManifest.xml`

### Step 1: Write the failing test

- [ ] **Create the test file**

Create `app/unit-tests/src/org/commcare/activities/PushNotificationLaunchActivityTest.kt`:

```kotlin
package org.commcare.activities

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PushNotificationLaunchActivityTest {

    @After
    fun tearDown() {
        // Ensure no in-progress form state leaks between tests.
        FormEntryActivity.setFormEntryInProgressForTest(false)
    }

    @Test
    fun whenNoFormInProgress_startsWrappedIntentWithClearTaskNewTaskFlags() {
        FormEntryActivity.setFormEntryInProgressForTest(false)
        val ctx = CommCareTestApplication.instance()
        val wrapped = Intent(ctx, DispatchActivity::class.java).putExtra("payload", "p1")

        val launchIntent = Intent(ctx, PushNotificationLaunchActivity::class.java)
            .putExtra(PushNotificationLaunchActivity.EXTRA_WRAPPED_NAV_INTENT, wrapped)

        val controller = Robolectric.buildActivity(
            PushNotificationLaunchActivity::class.java, launchIntent
        ).create()
        val activity = controller.get()

        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(DispatchActivity::class.java.name, started.component?.className)
        assertEquals("p1", started.getStringExtra("payload"))
        val expectedFlags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        assertTrue((started.flags and expectedFlags) == expectedFlags)
        assertTrue(activity.isFinishing)
    }
}
```

- [ ] **Run the test to confirm it fails**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.activities.PushNotificationLaunchActivityTest"
```

Expected: COMPILATION FAILURE — `PushNotificationLaunchActivity` and `setFormEntryInProgressForTest` do not exist.

### Step 2: Add the test-only setter on FormEntryActivity

- [ ] **Add the @VisibleForTesting setter**

In `app/src/org/commcare/activities/FormEntryActivity.java`, immediately after `isFormEntryInProgress()` at line 1770–1772, add:

```java
@androidx.annotation.VisibleForTesting
public static void setFormEntryInProgressForTest(boolean inProgress) {
    isFormEntryActive = inProgress;
}
```

### Step 3: Implement the activity (inactive branch only)

- [ ] **Create `PushNotificationLaunchActivity.kt`**

Create `app/src/org/commcare/activities/PushNotificationLaunchActivity.kt`:

```kotlin
package org.commcare.activities

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger

/**
 * Transparent, no-UI launch activity for push-notification PendingIntents.
 *
 * Decides at tap time whether to:
 *   - dispatch the wrapped Intent directly (no form in progress), or
 *   - re-enter [FormEntryActivity] in singleTop carrying the wrapped Intent so the
 *     user is prompted before navigation occurs (form in progress) — added in
 *     a follow-up task.
 */
class PushNotificationLaunchActivity : Activity() {

    companion object {
        const val EXTRA_WRAPPED_NAV_INTENT = "org.commcare.push.wrapped_nav_intent"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wrapped: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_WRAPPED_NAV_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_WRAPPED_NAV_INTENT)
        }

        if (wrapped == null) {
            Logger.log(LogTypes.TYPE_FCM,
                "PushNotificationLaunchActivity received intent without wrapped nav intent")
            finish()
            return
        }

        dispatchWithoutForm(wrapped)
        finish()
    }

    private fun dispatchWithoutForm(wrapped: Intent) {
        wrapped.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(wrapped)
        } catch (e: android.content.ActivityNotFoundException) {
            Logger.exception("Push notification target activity not found", e)
        }
    }
}
```

### Step 4: Register the activity in the manifest

- [ ] **Edit `app/AndroidManifest.xml`**

Locate the `<activity android:name="org.commcare.activities.FormEntryActivity"` block (around line 356–359). Add immediately after it:

```xml
<activity
    android:name="org.commcare.activities.PushNotificationLaunchActivity"
    android:theme="@android:style/Theme.NoDisplay"
    android:taskAffinity=""
    android:excludeFromRecents="true"
    android:launchMode="singleInstance"
    android:exported="false" />
```

### Step 5: Run the test

- [ ] **Verify pass**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.activities.PushNotificationLaunchActivityTest"
```

Expected: BUILD SUCCESSFUL, 1 test passing.

### Step 6: Commit

- [ ] **Commit**

```
git add app/src/org/commcare/activities/PushNotificationLaunchActivity.kt
git add app/src/org/commcare/activities/FormEntryActivity.java
git add app/unit-tests/src/org/commcare/activities/PushNotificationLaunchActivityTest.kt
git add app/AndroidManifest.xml
git commit -m "Add PushNotificationLaunchActivity (form-inactive branch) [AI]"
```

---

## Task 4: Add the form-active branch to `PushNotificationLaunchActivity`

**Files:**
- Modify: `app/src/org/commcare/activities/PushNotificationLaunchActivity.kt`
- Modify: `app/unit-tests/src/org/commcare/activities/PushNotificationLaunchActivityTest.kt`

### Step 1: Add the failing test

- [ ] **Append to `PushNotificationLaunchActivityTest.kt`**

Inside the existing class, add:

```kotlin
@Test
fun whenFormInProgress_reentersFormEntryWithPendingNavExtraAndSingleTop() {
    FormEntryActivity.setFormEntryInProgressForTest(true)
    val ctx = CommCareTestApplication.instance()
    val wrapped = Intent(ctx, DispatchActivity::class.java).putExtra("payload", "p1")

    val launchIntent = Intent(ctx, PushNotificationLaunchActivity::class.java)
        .putExtra(PushNotificationLaunchActivity.EXTRA_WRAPPED_NAV_INTENT, wrapped)

    val controller = Robolectric.buildActivity(
        PushNotificationLaunchActivity::class.java, launchIntent
    ).create()
    val activity = controller.get()

    val started = shadowOf(activity).nextStartedActivity
    assertNotNull(started)
    assertEquals(FormEntryActivity::class.java.name, started.component?.className)

    val expectedFlags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    assertTrue((started.flags and expectedFlags) == expectedFlags)
    assertTrue((started.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK) == 0)

    val carried: Intent? = started.getParcelableExtra(
        FormEntryActivity.EXTRA_PENDING_NAV_INTENT
    )
    assertNotNull(carried)
    assertEquals("p1", carried!!.getStringExtra("payload"))
    assertEquals(DispatchActivity::class.java.name, carried.component?.className)

    assertTrue(activity.isFinishing)
}
```

> Note: `FormEntryActivity.EXTRA_PENDING_NAV_INTENT` does not exist yet — Task 5 introduces it. To unblock this task, add it as a stub now (Step 2).

- [ ] **Stub the constant on FormEntryActivity**

In `app/src/org/commcare/activities/FormEntryActivity.java`, near the other `KEY_*` constants (around lines 125–140), add:

```java
public static final String EXTRA_PENDING_NAV_INTENT =
        "org.commcare.formentry.pending_nav_intent";
```

- [ ] **Run test to confirm failure**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.activities.PushNotificationLaunchActivityTest.whenFormInProgress_reentersFormEntryWithPendingNavExtraAndSingleTop"
```

Expected: FAIL — the new branch is not yet implemented (the activity still calls `dispatchWithoutForm` unconditionally).

### Step 2: Implement the form-active branch

- [ ] **Edit `PushNotificationLaunchActivity.kt`**

Replace the body of `onCreate` after the `wrapped == null` check, and add a new private method, so the class now reads:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val wrapped: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(EXTRA_WRAPPED_NAV_INTENT, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(EXTRA_WRAPPED_NAV_INTENT)
    }

    if (wrapped == null) {
        Logger.log(LogTypes.TYPE_FCM,
            "PushNotificationLaunchActivity received intent without wrapped nav intent")
        finish()
        return
    }

    if (FormEntryActivity.isFormEntryInProgress()) {
        dispatchToFormEntry(wrapped)
    } else {
        dispatchWithoutForm(wrapped)
    }
    finish()
}

private fun dispatchToFormEntry(wrapped: Intent) {
    val reroute = Intent(this, FormEntryActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(FormEntryActivity.EXTRA_PENDING_NAV_INTENT, wrapped)
    try {
        startActivity(reroute)
    } catch (e: android.content.ActivityNotFoundException) {
        Logger.exception("FormEntryActivity not resolvable from launch activity", e)
    }
}
```

### Step 3: Verify

- [ ] **Run all PushNotificationLaunchActivityTest tests**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.activities.PushNotificationLaunchActivityTest"
```

Expected: BUILD SUCCESSFUL, 2 tests passing.

### Step 4: Commit

- [ ] **Commit**

```
git add app/src/org/commcare/activities/PushNotificationLaunchActivity.kt
git add app/src/org/commcare/activities/FormEntryActivity.java
git add app/unit-tests/src/org/commcare/activities/PushNotificationLaunchActivityTest.kt
git commit -m "Add form-active branch to PushNotificationLaunchActivity [AI]"
```

---

## Task 5: Wire up `FormEntryActivity` — `triggerUserQuitInputForExternalNav`, `onNewIntent`, save-callback, instance state

This is the largest task. Test-first: a single test exercises the read-only and not-yet-loaded short-circuits via `onNewIntent`, and the normal-path dialog construction via mocking `FormEntryDialogs`.

**Files:**
- Modify: `app/src/org/commcare/activities/FormEntryActivity.java`
- Create: `app/unit-tests/src/org/commcare/activities/FormEntryActivityExternalNavTest.kt`

### Step 1: Write failing tests

- [ ] **Create the test file**

Create `app/unit-tests/src/org/commcare/activities/FormEntryActivityExternalNavTest.kt`:

```kotlin
package org.commcare.activities

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.components.FormEntryDialogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FormEntryActivityExternalNavTest {

    @After
    fun tearDown() {
        FormEntryActivity.setFormEntryInProgressForTest(false)
    }

    @Test
    fun pendingNavSurvivesSaveInstanceStateRoundTrip() {
        val ctx = CommCareTestApplication.instance()
        val pending = Intent(ctx, DispatchActivity::class.java)
            .putExtra("marker", "m1")

        val controller = Robolectric.buildActivity(FormEntryActivity::class.java).create()
        val activity = controller.get()

        activity.setPendingNavAfterSave(pending)

        val outState = Bundle()
        activity.onSaveInstanceState(outState)

        val restored: Intent? =
            outState.getParcelable(FormEntryActivity.EXTRA_PENDING_NAV_INTENT)
        assertNotNull(restored)
        assertEquals("m1", restored!!.getStringExtra("marker"))
    }

    @Test
    fun onNewIntent_withoutPendingNavExtra_doesNotTriggerExternalQuit() {
        val ctx = CommCareTestApplication.instance()
        val controller = Robolectric.buildActivity(FormEntryActivity::class.java).create()
        val activity = controller.get()

        val mock: MockedStatic<FormEntryDialogs> =
            Mockito.mockStatic(FormEntryDialogs::class.java)
        try {
            activity.onNewIntent(Intent(ctx, FormEntryActivity::class.java))
            mock.verifyNoInteractions()
        } finally {
            mock.close()
        }
    }
}
```

> Why these two tests and not more: the dialog path itself is covered by `FormEntryDialogsExternalNavTest` (Task 2). The dispatch logic in `triggerUserQuitInputForExternalNav` for `formHasLoaded() == false` and the read-only branch depends on internal state (`mFormController`) that the activity initializes only after a real form load. Driving that through Robolectric is out of scope for this plan; those paths are exercised by the instrumentation test in Task 7. The two tests above cover the deterministic plumbing that does not require a loaded form: state persistence and intent routing.

- [ ] **Run the new tests to confirm failure**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.activities.FormEntryActivityExternalNavTest"
```

Expected: tests fail — `setPendingNavAfterSave` is still a stub and writes nothing into `outState`; `onNewIntent` is not overridden.

### Step 2: Implement the real `FormEntryActivity` changes

- [ ] **Remove the Task-2 stub `setPendingNavAfterSave`**

In `app/src/org/commcare/activities/FormEntryActivity.java`, locate and delete the placeholder method added in Task 2 (above `isFormEntryInProgress()`).

- [ ] **Add the field for the pending nav intent**

Near the other `private` fields at the top of the class (e.g. around the `mIncompleteEnabled` declaration on line 153), add:

```java
private Intent mPendingNavAfterSave;
```

- [ ] **Add the real `setPendingNavAfterSave`**

Add this near the other public helpers (just above `isFormEntryInProgress()`):

```java
public void setPendingNavAfterSave(Intent pendingNav) {
    this.mPendingNavAfterSave = pendingNav;
}
```

- [ ] **Persist and restore `mPendingNavAfterSave`**

In `onSaveInstanceState` (the block starting at line 312), after the existing `outState.put*` calls (the last one in the original block, e.g. line 324 `outState.putInt(KEY_NUM_FORM_ATTACHMENTS, formAttachmentCount);`), add:

```java
if (mPendingNavAfterSave != null) {
    outState.putParcelable(EXTRA_PENDING_NAV_INTENT, mPendingNavAfterSave);
}
```

In the savedInstanceState restoration block (around line 1555 where `KEY_INCOMPLETE_ENABLED` is read), after the existing restorations add:

```java
if (savedInstanceState.containsKey(EXTRA_PENDING_NAV_INTENT)) {
    mPendingNavAfterSave = savedInstanceState.getParcelable(EXTRA_PENDING_NAV_INTENT);
}
```

- [ ] **Override `onNewIntent`**

Add the override near the other lifecycle overrides (e.g. just below `onSaveInstanceState`). Import `androidx.annotation.NonNull` if not already imported:

```java
@Override
protected void onNewIntent(@NonNull Intent intent) {
    super.onNewIntent(intent);
    if (intent.hasExtra(EXTRA_PENDING_NAV_INTENT)) {
        Intent pendingNav = intent.getParcelableExtra(EXTRA_PENDING_NAV_INTENT);
        if (pendingNav != null) {
            triggerUserQuitInputForExternalNav(pendingNav);
        }
    }
}
```

- [ ] **Add `triggerUserQuitInputForExternalNav`**

Insert immediately below the existing `triggerUserQuitInput()` (ends around line 1236):

```java
/**
 * Variant of {@link #triggerUserQuitInput()} for the case where the user is being
 * pulled away from the form by an external navigation event (currently: tapping a push
 * notification). On Save / Discard the form's existing exit handling runs and then
 * pendingNav is started so the user reaches the notification's target.
 *
 * Stay drops pendingNav. If a save is already in flight, pendingNav is dropped (the user
 * can re-tap the notification once the current save finishes).
 */
protected void triggerUserQuitInputForExternalNav(Intent pendingNav) {
    FirebaseAnalyticsUtil.reportFormQuitAttempt(
            AnalyticsParamValue.PUSH_NOTIFICATION_TAP,
            getCurrentFormXmlnsFailSafe());

    if (mSaveToDiskTask != null) {
        // A save is already running; do not stack another exit attempt on top of it.
        return;
    }

    if (!formHasLoaded()) {
        startPendingNavSafely(pendingNav);
        finish();
        return;
    }
    if (mFormController != null && mFormController.isFormReadOnly()) {
        finishReturnInstance(false);
        startPendingNavSafely(pendingNav);
        return;
    }
    FormEntryDialogs.createQuitDialog(this, mIncompleteEnabled, pendingNav);
}

private void startPendingNavSafely(Intent pendingNav) {
    try {
        startActivity(pendingNav);
    } catch (android.content.ActivityNotFoundException e) {
        org.javarosa.core.services.Logger.exception(
                "Push notification target activity not found", e);
    }
}
```

- [ ] **Consume `mPendingNavAfterSave` in `savingComplete`**

In `savingComplete` (line 1326), add handling right before `finishReturnInstance();` in the `SAVED_AND_EXIT` branch (line 1357) — change:

```java
case SAVED_AND_EXIT:
    hasSaved = true;
    if (!CommCareApplication.instance().isConsumerApp()) {
        Toast.makeText(this,
                Localization.get("form.entry.complete.save.success"), Toast.LENGTH_SHORT).show();
    }
    finishReturnInstance();
    return;
```

to:

```java
case SAVED_AND_EXIT:
    hasSaved = true;
    if (!CommCareApplication.instance().isConsumerApp()) {
        Toast.makeText(this,
                Localization.get("form.entry.complete.save.success"), Toast.LENGTH_SHORT).show();
    }
    finishReturnInstance();
    consumePendingNavAfterSave();
    return;
```

And in the same file, add the helper alongside the existing helpers:

```java
private void consumePendingNavAfterSave() {
    if (mPendingNavAfterSave != null) {
        Intent toStart = mPendingNavAfterSave;
        mPendingNavAfterSave = null;
        startPendingNavSafely(toStart);
    }
}
```

> Why `SAVED_AND_EXIT` and not `SAVED_INCOMPLETE`: `saveFormToDisk(FormEntryConstants.EXIT)` produces a `SAVED_AND_EXIT` status (see `SaveToDiskTask.java:145`). `SAVED_INCOMPLETE` only fires when the save was triggered without the exit flag. The dialog's Save listener always passes `EXIT`, so `SAVED_AND_EXIT` is the correct branch.

- [ ] **Add the required imports to `FormEntryActivity.java`**

At the top of the file, ensure these imports exist (some may already be present):

```java
import androidx.annotation.NonNull;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
```

### Step 3: Run all unit tests added or touched so far

- [ ] **Run the FormEntryActivity tests**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.activities.FormEntryActivityExternalNavTest" --tests "org.commcare.activities.PushNotificationLaunchActivityTest" --tests "org.commcare.activities.components.FormEntryDialogsExternalNavTest"
```

Expected: BUILD SUCCESSFUL, all tests passing.

### Step 4: Commit

- [ ] **Commit**

```
git add app/src/org/commcare/activities/FormEntryActivity.java
git add app/unit-tests/src/org/commcare/activities/FormEntryActivityExternalNavTest.kt
git commit -m "Route push-notification-driven exits through quit dialog [AI]"
```

---

## Task 6: Update `FirebaseMessagingUtil.buildNotification` to target the launch activity

**Files:**
- Modify: `app/src/org/commcare/utils/FirebaseMessagingUtil.java`
- Create: `app/unit-tests/src/org/commcare/utils/FirebaseMessagingUtilBuildNotificationTest.kt`

### Step 1: Write failing test

- [ ] **Create the test**

Create `app/unit-tests/src/org/commcare/utils/FirebaseMessagingUtilBuildNotificationTest.kt`:

```kotlin
package org.commcare.utils

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.DispatchActivity
import org.commcare.activities.PushNotificationLaunchActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FirebaseMessagingUtilBuildNotificationTest {

    @Test
    fun buildNotification_pendingIntentTargetsLaunchActivityAndWrapsOriginal() {
        val ctx = CommCareTestApplication.instance()
        val originalTarget = Intent(ctx, DispatchActivity::class.java)
            .putExtra("originalKey", "originalValue")

        val payload = HashMap<String, String>().apply {
            put(FirebaseMessagingUtil.NOTIFICATION_TITLE, "title")
            put(FirebaseMessagingUtil.NOTIFICATION_BODY, "body")
        }
        val fcm = FCMMessageData(payload)

        val builder = FirebaseMessagingUtil.buildNotificationForTest(ctx, originalTarget, fcm)
        val notification = builder.build()
        val contentIntent: PendingIntent = notification.contentIntent
        assertNotNull(contentIntent)

        val shadowPi = shadowOf(contentIntent)
        val savedIntent: Intent = shadowPi.savedIntent
        assertEquals(
            PushNotificationLaunchActivity::class.java.name,
            savedIntent.component?.className
        )
        val wrapped: Intent? = savedIntent.getParcelableExtra(
            PushNotificationLaunchActivity.EXTRA_WRAPPED_NAV_INTENT
        )
        assertNotNull(wrapped)
        assertEquals(DispatchActivity::class.java.name, wrapped!!.component?.className)
        assertEquals("originalValue", wrapped.getStringExtra("originalKey"))
    }
}
```

> The current `buildNotification` is `private`. We expose a package-friendly `buildNotificationForTest` that simply delegates. Reaching the existing private method via reflection would be more brittle.

- [ ] **Run to confirm failure**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.utils.FirebaseMessagingUtilBuildNotificationTest"
```

Expected: COMPILATION FAILURE — `buildNotificationForTest` does not exist, the existing code still targets `DispatchActivity` directly.

### Step 2: Implement

- [ ] **Edit `buildNotification` in `FirebaseMessagingUtil.java`**

Replace the body of the existing `buildNotification` (line 549) so it wraps the incoming `intent`:

```java
private static NotificationCompat.Builder buildNotification(Context context, Intent intent, FCMMessageData fcmMessageData) {
    Bundle bundleExtras = new Bundle();
    intent.putExtra(NOTIFICATION_ID, fcmMessageData.getPayloadData().get(NOTIFICATION_ID));

    Intent launchIntent = new Intent(context,
            org.commcare.activities.PushNotificationLaunchActivity.class);
    launchIntent.putExtra(
            org.commcare.activities.PushNotificationLaunchActivity.EXTRA_WRAPPED_NAV_INTENT,
            intent);

    int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            : PendingIntent.FLAG_UPDATE_CURRENT;

    PendingIntent contentIntent = PendingIntent.getActivity(context, 0, launchIntent, flags);

    if (Strings.isEmptyOrWhitespace(fcmMessageData.getNotificationTitle()) && Strings.isEmptyOrWhitespace(fcmMessageData.getNotificationText())) {
        Logger.exception("Empty push notification",
                new Throwable(String.format("Empty notification for action '%s'", fcmMessageData.getAction())));
    }

    NotificationCompat.Builder fcmNotification = new NotificationCompat.Builder(context,
            fcmMessageData.getNotificationChannel())
            .setContentTitle(fcmMessageData.getNotificationTitle())
            .setContentText(fcmMessageData.getNotificationText())
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.commcare_actionbar_logo)
            .setPriority(fcmMessageData.getPriority())
            .setWhen(System.currentTimeMillis())
            .setExtras(bundleExtras);

    if (fcmMessageData.getLargeIcon() != null) {
        fcmNotification.setLargeIcon(fcmMessageData.getLargeIcon());
    }
    return fcmNotification;
}
```

Notes on what changed compared to the original (line 549):

- `intent.addFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK)` has been **removed**. The wrapped intent's task-clear behavior is now applied inside `PushNotificationLaunchActivity.dispatchWithoutForm` when the form is not active. Applying it here would defeat the form-active interception path.
- `PendingIntent.getActivity` now targets `launchIntent` (and therefore `PushNotificationLaunchActivity`) instead of the caller-supplied `intent`. The caller-supplied `intent` is preserved as an extra on `launchIntent`.

- [ ] **Expose the test seam**

In the same file, immediately below `buildNotification`, add:

```java
@androidx.annotation.VisibleForTesting
static NotificationCompat.Builder buildNotificationForTest(Context context, Intent intent, FCMMessageData fcmMessageData) {
    return buildNotification(context, intent, fcmMessageData);
}
```

### Step 3: Run the test

- [ ] **Verify pass**

```
./gradlew :app:testCommcareDebugUnitTest --tests "org.commcare.utils.FirebaseMessagingUtilBuildNotificationTest"
```

Expected: BUILD SUCCESSFUL, 1 test passing.

### Step 4: Run full unit suite

- [ ] **Run the whole unit-test target to catch any regressions**

```
./gradlew :app:testCommcareDebugUnitTest
```

Expected: BUILD SUCCESSFUL. Existing `SessionEndpointNotificationTest` and other push-notification tests should still pass — they assert the wrapped target intent, which we preserve unchanged.

### Step 5: Commit

- [ ] **Commit**

```
git add app/src/org/commcare/utils/FirebaseMessagingUtil.java
git add app/unit-tests/src/org/commcare/utils/FirebaseMessagingUtilBuildNotificationTest.kt
git commit -m "Route notification PendingIntent through PushNotificationLaunchActivity [AI]"
```

---

## Task 7: Manual / instrumentation verification

This task does not add new code. It verifies the end-to-end behavior on a device or emulator, since the dialog-actually-appearing path and the loaded/read-only/save short-circuits depend on a real `FormController` initialization that is impractical to fake in unit tests.

- [ ] **Build the debug APK**

```
./gradlew :app:assembleCommcareDebug
```

Expected: BUILD SUCCESSFUL. APK in `app/build/outputs/apk/`.

- [ ] **Install and verify scenario 1: notification tap during editable form**

1. Install the APK on a device with a logged-in CommCare account.
2. Open an application and enter any form so `FormEntryActivity` is foreground.
3. Trigger a navigation-style FCM push using the dev tooling (see `notifications_readme.md`) — e.g. a `ccc_message` or `ccc_dest_payments` payload.
4. Tap the notification.
5. Expected: the quit-form dialog appears with title "Quit form?". Three choices.
6. Tap "Don't save" → form is dismissed without saving, the notification's target screen opens.
7. Repeat steps 2–4, tap "Keep changes" → form is saved as incomplete (verify it appears in the Saved Forms list), then the notification target opens.
8. Repeat steps 2–4, tap "Stay" → dialog dismisses, user remains in the form, no navigation occurs.

- [ ] **Verify scenario 2: notification tap with no form open**

1. From the home screen, tap a fresh navigation notification.
2. Expected: the notification's target screen opens directly, no dialog, same behavior as before this feature.

- [ ] **Verify scenario 3: read-only form**

1. Open a previously completed form in review mode.
2. Tap a navigation notification.
3. Expected: no dialog. The form closes (`finishReturnInstance(false)`) and the notification target opens.

- [ ] **Verify scenario 4: process death mid-dialog (smoke check)**

1. Enable "Don't keep activities" in developer options.
2. Open a form, tap a navigation notification so the dialog appears.
3. Background and resume the app.
4. Expected: the dialog re-appears (because `mPendingNavAfterSave` was persisted and restored). Actions still work.

- [ ] **Verify analytics**

If the dev environment has Firebase debug logging enabled, confirm a `form_exit_attempt` event is logged with `method=push_notification_tap` on the tap, sitting alongside the existing `back_button_press` and `nav_button_press` values.

- [ ] **Commit the verification log (optional)**

If desired, add a short note to `docs/superpowers/specs/2026-05-11-form-exit-warning-on-push-design.md` documenting verification date and device/Android version.

---

## Task 8: Lint and formatting cleanup

Per project CLAUDE.md, formatting cleanup is a separate commit after main code changes.

- [ ] **Run ktlint on new Kotlin files**

```
ktlint --format app/src/org/commcare/activities/PushNotificationLaunchActivity.kt
ktlint --format app/unit-tests/src/org/commcare/activities/PushNotificationLaunchActivityTest.kt
ktlint --format app/unit-tests/src/org/commcare/activities/FormEntryActivityExternalNavTest.kt
ktlint --format app/unit-tests/src/org/commcare/activities/components/FormEntryDialogsExternalNavTest.kt
ktlint --format app/unit-tests/src/org/commcare/utils/FirebaseMessagingUtilBuildNotificationTest.kt
```

- [ ] **Verify ktlint compliance**

```
ktlint app/src/org/commcare/activities/PushNotificationLaunchActivity.kt
ktlint "app/unit-tests/src/org/commcare/**/*.kt"
```

Expected: no violations reported.

- [ ] **Verify Java checkstyle**

```
./gradlew :app:checkstyle
```

(If a `checkstyle` task is not configured at the Gradle level, the project's `checkstyle.xml` is used by the IDE/CI; inspect changed Java files for style compliance manually.)

- [ ] **Commit any formatting changes**

```
git add -A app/src app/unit-tests
git commit -m "Apply ktlint formatting to push-notification exit dialog files [AI]"
```

(Skip the commit if no diff after formatting.)

---

## Done criteria

- All five unit-test classes pass via `./gradlew :app:testCommcareDebugUnitTest`.
- `./gradlew :app:assembleCommcareDebug` produces a debug APK.
- All four manual scenarios in Task 7 behave as documented.
- ktlint and Java checkstyle clean on changed files.
- All commits tagged `[AI]` per project convention.

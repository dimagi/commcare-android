package org.commcare.activities

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.util.ActivityLaunchUtils
import org.commcare.android.util.TestAppInstaller
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Navigation must be a no-op while [FormEntryActivity.mFormController] is null, which is the
 * state `loadForm()` leaves it in until the form loader delivers its result.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class FormEntryNavigationBeforeLoadTest {
    private lateinit var activity: FormEntryActivity

    /**
     * Opens a form, then nulls the controller to recreate the state `loadForm()` leaves it in
     * until the form loader delivers its result.
     */
    @Before
    fun setup() {
        TestAppInstaller.installAppAndLogin(
            "jr://resource/commcare-apps/form_nav_tests/profile.ccpr",
            "test",
            "123",
        )
        activity = ActivityLaunchUtils.launchFormEntry("m0-f0")
        FormEntryActivity.mFormController = null
    }

    /**
     * Leaves no half-initialised controller behind for other tests sharing the static field.
     */
    @After
    fun tearDown() {
        FormEntryActivity.mFormController = null
    }

    /**
     * The Next button path from the crash report must not dereference the missing controller.
     */
    @Test
    fun nextButtonIsIgnoredBeforeFormLoads() {
        val questionsView = activity.odkView

        activity.findViewById<View>(R.id.nav_btn_next).performClick()

        assertSame(questionsView, activity.odkView)
        assertNull(FormEntryActivity.mFormController)
    }

    /**
     * Backward navigation (Previous button, back swipe) must not dereference the missing controller.
     */
    @Test
    fun previousNavigationIsIgnoredBeforeFormLoads() {
        val questionsView = activity.odkView

        (activity.uiController as FormEntryActivityUIController).showPreviousView(true)

        assertSame(questionsView, activity.odkView)
        assertNull(FormEntryActivity.mFormController)
    }
}

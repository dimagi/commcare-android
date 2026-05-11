package org.commcare.activities

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun whenWrappedIntentMissing_finishesWithoutStartingAnyActivity() {
        val ctx = CommCareTestApplication.instance()
        val launchIntent = Intent(ctx, PushNotificationLaunchActivity::class.java)
            // Deliberately no EXTRA_WRAPPED_NAV_INTENT.

        val controller = Robolectric.buildActivity(
            PushNotificationLaunchActivity::class.java, launchIntent
        ).create()
        val activity = controller.get()

        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }
}

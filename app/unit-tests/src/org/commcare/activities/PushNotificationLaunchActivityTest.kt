package org.commcare.activities

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun whenNoFormInProgress_startsWrappedIntentWithClearTaskNewTaskFlags() {
        FormEntryActivity.setFormEntryInProgressForTest(false)
        val ctx = CommCareTestApplication.instance()
        val wrapped = Intent(ctx, DispatchActivity::class.java).putExtra("payload", "p1")

        val launchIntent =
            Intent(ctx, PushNotificationLaunchActivity::class.java)
                .putExtra(PushNotificationLaunchActivity.EXTRA_WRAPPED_NAV_INTENT, wrapped)

        val controller =
            Robolectric
                .buildActivity(
                    PushNotificationLaunchActivity::class.java,
                    launchIntent,
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

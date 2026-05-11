package org.commcare.activities

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.activities.components.FormEntryDialogs
import org.commcare.activities.components.FormEntryInstanceState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val pending =
            Intent(ctx, DispatchActivity::class.java)
                .putExtra("marker", "m1")

        val controller = Robolectric.buildActivity(FormEntryActivity::class.java).create()
        val activity = controller.get()

        // Inject a real FormEntryInstanceState so onSaveInstanceState does not NPE.
        // FormEntryActivity.onCreateSessionSafe may not run in this lightweight Robolectric
        // setup (no active CommCare session), so instanceState can be null at this point.
        val field = FormEntryActivity::class.java.getDeclaredField("instanceState")
        field.isAccessible = true
        if (field.get(activity) == null) {
            field.set(activity, FormEntryInstanceState(null))
        }

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

        val mock: MockedStatic<FormEntryDialogs> =
            Mockito.mockStatic(FormEntryDialogs::class.java)
        try {
            // Use Robolectric's newIntent to trigger the lifecycle-safe onNewIntent call
            controller.newIntent(Intent(ctx, FormEntryActivity::class.java))
            mock.verifyNoInteractions()
        } finally {
            mock.close()
        }
    }
}

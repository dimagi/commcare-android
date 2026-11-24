package org.commcare.views.connect

import android.content.Context
import android.text.format.DateUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.viewmodel.AppSyncStatus
import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class OpportunitySyncCardStateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = 10 * DateUtils.DAY_IN_MILLIS
    private val twoHoursAgo = now - 2 * DateUtils.HOUR_IN_MILLIS

    private fun lastSynced(time: Long) =
        context.getString(
            R.string.connect_last_synced,
            DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS),
        )

    private fun state(
        app: AppSyncStatus?,
        opportunitySynced: Boolean = true,
    ) = opportunitySyncCardState(context, "Opportunity status", opportunitySynced, app, now)

    @Test
    fun `without an app session the card shows the opportunity's status`() {
        val state = state(app = null, opportunitySynced = false)

        assertEquals(context.getString(R.string.connect_sync_card_press_to_sync), state.statusText)
        assertEquals("Opportunity status", state.statusSubtext)
        assertTrue(state.warning)
    }

    @Test
    fun `an up to date app shows its last sync without warning`() {
        val state = state(AppSyncStatus(twoHoursAgo, unsentForms = 0, syncNeeded = false))

        assertEquals(lastSynced(twoHoursAgo), state.statusSubtext.toString())
        assertFalse(state.warning)
    }

    @Test
    fun `pending forms are counted and warn`() {
        val state = state(AppSyncStatus(twoHoursAgo, unsentForms = 3, syncNeeded = false))

        assertEquals(
            lastSynced(twoHoursAgo) + "\n" + context.getString(R.string.connect_sync_card_forms_pending, 3),
            state.statusSubtext.toString(),
        )
        assertTrue(state.warning)
    }

    @Test
    fun `an overdue sync warns with no forms pending`() {
        assertTrue(state(AppSyncStatus(twoHoursAgo, unsentForms = 0, syncNeeded = true)).warning)
    }

    @Test
    fun `a failed opportunity refresh still warns`() {
        val app = AppSyncStatus(twoHoursAgo, unsentForms = 0, syncNeeded = false)

        assertTrue(state(app, opportunitySynced = false).warning)
    }

    @Test
    fun `zero pending forms are listed when the app asks for it`() {
        val state = state(AppSyncStatus(twoHoursAgo, 0, syncNeeded = false, showZeroUnsentForms = true))

        assertTrue(state.statusSubtext.toString().endsWith(context.getString(R.string.connect_sync_card_forms_pending, 0)))
        assertFalse(state.warning)
    }

    @Test
    fun `an app that never synced says never`() {
        val state = state(AppSyncStatus(0L, unsentForms = 0, syncNeeded = true))

        assertEquals(
            context.getString(R.string.connect_last_synced, context.getString(R.string.connect_never)),
            state.statusSubtext.toString(),
        )
    }
}

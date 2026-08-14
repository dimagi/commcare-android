package org.commcare.activities

import android.view.View
import android.widget.TextView
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.util.ConnectTestUtils.connectJob
import org.commcare.android.util.ConnectTestUtils.daysFromNow
import org.commcare.android.util.ConnectTestUtils.seatJob
import org.commcare.dalvik.R
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization pins for the Connect job tile on the home screen: the connect button's
 * visibility, the job card, and the job message card.
 *
 * Non-Connect home button visibility lives in [StandardHomePresentationTest].
 */
class HomeConnectTileTest : BaseHomeScreenActivityTest() {
    // ---- Connect button ----

    @Test
    fun `connect button hidden when no job is seated`() {
        val home = buildVisibleHome()

        assertFalse(homeButtonLabels(home).contains(connectLabel))
    }

    @Test
    fun `connect button hidden while learning for a job already in delivery`() {
        // shouldShowJobStatus() suppresses the button only in this combination: the seated app is
        // the job's learn app, but the job has already moved on to delivery.
        seatJob(connectJob(status = ConnectJobRecord.STATUS_DELIVERING), isLearning = true)
        val home = buildVisibleHome()

        assertFalse(homeButtonLabels(home).contains(connectLabel))
    }

    @Test
    fun `connect button shown when a delivery job is seated`() {
        seatJob(connectJob(status = ConnectJobRecord.STATUS_DELIVERING))
        val home = buildVisibleHome()

        assertTrue(homeButtonLabels(home).contains(connectLabel))
    }

    // ---- Tile visibility ----

    @Test
    fun `job card hidden when no job is seated`() {
        val home = buildHome()

        assertEquals(View.GONE, home.findViewById<View>(R.id.viewJobCard).visibility)
    }

    @Test
    fun `job card visible and titled when a job is seated`() {
        seatJob(connectJob(title = "Field Survey", shortDescription = "Collect visits"))
        val home = buildHome()

        val card = home.findViewById<View>(R.id.viewJobCard)
        assertEquals(View.VISIBLE, card.visibility)
        assertEquals("Field Survey", card.findViewById<TextView>(R.id.tv_job_title).text.toString())
        assertEquals("Collect visits", card.findViewById<TextView>(R.id.tv_job_description).text.toString())
    }

    // ---- Message card ----

    @Test
    fun `message card hidden when the job has nothing to warn about`() {
        seatJob(connectJob())
        val home = buildHome()

        assertEquals(View.GONE, home.findViewById<View>(R.id.cvConnectMessage).visibility)
        assertEquals(View.GONE, home.findViewById<View>(R.id.ivConnectMessageWarningIcon).visibility)
    }

    @Test
    fun `ended job shows its message with a warning icon`() {
        // A past end date leaves no days remaining, which is what makes the job both finished
        // (so getCardMessageText returns the "ended" warning) and delivery-complete.
        seatJob(connectJob(endDate = daysFromNow(-1)))
        val home = buildHome()

        val card = home.findViewById<View>(R.id.cvConnectMessage)
        assertEquals(View.VISIBLE, card.visibility)
        assertEquals(View.VISIBLE, home.findViewById<View>(R.id.ivConnectMessageWarningIcon).visibility)
        assertEquals(
            home.getString(R.string.connect_progress_warning_ended),
            card.findViewById<TextView>(R.id.tvConnectMessage).text.toString(),
        )
    }

    private val connectLabel get() = Localization.get("home.connect")
}

package org.commcare.activities

import android.view.View
import android.widget.TextView
import io.mockk.every
import org.commcare.connect.ConnectJobHelper
import org.commcare.dalvik.R
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
class HomeConnectTileTest : HomeConnectTestBase() {
    // ---- getHiddenButtons ----

    @Test
    fun `connect button hidden when job status should not show`() {
        // shouldShowJobStatus defaults to false in the base builder.
        val home = buildHome()

        assertTrue(uiController(home).getHiddenButtons().contains("connect"))
    }

    @Test
    fun `connect button shown when job status should show`() {
        every { ConnectJobHelper.shouldShowJobStatus(any(), any()) } returns true
        val home = buildHome()

        assertFalse(uiController(home).getHiddenButtons().contains("connect"))
    }

    // ---- Tile visibility ----

    @Test
    fun `job card hidden when no job is seated`() {
        val home = buildHome() // base default: getJobForSeatedApp returns null
        assertEquals(View.GONE, home.findViewById<View>(R.id.viewJobCard).visibility)
    }

    @Test
    fun `job card visible and titled when a job is seated`() {
        seatJobForTileRender(connectJob(title = "Field Survey", shortDescription = "Collect visits"))
        val home = buildHome()

        val card = home.findViewById<View>(R.id.viewJobCard)
        assertEquals(View.VISIBLE, card.visibility)
        assertEquals("Field Survey", card.findViewById<TextView>(R.id.tv_job_title).text.toString())
        assertEquals("Collect visits", card.findViewById<TextView>(R.id.tv_job_description).text.toString())
    }

    // ---- Message card ----

    @Test
    fun `message card hidden when job has no card message`() {
        val job = connectJob()
        every { job.getCardMessageText(any()) } returns null
        seatJobForTileRender(job)
        val home = buildHome()

        uiController(home).updateConnectJobMessage()

        assertEquals(View.GONE, home.findViewById<View>(R.id.cvConnectMessage).visibility)
        assertEquals(View.GONE, home.findViewById<View>(R.id.ivConnectMessageWarningIcon).visibility)
    }

    @Test
    fun `delivery-complete message shows warning icon`() {
        val job = connectJob(deliveryComplete = true)
        every { job.getCardMessageText(any()) } returns "All deliveries complete"
        seatJobForTileRender(job)
        val home = buildHome()

        uiController(home).updateConnectJobMessage()

        assertEquals(View.VISIBLE, home.findViewById<View>(R.id.cvConnectMessage).visibility)
        assertEquals(View.VISIBLE, home.findViewById<View>(R.id.ivConnectMessageWarningIcon).visibility)
    }
}

package org.commcare.activities

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.util.ConnectTestUtils.connectJob
import org.commcare.android.util.ConnectTestUtils.seatJob
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ConnectJobHelper
import org.commcare.utils.ConnectivityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterization pins for the home screen's Connect job-progress paths: opening a job's status
 * screen, and when `fetchJobProgressOverNetwork()` actually hits the network — both when called
 * directly and when reached through the sync button.
 *
 * The sync button's traditional behaviour — the toast and notification raised when a sync can't be
 * attempted — lives in [HomeButtonsTest].
 */
class HomeConnectJobProgressTest : BaseHomeScreenActivityTest() {
    @Before
    fun stubProgressFetch() {
        // updateDeliveryProgress() is the outbound network call this suite is asserting on, so it
        // is stubbed rather than run. mockkObject spies: every other ConnectJobHelper method,
        // including the job lookups home boots through, still runs for real against the DB.
        mockkObject(ConnectJobHelper)
        every { ConnectJobHelper.updateDeliveryProgress(any(), any(), any()) } returns Unit
    }

    // ---- Opportunity status ----

    @Test
    fun `view opportunity status navigates to job info when a job is seated`() {
        seatJob(connectJob())
        val home = buildHome()

        home.userPressedOpportunityStatus()

        val started = shadowOf(home).nextStartedActivity
        assertEquals(ConnectActivity::class.java.name, started.component!!.className)
        assertTrue(started.getBooleanExtra(ConnectConstants.GO_TO_JOB_STATUS, false))
        assertEquals("test-job-uuid", started.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
        assertTrue(started.getBooleanExtra(ConnectConstants.SHOW_LAUNCH_BUTTON, false))
    }

    @Test(expected = NullPointerException::class)
    fun `view opportunity status throws when no job is seated`() {
        val home = buildHome()

        home.userPressedOpportunityStatus()
    }

    // ---- fetchJobProgressOverNetwork ----

    @Test
    fun `fetch job progress over network updates progress for a delivering job`() {
        seatJob(connectJob(status = ConnectJobRecord.STATUS_DELIVERING))
        val home = buildHome()

        home.fetchJobProgressOverNetwork()

        val job = slot<ConnectJobRecord>()
        verify(exactly = 1) { ConnectJobHelper.updateDeliveryProgress(home, capture(job), any()) }
        assertEquals("test-job-uuid", job.captured.jobUUID)
    }

    @Test
    fun `fetch job progress over network does nothing for a non-delivering job`() {
        seatJob(connectJob(status = ConnectJobRecord.STATUS_LEARNING))
        val home = buildHome()

        home.fetchJobProgressOverNetwork()

        verify(exactly = 0) { ConnectJobHelper.updateDeliveryProgress(any(), any(), any()) }
    }

    // ---- Reached via the sync button ----

    @Test
    fun `sync with no network in airplane mode does not fetch job progress`() {
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns true
        val home = buildHome()

        home.syncButtonPressed()

        verify(exactly = 0) { ConnectJobHelper.updateDeliveryProgress(any(), any(), any()) }
    }

    @Test
    fun `sync with network available fetches job progress for delivering job`() {
        seatJob(connectJob(status = ConnectJobRecord.STATUS_DELIVERING))
        val home = buildHome()

        home.syncButtonPressed()

        val job = slot<ConnectJobRecord>()
        verify(exactly = 1) { ConnectJobHelper.updateDeliveryProgress(home, capture(job), any()) }
        assertEquals("test-job-uuid", job.captured.jobUUID)
    }
}

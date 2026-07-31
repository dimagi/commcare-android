package org.commcare.activities

import io.mockk.every
import io.mockk.verify
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.ConnectNavHelper
import org.commcare.utils.ConnectivityStatus
import org.junit.Test

/**
 * Characterization pins for the home screen's Connect job-progress paths: opening a job's status
 * screen, and when `fetchJobProgressOverNetwork()` actually hits the network — both when called
 * directly and when reached through the sync button.
 *
 * [HomeScreenActivityTest] pins [ConnectivityStatus] to "online, not in airplane mode"; the offline
 * rows below re-stub it per test.
 *
 * The sync button's traditional behaviour — the toast and notification raised when a sync can't be
 * attempted — lives in [HomeActionsTest].
 */
class HomeConnectJobProgressTest : HomeConnectTestBase() {
    // ---- Opportunity status ----

    @Test
    fun `view opportunity status navigates to job info when a job is seated`() {
        val job = connectJob()
        seatJob(job)
        val home = buildHome()

        home.userPressedOpportunityStatus()

        verify(exactly = 1) { ConnectNavHelper.goToActiveInfoForJob(home, job, true) }
    }

    @Test(expected = NullPointerException::class)
    fun `view opportunity status throws when no job is seated`() {
        val home = buildHome() // base default: no job
        home.userPressedOpportunityStatus()
    }

    // ---- fetchJobProgressOverNetwork ----

    @Test
    fun `fetch job progress over network updates progress for a delivering job`() {
        val job = connectJob(status = ConnectJobRecord.STATUS_DELIVERING)
        seatJob(job)
        val home = buildHome()
        // mockkObject(ConnectJobHelper) isn't relaxed here; the real body would run and NPE on
        // ConnectUserDatabaseUtil.getUser(context)!! since no Connect user is set up in this test.
        every { ConnectJobHelper.updateDeliveryProgress(any(), any(), any()) } returns Unit

        home.fetchJobProgressOverNetwork()

        verify(exactly = 1) { ConnectJobHelper.updateDeliveryProgress(home, job, any()) }
    }

    @Test
    fun `fetch job progress over network does nothing for a non-delivering job`() {
        val job = connectJob(status = ConnectJobRecord.STATUS_LEARNING)
        seatJob(job)
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
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns true
        every { ConnectJobHelper.updateDeliveryProgress(any(), any(), any()) } returns Unit
        val job = connectJob(status = ConnectJobRecord.STATUS_DELIVERING)
        val home = buildHome()
        seatJob(job)

        home.syncButtonPressed()

        verify(exactly = 1) { ConnectJobHelper.updateDeliveryProgress(home, job, any()) }
    }
}

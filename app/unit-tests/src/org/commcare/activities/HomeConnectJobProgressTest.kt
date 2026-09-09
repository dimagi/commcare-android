package org.commcare.activities

import io.mockk.every
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.util.ActivityAssertions.assertStarted
import org.commcare.android.util.ConnectTestUtils.JOB_UUID
import org.commcare.android.util.ConnectTestUtils.MAX_VISITS
import org.commcare.android.util.ConnectTestUtils.connectJob
import org.commcare.android.util.ConnectTestUtils.seatJob
import org.commcare.connect.ConnectConstants
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.network.ConnectMockApiServer
import org.commcare.utils.ConnectivityStatus
import org.javarosa.core.services.locale.Localization
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterization pins for the home screen's Connect job-progress paths, driven by clicking the
 * buttons a user would: the Connect button opening a job's status screen, and the sync button's
 * delivery-progress fetch.
 *
 * The fetch runs against a [ConnectMockApiServer], so the request, the response parser and the DB
 * write it produces all run for real. The one thing stubbed is [ConnectivityStatus] (inherited from
 * the base), which reports on the device's radios.
 *
 * The sync button's traditional behaviour — the toast and notification raised when a sync can't be
 * attempted — lives in [HomeButtonsTest].
 */
class HomeConnectJobProgressTest : BaseHomeScreenActivityTest() {
    private val connectApi = ConnectMockApiServer()

    @Before
    fun startConnectApi() = connectApi.start()

    @After
    fun stopConnectApi() = connectApi.shutdown()

    // ---- Opportunity status ----

    @Test
    fun `clicking the connect button opens the job status screen`() {
        seatJob(connectJob())
        val home = buildVisibleHome()

        clickHomeButton(home, Localization.get("home.connect"))

        val started = assertStarted(home, ConnectActivity::class.java)
        assertTrue(started.getBooleanExtra(ConnectConstants.GO_TO_JOB_STATUS, false))
        assertEquals(JOB_UUID, started.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
        assertTrue(started.getBooleanExtra(ConnectConstants.SHOW_LAUNCH_BUTTON, false))
    }

    // ---- Delivery progress over the network ----

    @Test
    fun `sync applies the delivery progress the server returns for a delivering job`() {
        seatJob(connectJob(status = ConnectJobRecord.STATUS_DELIVERING))
        connectApi.enqueueJson("""{"max_payments": $UPDATED_MAX_PAYMENTS}""")
        val home = buildVisibleHome()

        clickSyncButton(home)

        assertEquals("/api/opportunity/$JOB_UUID/delivery_progress", connectApi.awaitRequest().path)
        assertEquals(
            "the parsed response should have been written back to the job",
            UPDATED_MAX_PAYMENTS,
            seatedJob().maxVisits,
        )
    }

    @Test
    fun `sync leaves the job untouched when the server rejects the progress request`() {
        seatJob(connectJob(status = ConnectJobRecord.STATUS_DELIVERING))
        connectApi.enqueueError(500)
        val home = buildVisibleHome()

        clickSyncButton(home)

        connectApi.awaitRequest()
        assertEquals(MAX_VISITS, seatedJob().maxVisits)
    }

    @Test
    fun `sync does not fetch progress for a non-delivering job`() {
        seatJob(connectJob(status = ConnectJobRecord.STATUS_LEARNING))
        val home = buildVisibleHome()

        clickSyncButton(home)

        connectApi.assertNoRequest()
    }

    @Test
    fun `sync does not fetch progress with no network`() {
        seatJob(connectJob(status = ConnectJobRecord.STATUS_DELIVERING))
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        val home = buildVisibleHome()

        clickSyncButton(home)

        connectApi.assertNoRequest()
    }

    private fun clickSyncButton(home: StandardHomeActivity) = clickHomeButton(home, Localization.get("home.sync"))

    private fun seatedJob(): ConnectJobRecord =
        requireNotNull(ConnectJobUtils.getCompositeJob(JOB_UUID)) {
            "the seated job disappeared from the Connect DB"
        }

    companion object {
        /** Distinct from `ConnectTestUtils.MAX_VISITS`, so applying the response is observable. */
        private const val UPDATED_MAX_PAYMENTS = 42
    }
}

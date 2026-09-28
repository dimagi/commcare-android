package org.commcare.connect.database

import io.mockk.every
import io.mockk.mockk
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectConstants
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins what a generic opportunity link resolves to.
 *
 * The phase no longer selects a destination - Opportunity Home resolves the summary, learn and
 * delivery surfaces from that same phase once it is open - so the only question left here is
 * whether the link named a payment, which is what opens the payments tab rather than the dashboard.
 *
 * The routing these values drive is covered from the other end in
 * [org.commcare.connect.SmsInviteLinkFlowTest].
 */
class ConnectJobUtilsDestinationTest {
    private fun jobWithStatus(status: Int): ConnectJobRecord = mockk<ConnectJobRecord>().also { every { it.status } returns status }

    private fun resolve(
        job: ConnectJobRecord?,
        paymentUuid: String? = null,
        action: String = ConnectConstants.CCC_GENERIC_OPPORTUNITY,
    ): String? = ConnectJobUtils.resolveGenericOpportunityDestination(action, job, paymentUuid)

    // ---- the one distinction that survives ----

    @Test
    fun `a delivering job with a payment opens the payments tab`() {
        assertEquals(
            ConnectConstants.CCC_DEST_PAYMENTS,
            resolve(jobWithStatus(ConnectJobRecord.STATUS_DELIVERING), paymentUuid = "payment-1"),
        )
    }

    @Test
    fun `a delivering job with no payment named opens the opportunity page`() {
        assertEquals(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            resolve(jobWithStatus(ConnectJobRecord.STATUS_DELIVERING)),
        )
    }

    @Test
    fun `an empty payment uuid counts as no payment`() {
        assertEquals(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            resolve(jobWithStatus(ConnectJobRecord.STATUS_DELIVERING), paymentUuid = ""),
        )
    }

    @Test
    fun `a payment named on a job that is not delivering is ignored`() {
        assertEquals(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            resolve(jobWithStatus(ConnectJobRecord.STATUS_LEARNING), paymentUuid = "payment-1"),
        )
    }

    // ---- phase no longer picks a destination ----

    @Test
    fun `every active phase resolves to the same opportunity page`() {
        listOf(
            ConnectJobRecord.STATUS_LEARNING,
            ConnectJobRecord.STATUS_AVAILABLE,
            ConnectJobRecord.STATUS_AVAILABLE_NEW,
            ConnectJobRecord.STATUS_DELIVERING,
        ).forEach { status ->
            assertEquals(
                "status $status should not change the destination",
                ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
                resolve(jobWithStatus(status)),
            )
        }
    }

    // ---- everything else is left alone ----

    @Test
    fun `an unrecognised status is left unresolved so the caller falls back to the jobs list`() {
        assertEquals(
            ConnectConstants.CCC_GENERIC_OPPORTUNITY,
            resolve(jobWithStatus(ConnectJobRecord.STATUS_ALL_JOBS)),
        )
    }

    @Test
    fun `an unknown job is left unresolved`() {
        assertEquals(ConnectConstants.CCC_GENERIC_OPPORTUNITY, resolve(job = null))
    }

    @Test
    fun `an action that already names a destination is passed through untouched`() {
        assertEquals(
            ConnectConstants.CCC_DEST_LEARN_PROGRESS,
            resolve(
                jobWithStatus(ConnectJobRecord.STATUS_DELIVERING),
                paymentUuid = "payment-1",
                action = ConnectConstants.CCC_DEST_LEARN_PROGRESS,
            ),
        )
    }
}

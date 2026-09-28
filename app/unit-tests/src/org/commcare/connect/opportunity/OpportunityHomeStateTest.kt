package org.commcare.connect.opportunity

import io.mockk.every
import io.mockk.mockk
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class OpportunityHomeStateTest {
    private fun jobWithStatus(jobStatus: Int): ConnectJobRecord = mockk { every { status } returns jobStatus }

    @Test
    fun `both available statuses are the available phase`() {
        for (status in listOf(ConnectJobRecord.STATUS_AVAILABLE_NEW, ConnectJobRecord.STATUS_AVAILABLE)) {
            assertEquals(OpportunityPhase.AVAILABLE, OpportunityPhase.of(jobWithStatus(status)))
        }
    }

    @Test
    fun `learning and delivering map to their own phases`() {
        assertEquals(OpportunityPhase.LEARNING, OpportunityPhase.of(jobWithStatus(ConnectJobRecord.STATUS_LEARNING)))
        assertEquals(OpportunityPhase.DELIVERY, OpportunityPhase.of(jobWithStatus(ConnectJobRecord.STATUS_DELIVERING)))
    }

    @Test
    fun `an unrecognised status falls back to the available phase`() {
        assertEquals(OpportunityPhase.AVAILABLE, OpportunityPhase.of(jobWithStatus(ConnectJobRecord.STATUS_ALL_JOBS)))
    }

    @Test
    fun `each phase resolves to its surface`() {
        assertEquals(OpportunityHomeState.JOB_INTRO, OpportunityHomeState.resolve(OpportunityPhase.AVAILABLE))
        assertEquals(OpportunityHomeState.LEARNING, OpportunityHomeState.resolve(OpportunityPhase.LEARNING))
        assertEquals(OpportunityHomeState.DELIVERY, OpportunityHomeState.resolve(OpportunityPhase.DELIVERY))
    }
}

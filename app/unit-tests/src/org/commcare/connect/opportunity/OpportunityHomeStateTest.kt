package org.commcare.connect.opportunity

import org.commcare.android.database.connect.models.ConnectJobRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class OpportunityHomeStateTest {
    @Test
    fun `both available statuses resolve to the job intro`() {
        for (status in listOf(ConnectJobRecord.STATUS_AVAILABLE_NEW, ConnectJobRecord.STATUS_AVAILABLE)) {
            assertEquals(OpportunityHomeState.JOB_INTRO, OpportunityHomeState.resolve(status))
        }
    }

    @Test
    fun `learning and delivering resolve to their own surfaces`() {
        assertEquals(OpportunityHomeState.LEARNING, OpportunityHomeState.resolve(ConnectJobRecord.STATUS_LEARNING))
        assertEquals(OpportunityHomeState.DELIVERY, OpportunityHomeState.resolve(ConnectJobRecord.STATUS_DELIVERING))
    }

    @Test
    fun `an unrecognised status falls back to the job intro`() {
        assertEquals(OpportunityHomeState.JOB_INTRO, OpportunityHomeState.resolve(ConnectJobRecord.STATUS_ALL_JOBS))
    }
}

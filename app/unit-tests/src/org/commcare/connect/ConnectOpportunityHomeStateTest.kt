package org.commcare.connect

import org.commcare.android.database.connect.models.ConnectJobRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectOpportunityHomeStateTest {
    @Test
    fun `both available statuses resolve to the job intro`() {
        for (status in listOf(ConnectJobRecord.STATUS_AVAILABLE_NEW, ConnectJobRecord.STATUS_AVAILABLE)) {
            assertEquals(ConnectOpportunityHomeState.JOB_INTRO, ConnectOpportunityHomeState.resolve(status))
        }
    }

    @Test
    fun `learning and delivering resolve to their own surfaces`() {
        assertEquals(ConnectOpportunityHomeState.LEARNING, ConnectOpportunityHomeState.resolve(ConnectJobRecord.STATUS_LEARNING))
        assertEquals(ConnectOpportunityHomeState.DELIVERY, ConnectOpportunityHomeState.resolve(ConnectJobRecord.STATUS_DELIVERING))
    }

    @Test
    fun `an unrecognised status falls back to the job intro`() {
        assertEquals(ConnectOpportunityHomeState.JOB_INTRO, ConnectOpportunityHomeState.resolve(ConnectJobRecord.STATUS_ALL_JOBS))
    }
}

package org.commcare.connect

import org.commcare.android.database.connect.models.ConnectJobRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectOpportunityHomeStateControllerTest {
    private class FakeHost : ConnectOpportunityHomeHost {
        var status: Int = ConnectJobRecord.STATUS_LEARNING
        var installing: Boolean = false

        override fun jobStatus(): Int = status

        override fun shouldBlockPhaseTransition(): Boolean = installing
    }

    @Test
    fun `the first resolve reports a change`() {
        val controller = ConnectOpportunityHomeStateController(FakeHost())

        assertTrue(controller.reResolveState())
        assertEquals(ConnectOpportunityHomeState.LEARNING, controller.currentState)
    }

    @Test
    fun `re-resolving to the same state reports no change`() {
        val controller = ConnectOpportunityHomeStateController(FakeHost())
        controller.reResolveState()

        assertFalse(controller.reResolveState())
    }

    @Test
    fun `a status change resolves to the new state`() {
        val host = FakeHost()
        val controller = ConnectOpportunityHomeStateController(host)
        controller.reResolveState()

        host.status = ConnectJobRecord.STATUS_DELIVERING

        assertTrue(controller.reResolveState())
        assertEquals(ConnectOpportunityHomeState.DELIVERY, controller.currentState)
    }

    @Test
    fun `a running install defers the re-resolve`() {
        val host = FakeHost()
        val controller = ConnectOpportunityHomeStateController(host)
        controller.reResolveState()

        host.installing = true
        host.status = ConnectJobRecord.STATUS_DELIVERING
        assertFalse(controller.reResolveState())
        assertEquals(ConnectOpportunityHomeState.LEARNING, controller.currentState)

        host.installing = false
        assertTrue(controller.reResolveState())
        assertEquals(ConnectOpportunityHomeState.DELIVERY, controller.currentState)
    }
}

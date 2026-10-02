package org.commcare.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CommCareAttemptCounterTest {
    @Test
    fun `new tracker starts at zero`() {
        val tracker = CommCareAttemptCounter()
        assertEquals(0, tracker.requestCount)
        assertEquals(0, tracker.failedAttempts)
    }

    @Test
    fun `recordRequest increments only the request count`() {
        val tracker = CommCareAttemptCounter()
        tracker.recordRequest()
        tracker.recordRequest()
        assertEquals(2, tracker.requestCount)
        assertEquals(0, tracker.failedAttempts)
    }

    @Test
    fun `recordFailedAttempt increments only the failed count`() {
        val tracker = CommCareAttemptCounter()
        tracker.recordFailedAttempt()
        assertEquals(0, tracker.requestCount)
        assertEquals(1, tracker.failedAttempts)
    }

    @Test
    fun `tracker seeds from initial values`() {
        val tracker = CommCareAttemptCounter(initialRequestCount = 3, initialFailedAttempts = 2)
        assertEquals(3, tracker.requestCount)
        assertEquals(2, tracker.failedAttempts)
        tracker.recordRequest()
        tracker.recordFailedAttempt()
        assertEquals(4, tracker.requestCount)
        assertEquals(3, tracker.failedAttempts)
    }

    @Test
    fun `reset returns both counts to zero`() {
        val tracker = CommCareAttemptCounter(initialRequestCount = 5, initialFailedAttempts = 4)
        tracker.reset()
        assertEquals(0, tracker.requestCount)
        assertEquals(0, tracker.failedAttempts)
    }
}

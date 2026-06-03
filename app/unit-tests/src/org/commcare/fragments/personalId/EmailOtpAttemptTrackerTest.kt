package org.commcare.fragments.personalId

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailOtpAttemptTrackerTest {
    @Test
    fun `new tracker starts at zero`() {
        val tracker = EmailOtpAttemptTracker()
        assertEquals(0, tracker.requestCount)
        assertEquals(0, tracker.failedAttempts)
    }

    @Test
    fun `recordRequest increments only the request count`() {
        val tracker = EmailOtpAttemptTracker()
        tracker.recordRequest()
        tracker.recordRequest()
        assertEquals(2, tracker.requestCount)
        assertEquals(0, tracker.failedAttempts)
    }

    @Test
    fun `recordFailedVerification increments only the failed count`() {
        val tracker = EmailOtpAttemptTracker()
        tracker.recordFailedVerification()
        assertEquals(0, tracker.requestCount)
        assertEquals(1, tracker.failedAttempts)
    }

    @Test
    fun `tracker seeds from initial values`() {
        val tracker = EmailOtpAttemptTracker(initialRequestCount = 3, initialFailedAttempts = 2)
        assertEquals(3, tracker.requestCount)
        assertEquals(2, tracker.failedAttempts)
        tracker.recordRequest()
        tracker.recordFailedVerification()
        assertEquals(4, tracker.requestCount)
        assertEquals(3, tracker.failedAttempts)
    }

    @Test
    fun `reset returns both counts to zero`() {
        val tracker = EmailOtpAttemptTracker(initialRequestCount = 5, initialFailedAttempts = 4)
        tracker.reset()
        assertEquals(0, tracker.requestCount)
        assertEquals(0, tracker.failedAttempts)
    }
}

package org.commcare.login

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.commcare.CommCareApplication
import org.commcare.android.database.global.models.ApplicationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val STATE_READY = 2

class AppSeaterTest {
    private val sink = LoginProgressListener { }

    @Test
    fun `missing record returns Failed APP_NOT_FOUND without seating`() =
        runTest {
            var seatCalled = false
            val seater =
                AppSeater(
                    recordLookup = { null },
                    seatApp = {
                        seatCalled = true
                        STATE_READY
                    },
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val result = seater.seatIfNeeded("missing-app", sink)

            assertEquals(SeatResult.Failed(SeatFailure.APP_NOT_FOUND), result)
            assertFalse(seatCalled)
        }

    @Test
    fun `ready resource state returns Success`() =
        runTest {
            val record = mockk<ApplicationRecord>(relaxed = true)
            val seater =
                AppSeater(
                    recordLookup = { record },
                    seatApp = { STATE_READY },
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val result = seater.seatIfNeeded("app-1", sink)

            assertEquals(SeatResult.Success, result)
        }

    @Test
    fun `corrupted resource state returns Failed CORRUPTED`() =
        runTest {
            val record = mockk<ApplicationRecord>(relaxed = true)
            val seater =
                AppSeater(
                    recordLookup = { record },
                    seatApp = { CommCareApplication.STATE_CORRUPTED },
                    ioDispatcher = Dispatchers.Unconfined,
                )

            val result = seater.seatIfNeeded("app-1", sink)

            assertEquals(SeatResult.Failed(SeatFailure.CORRUPTED), result)
        }

    @Test(expected = IllegalStateException::class)
    fun `seatApp throwing propagates instead of being swallowed`() =
        runTest {
            val record = mockk<ApplicationRecord>(relaxed = true)
            val seater =
                AppSeater(
                    recordLookup = { record },
                    seatApp = { throw IllegalStateException("seat blew up") },
                    ioDispatcher = Dispatchers.Unconfined,
                )

            seater.seatIfNeeded("app-1", sink)
        }

    @Test(expected = RuntimeException::class)
    fun `recordLookup throwing propagates instead of being swallowed`() =
        runTest {
            val seater =
                AppSeater(
                    recordLookup = { throw RuntimeException("db unavailable") },
                    seatApp = { STATE_READY },
                    ioDispatcher = Dispatchers.Unconfined,
                )

            seater.seatIfNeeded("app-1", sink)
        }

    @Test
    fun `emits Seating progress`() =
        runTest {
            val record = mockk<ApplicationRecord>(relaxed = true)
            val phases = mutableListOf<LoginPhase>()
            val seater =
                AppSeater(
                    recordLookup = { record },
                    seatApp = { STATE_READY },
                    ioDispatcher = Dispatchers.Unconfined,
                )

            seater.seatIfNeeded("app-1") { phases += it.phase }

            assertTrue(phases.contains(LoginPhase.Seating))
        }
}

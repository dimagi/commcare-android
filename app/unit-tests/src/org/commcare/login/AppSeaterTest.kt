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
    private val sink = LoginProgressSink { }

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

            seater.seatIfNeeded("app-1", LoginProgressSink { phases += it.phase })

            assertTrue(phases.contains(LoginPhase.Seating))
        }
}

package org.commcare.android.database.connect.models

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Covers [ConnectJobRecord.isFurtherWorkBlocked], which decides whether the delivery dashboard
 * renders its progress as disabled.
 */
class ConnectJobRecordWorkBlockedTest {
    private val visitDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    private fun daysFromNow(days: Int): Date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.time

    private fun job(
        active: Boolean = true,
        suspended: Boolean = false,
        endDate: Date = daysFromNow(30),
        maxVisits: Int = 10,
        maxDailyVisits: Int = 5,
    ) = ConnectJobRecord().apply {
        setIsActive(active)
        setIsUserSuspended(suspended)
        projectEndDate = endDate
        setMaxVisits(maxVisits)
        setMaxDailyVisits(maxDailyVisits)
        deliveries = emptyList()
        paymentUnits = emptyList()
    }

    private fun paymentUnit(
        id: Int,
        maxTotal: Int,
        maxDaily: Int,
    ): ConnectPaymentUnitRecord =
        ConnectPaymentUnitRecord.fromJson(
            JSONObject(
                """
                {
                    "id": $id,
                    "payment_unit_id": "unit-$id",
                    "name": "Unit $id",
                    "max_total": $maxTotal,
                    "max_daily": $maxDaily,
                    "amount": 100
                }
                """.trimIndent(),
            ),
            ConnectJobRecord(),
        )

    private fun delivery(
        id: Int,
        unitId: Int,
        date: Date,
    ): ConnectJobDeliveryRecord =
        ConnectJobDeliveryRecord.fromJson(
            JSONObject(
                """
                {
                    "id": $id,
                    "visit_date": "${visitDateFormat.format(date)}",
                    "status": "approved",
                    "deliver_unit_name": "Unit $unitId",
                    "deliver_unit_slug": "unit-$unitId",
                    "entity_id": "entity-$id",
                    "entity_name": "Entity $id",
                    "reason": "",
                    "deliver_unit_slug_id": "unit-$unitId"
                }
                """.trimIndent(),
            ),
            ConnectJobRecord(),
        )

    @Test
    fun `a job with visits left in the day and overall is not blocked`() {
        val job = job(maxVisits = 10, maxDailyVisits = 5)
        job.deliveries = listOf(delivery(1, 1, Date()))

        assertFalse(job.isFurtherWorkBlocked)
    }

    @Test
    fun `an inactive job is blocked`() {
        val job = job(active = false)

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `a job past its end date is blocked`() {
        val job = job(endDate = daysFromNow(-1))

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `a suspended user is blocked even with visits remaining`() {
        val job = job(suspended = true, maxVisits = 10, maxDailyVisits = 5)

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `reaching the total visit maximum blocks work`() {
        val job = job(maxVisits = 2, maxDailyVisits = 5)
        job.deliveries =
            listOf(
                delivery(1, 1, daysFromNow(-2)),
                delivery(2, 1, daysFromNow(-1)),
            )

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `reaching the daily visit maximum blocks work`() {
        val job = job(maxVisits = 10, maxDailyVisits = 2)
        job.deliveries =
            listOf(
                delivery(1, 1, Date()),
                delivery(2, 1, Date()),
            )

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `visits made on earlier days do not count towards the daily maximum`() {
        val job = job(maxVisits = 10, maxDailyVisits = 2)
        job.deliveries =
            listOf(
                delivery(1, 1, daysFromNow(-1)),
                delivery(2, 1, daysFromNow(-2)),
            )

        assertFalse(job.isFurtherWorkBlocked)
    }

    @Test
    fun `a multi-payment job is not blocked while one unit still has visits left`() {
        val job = job(maxVisits = 10, maxDailyVisits = 10)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 2, maxDaily = 1),
                paymentUnit(id = 2, maxTotal = 2, maxDaily = 1),
            )
        job.deliveries =
            listOf(
                delivery(1, 1, daysFromNow(-1)),
                delivery(2, 1, daysFromNow(-2)),
            )

        assertFalse(job.isFurtherWorkBlocked)
    }

    @Test
    fun `a multi-payment job is blocked once every unit is at its total maximum`() {
        val job = job(maxVisits = 10, maxDailyVisits = 10)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 1, maxDaily = 5),
                paymentUnit(id = 2, maxTotal = 1, maxDaily = 5),
            )
        job.deliveries =
            listOf(
                delivery(1, 1, daysFromNow(-3)),
                delivery(2, 2, daysFromNow(-2)),
            )

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `a multi-payment job is blocked once every unit is at its daily maximum`() {
        val job = job(maxVisits = 100, maxDailyVisits = 100)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 50, maxDaily = 1),
                paymentUnit(id = 2, maxTotal = 50, maxDaily = 1),
            )
        job.deliveries =
            listOf(
                delivery(1, 1, Date()),
                delivery(2, 2, Date()),
            )

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `a multi-payment job is blocked when one unit is spent and the other is done for today`() {
        val job = job(maxVisits = 100, maxDailyVisits = 100)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 1, maxDaily = 5),
                paymentUnit(id = 2, maxTotal = 50, maxDaily = 1),
            )
        job.deliveries =
            listOf(
                delivery(1, 1, daysFromNow(-3)),
                delivery(2, 2, Date()),
            )

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `units at limit names only the unit that is out of visits for good`() {
        val job = job(maxVisits = 100, maxDailyVisits = 100)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 1, maxDaily = 5),
                paymentUnit(id = 2, maxTotal = 50, maxDaily = 5),
            )
        job.deliveries = listOf(delivery(1, 1, daysFromNow(-3)))

        assertEquals(setOf("unit-1"), job.paymentUnitsAtLimit)
    }

    @Test
    fun `units at limit names only the unit that is done for today`() {
        val job = job(maxVisits = 100, maxDailyVisits = 100)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 50, maxDaily = 5),
                paymentUnit(id = 2, maxTotal = 50, maxDaily = 1),
            )
        job.deliveries = listOf(delivery(1, 2, Date()))

        assertEquals(setOf("unit-2"), job.paymentUnitsAtLimit)
    }

    @Test
    fun `units at limit is empty while every unit has visits left`() {
        val job = job(maxVisits = 100, maxDailyVisits = 100)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 50, maxDaily = 5),
                paymentUnit(id = 2, maxTotal = 50, maxDaily = 5),
            )
        job.deliveries = listOf(delivery(1, 1, Date()))

        assertTrue(job.paymentUnitsAtLimit.isEmpty())
    }

    @Test
    fun `a multi-payment job is blocked by the job-level total even with units under their limits`() {
        val job = job(maxVisits = 2, maxDailyVisits = 100)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 30, maxDaily = 5),
                paymentUnit(id = 2, maxTotal = 30, maxDaily = 5),
            )
        job.deliveries =
            listOf(
                delivery(1, 1, daysFromNow(-2)),
                delivery(2, 2, daysFromNow(-1)),
            )

        assertTrue(job.isFurtherWorkBlocked)
    }

    @Test
    fun `a multi-payment job is blocked by the job-level daily total even with units under their limits`() {
        val job = job(maxVisits = 100, maxDailyVisits = 1)
        job.paymentUnits =
            listOf(
                paymentUnit(id = 1, maxTotal = 30, maxDaily = 5),
                paymentUnit(id = 2, maxTotal = 30, maxDaily = 5),
            )
        job.deliveries = listOf(delivery(1, 1, Date()))

        assertTrue(job.isFurtherWorkBlocked)
    }

    /**
     * A single unit at its limit leaves nothing to deliver against, so room under the job-level cap
     * earns nothing. Only reachable when the unit is capped tighter than the job.
     */
    @Test
    fun `a single-unit job is blocked once that unit is at its daily limit`() {
        val job = job(maxVisits = 100, maxDailyVisits = 5)
        job.paymentUnits = listOf(paymentUnit(id = 1, maxTotal = 50, maxDaily = 3))
        job.deliveries =
            listOf(
                delivery(1, 1, Date()),
                delivery(2, 1, Date()),
                delivery(3, 1, Date()),
            )

        assertTrue(job.isFurtherWorkBlocked)
    }

    /** Guards the empty case, where an unguarded `atLimit.size == units.size` compares 0 to 0. */
    @Test
    fun `a job with no payment units is not blocked while it has visits left`() {
        val job = job(maxVisits = 10, maxDailyVisits = 5)
        job.paymentUnits = emptyList()
        job.deliveries = listOf(delivery(1, 1, Date()))

        assertFalse(job.isFurtherWorkBlocked)
    }
}

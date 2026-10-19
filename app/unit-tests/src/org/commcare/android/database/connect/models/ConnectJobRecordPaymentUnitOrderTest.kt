package org.commcare.android.database.connect.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.connect.ConnectLearnJobTestData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers [ConnectJobRecord.getPaymentUnitsByMostRecentVisit], which decides the order the delivery
 * screens list payment units in.
 *
 * Deliveries are built through their own `fromJson`, so an unparseable date produces the same null
 * the real payload would.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobRecordPaymentUnitOrderTest {
    @Test
    fun `payment units are ordered by their most recent visit, newest first`() {
        val job =
            buildJob(
                unitCount = 3,
                visits =
                    listOf(
                        "unit-1" to "2026-01-05T09:00:00",
                        "unit-2" to "2026-03-09T09:00:00",
                        "unit-3" to "2026-02-14T09:00:00",
                    ),
            )

        assertEquals(listOf("unit-2", "unit-3", "unit-1"), job.getOrderedPaymentUnitUUIDs())
    }

    @Test
    fun `a payment unit is placed by its latest visit, not its earliest`() {
        val job =
            buildJob(
                unitCount = 2,
                visits =
                    listOf(
                        "unit-1" to "2025-01-01T09:00:00",
                        "unit-1" to "2026-06-01T09:00:00",
                        "unit-2" to "2026-03-01T09:00:00",
                    ),
            )

        assertEquals(listOf("unit-1", "unit-2"), job.getOrderedPaymentUnitUUIDs())
    }

    @Test
    fun `visits on the same day are ordered by time`() {
        val job =
            buildJob(
                unitCount = 2,
                visits =
                    listOf(
                        "unit-1" to "2026-03-09T09:00:00",
                        "unit-2" to "2026-03-09T15:30:00",
                    ),
            )

        assertEquals(listOf("unit-2", "unit-1"), job.getOrderedPaymentUnitUUIDs())
    }

    @Test
    fun `payment units without visits follow the visited payment units in server order`() {
        val job =
            buildJob(
                unitCount = 4,
                visits = listOf("unit-3" to "2026-03-09T09:00:00"),
            )

        assertEquals(listOf("unit-3", "unit-1", "unit-2", "unit-4"), job.getOrderedPaymentUnitUUIDs())
    }

    @Test
    fun `payment units whose latest visits match keep server order`() {
        val job =
            buildJob(
                unitCount = 3,
                visits =
                    listOf(
                        "unit-3" to "2026-03-09T09:00:00",
                        "unit-2" to "2026-03-09T09:00:00",
                    ),
            )

        assertEquals(listOf("unit-2", "unit-3", "unit-1"), job.getOrderedPaymentUnitUUIDs())
    }

    @Test
    fun `a visit whose date failed to parse does not place its payment unit`() {
        val job =
            buildJob(
                unitCount = 2,
                visits = listOf("unit-2" to "not-a-date"),
            )

        assertEquals(listOf("unit-1", "unit-2"), job.getOrderedPaymentUnitUUIDs())
    }

    @Test
    fun `a job with no visits keeps server order`() {
        assertEquals(listOf("unit-1", "unit-2", "unit-3"), buildJob(unitCount = 3).getOrderedPaymentUnitUUIDs())
    }

    @Test
    fun `sorting leaves the job's own payment unit order untouched`() {
        val job =
            buildJob(
                unitCount = 2,
                visits = listOf("unit-2" to "2026-03-09T09:00:00"),
            )

        job.paymentUnitsByMostRecentVisit

        assertEquals(listOf("unit-1", "unit-2"), job.paymentUnits.map { it.unitUUID })
    }

    private fun ConnectJobRecord.getOrderedPaymentUnitUUIDs(): List<String> = paymentUnitsByMostRecentVisit.map { it.unitUUID }

    private fun buildJob(
        unitCount: Int,
        visits: List<Pair<String, String>> = emptyList(),
    ): ConnectJobRecord =
        ConnectLearnJobTestData.job().apply {
            paymentUnits = (1..unitCount).map { buildPaymentUnit(this, it) }
            deliveries = visits.mapIndexed { index, (unitUUID, date) -> buildDelivery(this, index, unitUUID, date) }
        }

    private fun buildPaymentUnit(
        job: ConnectJobRecord,
        id: Int,
    ): ConnectPaymentUnitRecord =
        ConnectPaymentUnitRecord.fromJson(
            JSONObject().apply {
                put(ConnectPaymentUnitRecord.META_ID, id)
                put(ConnectPaymentUnitRecord.META_PAYMENT_UNIT_UUID, "unit-$id")
                put(ConnectPaymentUnitRecord.META_NAME, "Unit $id")
                put(ConnectPaymentUnitRecord.META_TOTAL, 10)
                put(ConnectPaymentUnitRecord.META_AMOUNT, 25)
            },
            job,
        )

    private fun buildDelivery(
        job: ConnectJobRecord,
        id: Int,
        unitUUID: String,
        date: String,
    ): ConnectJobDeliveryRecord =
        ConnectJobDeliveryRecord.fromJson(
            JSONObject().apply {
                put(ConnectJobDeliveryRecord.META_ID, id)
                put(ConnectJobDeliveryRecord.META_DATE, date)
                put(ConnectJobDeliveryRecord.META_STATUS, "pending")
                put(ConnectJobDeliveryRecord.META_UNIT_NAME, unitUUID)
                put(ConnectJobDeliveryRecord.META_SLUG, unitUUID)
                put(ConnectJobDeliveryRecord.META_SLUG_UUID, unitUUID)
                put(ConnectJobDeliveryRecord.META_ENTITY_ID, "entity-$id")
                put(ConnectJobDeliveryRecord.META_ENTITY_NAME, "Entity $id")
                put(ConnectJobDeliveryRecord.META_REASON, "")
            },
            job,
        )
}

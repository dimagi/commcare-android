package org.commcare.connect.network.connect.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.javarosa.core.model.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DeliveryAppProgressResponseParserTest {

    private lateinit var parser: DeliveryAppProgressResponseParser<DeliveryAppProgressResponseModel>
    private lateinit var job: ConnectJobRecord

    @Before
    fun setup() {
        parser = DeliveryAppProgressResponseParser()
        job = ConnectJobRecord()
    }

    private fun parse(json: String): DeliveryAppProgressResponseModel =
        parser.parse(200, ByteArrayInputStream(json.toByteArray()), job)

    private fun deliveryJson(id: Int): String =
        """
        {
            "id": $id,
            "visit_date": "2024-01-15T10:00:00.000",
            "status": "approved",
            "deliver_unit_name": "Unit A",
            "deliver_unit_slug": "unit-a",
            "entity_id": "entity-001",
            "entity_name": "Test Entity",
            "reason": "test reason",
            "deliver_unit_slug_id": "slug-uuid-001"
        }
        """.trimIndent()

    private fun paymentJson(id: String): String =
        """
        {
            "id": "$id",
            "payment_id": "pay-uuid-$id",
            "date_paid": "2024-01-15T10:00:00.000",
            "amount": 100,
            "confirmed": false
        }
        """.trimIndent()

    @Test
    fun testEmptyResponseBody_returnsNoFlags() {
        val result = parse("")

        assertFalse(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
    }

    @Test
    fun testEmptyJsonObject_returnsNoFlags() {
        val result = parse("{}")

        assertFalse(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
    }

    @Test
    fun testMaxPayments_updatesJobMaxVisitsAndSetsUpdatedJobFlag() {
        val result = parse("""{"max_payments": 10}""")

        assertTrue(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(10, job.maxVisits)
    }

    @Test
    fun testEndDate_updatesProjectEndDateAndSetsUpdatedJobFlag() {
        val result = parse("""{"end_date": "2025-06-30"}""")

        assertTrue(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(DateUtils.parseDate("2025-06-30"), job.projectEndDate)
    }

    @Test
    fun testPaymentAccrued_updatesAccruedAndSetsUpdatedJobFlag() {
        val result = parse("""{"payment_accrued": 500}""")

        assertTrue(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(500, job.paymentAccrued)
    }

    @Test
    fun testIsUserSuspended_updatesFieldAndSetsUpdatedJobFlag() {
        val result = parse("""{"is_user_suspended": true}""")

        assertTrue(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertTrue(job.isUserSuspended)
    }

    @Test
    fun testDeliveries_setsHasDeliveriesFlagAndPopulatesJobDeliveries() {
        val result = parse("""{"deliveries": [${deliveryJson(42)}]}""")

        assertFalse(result.updatedJob)
        assertTrue(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(1, job.deliveries.size)
        assertEquals(42, job.deliveries[0].deliveryId)
    }

    @Test
    fun testEmptyDeliveriesArray_setsHasDeliveriesFlagWithEmptyList() {
        val result = parse("""{"deliveries": []}""")

        assertFalse(result.updatedJob)
        assertTrue(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(0, job.deliveries.size)
    }

    @Test
    fun testPayments_setsHasPaymentFlagAndPopulatesJobPayments() {
        val result = parse("""{"payments": [${paymentJson("pay-001")}]}""")

        assertFalse(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertTrue(result.hasPayment)
        assertEquals(1, job.payments.size)
        assertEquals("100", job.payments[0].amount)
    }

    @Test
    fun testAllFields_setsAllThreeFlags() {
        val json =
            """
            {
                "max_payments": 20,
                "end_date": "2025-12-31",
                "payment_accrued": 300,
                "is_user_suspended": false,
                "deliveries": [${deliveryJson(1)}],
                "payments": [${paymentJson("pay-002")}]
            }
            """.trimIndent()

        val result = parse(json)

        assertTrue(result.updatedJob)
        assertTrue(result.hasDeliveries)
        assertTrue(result.hasPayment)
        assertEquals(20, job.maxVisits)
        assertEquals(DateUtils.parseDate("2025-12-31"), job.projectEndDate)
        assertEquals(300, job.paymentAccrued)
        assertFalse(job.isUserSuspended)
        assertEquals(1, job.deliveries.size)
        assertEquals(DateUtils.parseDateTime("2024-01-15T10:00:00.000"), job.deliveries[0].date)
        assertEquals(1, job.payments.size)
        assertEquals(DateUtils.parseDateTime("2024-01-15T10:00:00.000"), job.payments[0].date)
    }

    @Test(expected = RuntimeException::class)
    fun testInvalidJson_throwsRuntimeException() {
        parse("{ invalid json }")
    }
}

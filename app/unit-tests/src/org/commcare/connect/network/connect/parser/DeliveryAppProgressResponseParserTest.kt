package org.commcare.connect.network.connect.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.javarosa.core.model.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DeliveryAppProgressResponseParserTest {
    private lateinit var parser: DeliveryAppProgressResponseParser<DeliveryAppProgressResponseModel>
    private lateinit var job: ConnectJobRecord

    @Before
    fun setup() {
        parser = DeliveryAppProgressResponseParser()
        job = ConnectJobRecord()
        job.jobUUID = ""
    }

    private fun parse(json: String): DeliveryAppProgressResponseModel = parser.parse(200, ByteArrayInputStream(json.toByteArray()), job)

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
    fun `empty response body returns no flags`() {
        val result = parse("")

        assertFalse(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
    }

    @Test
    fun `empty JSON object returns no flags`() {
        val result = parse("{}")

        assertFalse(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
    }

    @Test
    fun `max_payments updates job maxVisits and sets updatedJob flag`() {
        val result = parse("""{"max_payments": 10}""")

        assertTrue(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(10, job.maxVisits)
    }

    @Test
    fun `end_date updates projectEndDate and sets updatedJob flag`() {
        val result = parse("""{"end_date": "2025-06-30"}""")

        assertTrue(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(DateUtils.parseDate("2025-06-30"), job.projectEndDate)
    }

    @Test
    fun `payment_accrued updates paymentAccrued and sets updatedJob flag`() {
        val result = parse("""{"payment_accrued": 500}""")

        assertTrue(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(500, job.paymentAccrued)
    }

    @Test
    fun `is_user_suspended updates field and sets updatedJob flag`() {
        val result = parse("""{"is_user_suspended": true}""")

        assertTrue(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertTrue(job.isUserSuspended)
    }

    @Test
    fun `deliveries sets hasDeliveries flag and populates job deliveries`() {
        val result = parse("""{"deliveries": [${deliveryJson(42)}]}""")

        assertFalse(result.updatedJob)
        assertTrue(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(1, job.deliveries.size)
        assertEquals(42, job.deliveries[0].deliveryId)
    }

    @Test
    fun `empty deliveries array sets hasDeliveries flag with empty list`() {
        val result = parse("""{"deliveries": []}""")

        assertFalse(result.updatedJob)
        assertTrue(result.hasDeliveries)
        assertFalse(result.hasPayment)
        assertEquals(0, job.deliveries.size)
    }

    @Test
    fun `payments sets hasPayment flag and populates job payments`() {
        val result = parse("""{"payments": [${paymentJson("pay-001")}]}""")

        assertFalse(result.updatedJob)
        assertFalse(result.hasDeliveries)
        assertTrue(result.hasPayment)
        assertEquals(1, job.payments.size)
        assertEquals("100", job.payments[0].amount)
    }

    @Test
    fun `all fields set all three flags`() {
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
    fun `invalid JSON throws RuntimeException`() {
        parse("{ invalid json }")
    }

    private fun taskJson(
        taskId: String = "cabcc77c-6610-485d-b147-97f28e7aca8f",
        name: String = "relarn_task_4",
        description: String = "This is relearn task 4",
        status: String = "assigned",
        dueDate: String? = "2026-07-17",
        dateCreated: String = "2026-07-03T10:36:45.559775Z",
    ): String {
        val dueDateField = if (dueDate != null) """"due_date": "$dueDate",""" else ""
        return """
            {
                "assigned_task_id": "$taskId",
                "task_name": "$name",
                "task_description": "$description",
                "status": "$status",
                $dueDateField
                "date_created": "$dateCreated"
            }
            """.trimIndent()
    }

    private fun isoUtcFormat() =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

    @Test
    fun `assigned_tasks absent returns empty task list`() {
        val result = parse("{}")

        assertEquals(0, result.tasks.size)
    }

    @Test
    fun `assigned_tasks empty array returns empty task list`() {
        val result = parse("""{"assigned_tasks": []}""")

        assertEquals(0, result.tasks.size)
    }

    @Test
    fun `single task parsed with all fields`() {
        val result = parse("""{"assigned_tasks": [${taskJson()}]}""")

        assertEquals(1, result.tasks.size)
        val task = result.tasks[0]
        assertEquals("cabcc77c-6610-485d-b147-97f28e7aca8f", task.taskId)
        assertEquals("relarn_task_4", task.name)
        assertEquals("This is relearn task 4", task.description)
        assertEquals("assigned", task.status)
        assertEquals(DateUtils.parseDate("2026-07-17"), task.dueDate)
        assertEquals(Date(isoUtcFormat().parse("2026-07-03T10:36:45Z")!!.time + 559L), task.dateCreated)
    }

    @Test
    fun `task jobUUID is set from parent job`() {
        job.jobUUID = "test-job-uuid"
        val result = parse("""{"assigned_tasks": [${taskJson()}]}""")

        assertEquals("test-job-uuid", result.tasks[0].jobUUID)
    }

    @Test
    fun `multiple tasks all parsed`() {
        val result =
            parse(
                """{"assigned_tasks": [${taskJson(taskId = "id-1")}, ${taskJson(taskId = "id-2")}, ${taskJson(taskId = "id-3")}]}""",
            )

        assertEquals(3, result.tasks.size)
        assertEquals("id-1", result.tasks[0].taskId)
        assertEquals("id-2", result.tasks[1].taskId)
        assertEquals("id-3", result.tasks[2].taskId)
    }

    @Test
    fun `task with no due_date leaves dueDate null`() {
        val result = parse("""{"assigned_tasks": [${taskJson(dueDate = null)}]}""")

        assertNull(result.tasks[0].dueDate)
    }

    @Test
    fun `task with missing optional fields uses defaults`() {
        val minimalTask =
            """
            {
                "assigned_task_id": "some-id",
                "task_name": "minimal",
                "status": "pending",
                "date_created": "2026-07-01T08:00:00Z"
            }
            """.trimIndent()
        val result = parse("""{"assigned_tasks": [$minimalTask]}""")

        val task = result.tasks[0]
        assertEquals("", task.description)
        assertEquals("", task.connectChannelId)
        assertEquals("", task.type)
        assertNull(task.dueDate)
    }

    @Test
    fun `date_created with microseconds parsed correctly`() {
        val result = parse("""{"assigned_tasks": [${taskJson(dateCreated = "2026-07-03T10:36:45.559775Z")}]}""")

        val expected = Date(isoUtcFormat().parse("2026-07-03T10:36:45Z")!!.time + 559L)
        assertEquals(expected, result.tasks[0].dateCreated)
    }

    @Test
    fun `date_created with timezone offset parsed correctly`() {
        val result = parse("""{"assigned_tasks": [${taskJson(dateCreated = "2026-07-03T10:36:45+00:00")}]}""")

        val expected = isoUtcFormat().parse("2026-07-03T10:36:45Z")
        assertEquals(expected, result.tasks[0].dateCreated)
    }

    @Test
    fun `tasks parsed alongside other fields without interfering`() {
        val json =
            """
            {
                "max_payments": 5,
                "assigned_tasks": [${taskJson()}]
            }
            """.trimIndent()

        val result = parse(json)

        assertTrue(result.updatedJob)
        assertEquals(5, job.maxVisits)
        assertEquals(1, result.tasks.size)
    }
}

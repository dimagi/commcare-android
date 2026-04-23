package org.commcare.connect.network.connect.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.network.connect.models.ConnectTaskStatus
import org.commcare.connect.network.connect.models.DeliveryAppProgressResponseModel
import org.javarosa.core.model.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DeliveryAppProgressResponseParserTest {
    private fun job(): ConnectJobRecord {
        val j = ConnectJobRecord()
        j.jobId = 1
        j.jobUUID = "job-uuid-1"
        return j
    }

    private fun parse(
        json: String,
        job: ConnectJobRecord,
    ): DeliveryAppProgressResponseModel {
        val parser = DeliveryAppProgressResponseParser<DeliveryAppProgressResponseModel>()
        val stream = ByteArrayInputStream(json.toByteArray())
        return parser.parse(200, stream, job)
    }

    @Test
    fun parse_withAssignedTasks_populatesParsedTasksAndSetsFlag() {
        val json =
            """
            {
              "assigned_tasks": [
                {
                  "assigned_task_id": "t1",
                  "task_name": "Review module",
                  "status": "assigned",
                  "due_date": "2026-05-01T12:00:00.000"
                },
                {
                  "assigned_task_id": "t2",
                  "task_name": "Complete quiz",
                  "task_description": "Short quiz",
                  "status": "completed",
                  "due_date": "2026-05-02T12:00:00.000",
                  "date_modified": "2026-04-21T09:00:00.000"
                }
              ]
            }
            """.trimIndent()

        val model = parse(json, job())

        assertTrue(model.hasTasks)
        assertEquals(2, model.parsedTasks.size)
        assertEquals(ConnectTaskStatus.ASSIGNED, model.parsedTasks[0].status)
        assertNull(model.parsedTasks[0].dateModified)
        assertEquals(ConnectTaskStatus.COMPLETED, model.parsedTasks[1].status)
        assertEquals(
            DateUtils.parseDateTime("2026-04-21T09:00:00.000"),
            model.parsedTasks[1].dateModified,
        )
    }

    @Test
    fun parse_withoutAssignedTasks_leavesFlagFalseAndParsedTasksEmpty() {
        val model = parse("{}", job())

        assertFalse(model.hasTasks)
        assertTrue(model.parsedTasks.isEmpty())
    }

    @Test
    fun parse_withEmptyAssignedTasksArray_setsFlagTrueAndListEmpty() {
        val model = parse("""{ "assigned_tasks": [] }""", job())

        assertTrue(model.hasTasks)
        assertTrue(model.parsedTasks.isEmpty())
    }

    @Test
    fun parse_withDateModifiedExplicitlyNull_producesNullDate() {
        val json =
            """
            {
              "assigned_tasks": [
                {
                  "assigned_task_id": "t1",
                  "task_name": "X",
                  "status": "assigned",
                  "due_date": "2026-05-01T12:00:00.000",
                  "date_modified": null
                }
              ]
            }
            """.trimIndent()

        val model = parse(json, job())

        assertNull(model.parsedTasks[0].dateModified)
    }
}

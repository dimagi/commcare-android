package org.commcare.connect.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.preferences.ConnectJobPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectTaskUtilsTest {
    private lateinit var context: Context

    private val jobUUID = "test-job-uuid"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        ConnectDatabaseHelper.teardown()
    }

    private fun prefs() = ConnectJobUtils.getJobPreferences(jobUUID)

    private fun storage() = ConnectDatabaseHelper.getConnectStorage(context, ConnectTaskRecord::class.java)

    private fun makeTask(
        taskId: String = "task-1",
        status: String = "assigned",
        type: String = "learning",
        dateModified: Date = Date(),
        dueDate: Date = Date(0),
    ) = ConnectTaskRecord().apply {
        this.jobUUID = this@ConnectTaskUtilsTest.jobUUID
        this.taskId = taskId
        this.status = status
        this.type = type
        this.dateModified = dateModified
        this.dueDate = dueDate
        this.name = "Task $taskId"
    }

    private fun makeJob(jobStatus: Int = ConnectJobRecord.STATUS_DELIVERING) =
        mockk<ConnectJobRecord> {
            every { jobUUID } returns this@ConnectTaskUtilsTest.jobUUID
            every { status } returns jobStatus
        }

    private fun seedTask(task: ConnectTaskRecord) = storage().write(task)

    private fun queryTasks() =
        storage()
            .getRecordsForValues(
                arrayOf(ConnectTaskRecord.META_JOB_UUID),
                arrayOf(jobUUID),
            ).toList()

    // ===========================
    // storeTasks
    // ===========================

    @Test
    fun `storeTasks with empty list and no existing does not add to DB`() {
        ConnectTaskUtils.storeTasks(context, emptyList(), jobUUID)

        assertTrue(queryTasks().isEmpty())
    }

    @Test
    fun `storeTasks writes new task not yet in storage`() {
        ConnectTaskUtils.storeTasks(context, listOf(makeTask(taskId = "new-task")), jobUUID)

        val tasks = queryTasks()
        assertEquals(1, tasks.size)
        assertEquals("new-task", tasks.first().taskId)
    }

    @Test
    fun `storeTasks does not rewrite an unchanged existing task`() {
        seedTask(makeTask(taskId = "task-1", status = "assigned"))
        val incoming = makeTask(taskId = "task-1", status = "assigned")

        ConnectTaskUtils.storeTasks(context, listOf(incoming), jobUUID)

        assertEquals(1, queryTasks().size)
        assertEquals(ConnectJobPreferences.TIMESTAMP_NOT_SET, prefs().getTaskModifiedTime())
    }

    @Test
    fun `storeTasks updates existing task whose mutable field changed`() {
        seedTask(makeTask(taskId = "task-1", status = "assigned"))
        val incoming = makeTask(taskId = "task-1", status = "completed")

        ConnectTaskUtils.storeTasks(context, listOf(incoming), jobUUID)

        assertEquals("completed", queryTasks().first().status)
    }

    @Test
    fun `storeTasks updates task modified time when a task changed`() {
        seedTask(makeTask(taskId = "task-1", status = "assigned"))

        ConnectTaskUtils.storeTasks(context, listOf(makeTask(taskId = "task-1", status = "completed")), jobUUID)

        assertNotEquals(ConnectJobPreferences.TIMESTAMP_NOT_SET, prefs().getTaskModifiedTime())
    }

    @Test
    fun `storeTasks does not update task modified time when nothing changed`() {
        seedTask(makeTask(taskId = "task-1", status = "assigned"))

        ConnectTaskUtils.storeTasks(context, listOf(makeTask(taskId = "task-1", status = "assigned")), jobUUID)

        assertEquals(ConnectJobPreferences.TIMESTAMP_NOT_SET, prefs().getTaskModifiedTime())
    }

    @Test
    fun `storeTasks sets relearn task pending true when any incoming task is assigned`() {
        ConnectTaskUtils.storeTasks(context, listOf(makeTask(status = "assigned")), jobUUID)

        assertTrue(prefs().isRelearnTaskPending())
    }

    @Test
    fun `storeTasks sets relearn task pending false when no incoming tasks are assigned`() {
        ConnectTaskUtils.storeTasks(context, listOf(makeTask(status = "completed")), jobUUID)

        assertFalse(prefs().isRelearnTaskPending())
    }

    @Test
    fun `storeTasks removes task not present in incoming payload`() {
        seedTask(makeTask(taskId = "old-task"))

        ConnectTaskUtils.storeTasks(context, emptyList(), jobUUID)

        assertTrue(queryTasks().isEmpty())
    }

    @Test
    fun `storeTasks does not remove task that is still in incoming payload`() {
        seedTask(makeTask(taskId = "task-1"))

        ConnectTaskUtils.storeTasks(context, listOf(makeTask(taskId = "task-1")), jobUUID)

        assertEquals(1, queryTasks().size)
    }

    @Test
    fun `storeTasks marks changed when a task is deleted`() {
        seedTask(makeTask(taskId = "old-task"))

        ConnectTaskUtils.storeTasks(context, emptyList(), jobUUID)

        assertNotEquals(ConnectJobPreferences.TIMESTAMP_NOT_SET, prefs().getTaskModifiedTime())
    }

    @Test
    fun `storeTasks always resets relearn tasks completed time`() {
        prefs().setRelearnTasksCompletedTime(12345L)

        ConnectTaskUtils.storeTasks(context, listOf(makeTask()), jobUUID)

        assertEquals(ConnectJobPreferences.TIMESTAMP_NOT_SET, prefs().getRelearnTasksCompletedTimeMs())
    }

    // ===========================
    // hasPendingTask
    // ===========================

    @Test
    fun `hasPendingTask returns true when DB has an assigned task`() {
        seedTask(makeTask(status = "assigned"))

        assertTrue(ConnectTaskUtils.hasPendingTask(context, jobUUID))
    }

    @Test
    fun `hasPendingTask returns false when DB has only completed tasks`() {
        seedTask(makeTask(status = "completed"))

        assertFalse(ConnectTaskUtils.hasPendingTask(context, jobUUID))
    }

    @Test
    fun `hasPendingTask falls back to preference when DB is empty and pref is true`() {
        prefs().setRelearnTaskPending(true)

        assertTrue(ConnectTaskUtils.hasPendingTask(context, jobUUID))
    }

    @Test
    fun `hasPendingTask falls back to preference when DB is empty and pref is false`() {
        assertFalse(ConnectTaskUtils.hasPendingTask(context, jobUUID))
    }

    // ===========================
    // getPendingTaskOfType / hasPendingTaskOfType
    // ===========================

    @Test
    fun `getPendingTaskOfType returns task matching type and assigned status`() {
        seedTask(makeTask(taskId = "t1", status = "assigned", type = "learning"))

        val result = ConnectTaskUtils.getPendingTaskOfType(context, jobUUID, "learning")
        assertEquals("t1", result?.taskId)
    }

    @Test
    fun `getPendingTaskOfType returns null when type does not match`() {
        seedTask(makeTask(status = "assigned", type = "delivery"))

        assertNull(ConnectTaskUtils.getPendingTaskOfType(context, jobUUID, "learning"))
    }

    @Test
    fun `getPendingTaskOfType returns null when type matches but status is not assigned`() {
        seedTask(makeTask(status = "completed", type = "learning"))

        assertNull(ConnectTaskUtils.getPendingTaskOfType(context, jobUUID, "learning"))
    }

    @Test
    fun `hasPendingTaskOfType returns true when matching assigned task exists`() {
        seedTask(makeTask(status = "assigned", type = "learning"))

        assertTrue(ConnectTaskUtils.hasPendingTaskOfType(context, jobUUID, "learning"))
    }

    @Test
    fun `hasPendingTaskOfType returns false when no tasks in DB`() {
        assertFalse(ConnectTaskUtils.hasPendingTaskOfType(context, jobUUID, "learning"))
    }

    // ===========================
    // shouldShowTasksCompletedMessage
    // ===========================

    @Test
    fun `shouldShowTasksCompletedMessage returns false when there is a pending task`() {
        seedTask(makeTask(status = "assigned"))

        assertFalse(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob()))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns false when there are no completed tasks`() {
        assertFalse(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob()))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns true when task completed within 6h and job is DELIVERING`() {
        seedTask(makeTask(status = "completed", dateModified = Date()))

        assertTrue(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob(ConnectJobRecord.STATUS_DELIVERING)))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns false when task completed more than 6h ago`() {
        val sevenHoursAgo = Date(System.currentTimeMillis() - 7 * 60 * 60 * 1000L)
        seedTask(makeTask(status = "completed", dateModified = sevenHoursAgo))

        assertFalse(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob()))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns false when completed within 6h but job not DELIVERING`() {
        seedTask(makeTask(status = "completed", dateModified = Date()))

        assertFalse(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob(ConnectJobRecord.STATUS_LEARNING)))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns true via legacy pref when completion is less than 6h ago`() {
        val recentMs = System.currentTimeMillis() - 1000L
        prefs().setRelearnTasksCompletedTime(recentMs)

        assertTrue(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob()))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns false via legacy pref when completion was more than 6h ago`() {
        val sevenHoursAgoMs = System.currentTimeMillis() - 7 * 60 * 60 * 1000L
        prefs().setRelearnTasksCompletedTime(sevenHoursAgoMs)

        assertFalse(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob()))
    }
}

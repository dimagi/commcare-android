package org.commcare.connect.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord.Companion.STATUS_ASSIGNED
import org.commcare.android.database.connect.models.ConnectTaskRecord.Companion.STATUS_COMPLETED
import org.commcare.preferences.ConnectJobPreferences
import org.commcare.utils.SyncDetailCalculations
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
        unmockkAll()
    }

    private fun prefs() = ConnectJobUtils.getJobPreferences(jobUUID)

    private fun storage() = ConnectDatabaseHelper.getConnectStorage(context, ConnectTaskRecord::class.java)

    private fun makeTask(
        taskId: String = "task-1",
        status: String = STATUS_ASSIGNED,
        mode: String = "learning",
        dateModified: Date = Date(),
        dueDate: Date = Date(0),
    ) = ConnectTaskRecord().apply {
        this.jobUUID = this@ConnectTaskUtilsTest.jobUUID
        this.taskId = taskId
        this.status = status
        this.mode = mode
        this.dateModified = dateModified
        this.dueDate = dueDate
        this.name = "Task $taskId"
    }

    private fun makeJob(jobStatus: Int = STATUS_DELIVERING) =
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
        val sharedDate = Date()
        seedTask(makeTask(taskId = "task-1", status = STATUS_ASSIGNED, dateModified = sharedDate))
        val incoming = makeTask(taskId = "task-1", status = STATUS_ASSIGNED, dateModified = sharedDate)

        ConnectTaskUtils.storeTasks(context, listOf(incoming), jobUUID)

        assertEquals(1, queryTasks().size)
        assertEquals(ConnectJobPreferences.TIMESTAMP_NOT_SET, prefs().getTaskModifiedTime())
    }

    @Test
    fun `storeTasks updates existing task whose mutable field changed`() {
        seedTask(makeTask(taskId = "task-1", status = STATUS_ASSIGNED))
        val incoming = makeTask(taskId = "task-1", status = STATUS_COMPLETED)

        ConnectTaskUtils.storeTasks(context, listOf(incoming), jobUUID)

        assertEquals(STATUS_COMPLETED, queryTasks().first().status)
    }

    @Test
    fun `storeTasks updates task modified time when a task changed`() {
        seedTask(makeTask(taskId = "task-1", status = STATUS_ASSIGNED))

        ConnectTaskUtils.storeTasks(context, listOf(makeTask(taskId = "task-1", status = STATUS_COMPLETED)), jobUUID)

        assertNotEquals(ConnectJobPreferences.TIMESTAMP_NOT_SET, prefs().getTaskModifiedTime())
    }

    @Test
    fun `storeTasks does not update task modified time when nothing changed`() {
        val sharedDate = Date()
        seedTask(makeTask(taskId = "task-1", status = STATUS_ASSIGNED, dateModified = sharedDate))

        ConnectTaskUtils.storeTasks(
            context,
            listOf(makeTask(taskId = "task-1", status = STATUS_ASSIGNED, dateModified = sharedDate)),
            jobUUID,
        )

        assertEquals(ConnectJobPreferences.TIMESTAMP_NOT_SET, prefs().getTaskModifiedTime())
    }

    @Test
    fun `storeTasks sets relearn task pending true when any incoming task is assigned`() {
        ConnectTaskUtils.storeTasks(context, listOf(makeTask(status = STATUS_ASSIGNED)), jobUUID)

        assertTrue(prefs().isRelearnTaskPending())
    }

    @Test
    fun `storeTasks sets relearn task pending false when no incoming tasks are assigned`() {
        ConnectTaskUtils.storeTasks(context, listOf(makeTask(status = STATUS_COMPLETED)), jobUUID)

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
        seedTask(makeTask(status = STATUS_ASSIGNED))

        assertTrue(ConnectTaskUtils.hasPendingTask(context, jobUUID))
    }

    @Test
    fun `hasPendingTask returns false when DB has only completed tasks`() {
        seedTask(makeTask(status = STATUS_COMPLETED))

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
    // getPendingTaskOfMode / hasPendingTaskOfMode
    // ===========================

    @Test
    fun `getPendingTaskOfMode returns task matching mode and assigned status`() {
        seedTask(makeTask(taskId = "t1", status = STATUS_ASSIGNED, mode = "learning"))

        val result = ConnectTaskUtils.getPendingTaskOfMode(context, jobUUID, "learning")
        assertEquals("t1", result?.taskId)
    }

    @Test
    fun `getPendingTaskOfMode returns null when mode does not match`() {
        seedTask(makeTask(status = STATUS_ASSIGNED, mode = "delivery"))

        assertNull(ConnectTaskUtils.getPendingTaskOfMode(context, jobUUID, "learning"))
    }

    @Test
    fun `getPendingTaskOfMode returns null when mode matches but status is not assigned`() {
        seedTask(makeTask(status = STATUS_COMPLETED, mode = "learning"))

        assertNull(ConnectTaskUtils.getPendingTaskOfMode(context, jobUUID, "learning"))
    }

    @Test
    fun `hasPendingTaskOfMode returns true when matching assigned task exists`() {
        seedTask(makeTask(status = STATUS_ASSIGNED, mode = "learning"))

        assertTrue(ConnectTaskUtils.hasPendingTaskOfMode(context, jobUUID, "learning"))
    }

    @Test
    fun `hasPendingTaskOfMode returns false when no tasks in DB`() {
        assertFalse(ConnectTaskUtils.hasPendingTaskOfMode(context, jobUUID, "learning"))
    }

    // ===========================
    // shouldShowTasksCompletedMessage
    // ===========================

    @Test
    fun `shouldShowTasksCompletedMessage returns false when there is a pending task`() {
        seedTask(makeTask(status = STATUS_ASSIGNED))

        assertFalse(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob()))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns false when there are no completed tasks`() {
        assertFalse(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob()))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns true when task completed within 6h and job is DELIVERING`() {
        seedTask(makeTask(status = STATUS_COMPLETED, dateModified = Date()))

        assertTrue(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob(STATUS_DELIVERING)))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns false when task completed more than 6h ago`() {
        val sevenHoursAgo = Date(System.currentTimeMillis() - 7 * 60 * 60 * 1000L)
        seedTask(makeTask(status = STATUS_COMPLETED, dateModified = sevenHoursAgo))

        assertFalse(ConnectTaskUtils.shouldShowTasksCompletedMessage(context, makeJob()))
    }

    @Test
    fun `shouldShowTasksCompletedMessage returns false when completed within 6h but job not DELIVERING`() {
        seedTask(makeTask(status = STATUS_COMPLETED, dateModified = Date()))

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

    // ===========================
    // isLastTaskUpdateLaterThanLastSync
    // ===========================

    @Test
    fun `isLastTaskUpdateLaterThanLastSync returns false when no job is seated`() {
        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.getJobForSeatedApp(context) } returns null

        assertFalse(ConnectTaskUtils.isLastTaskUpdateLaterThanLastSync(context))
    }

    @Test
    fun `isLastTaskUpdateLaterThanLastSync returns false when job is not DELIVERING`() {
        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.getJobForSeatedApp(context) } returns makeJob(ConnectJobRecord.STATUS_LEARNING)

        assertFalse(ConnectTaskUtils.isLastTaskUpdateLaterThanLastSync(context))
    }

    @Test
    fun `isLastTaskUpdateLaterThanLastSync returns false when task modified time is not set`() {
        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.getJobForSeatedApp(context) } returns makeJob()

        assertFalse(ConnectTaskUtils.isLastTaskUpdateLaterThanLastSync(context))
    }

    @Test
    fun `isLastTaskUpdateLaterThanLastSync returns false when task was modified before last sync`() {
        // Trigger a real task change so taskModifiedTime = ~now
        seedTask(makeTask(taskId = "task-1", status = STATUS_ASSIGNED))
        ConnectTaskUtils.storeTasks(context, listOf(makeTask(taskId = "task-1", status = STATUS_COMPLETED)), jobUUID)

        // Mock last sync as happening after the task update
        mockkStatic(ConnectJobUtils::class)
        mockkStatic(SyncDetailCalculations::class)
        every { ConnectJobUtils.getJobForSeatedApp(context) } returns makeJob()
        every { SyncDetailCalculations.getLastSyncTime() } returns System.currentTimeMillis() + 10_000L

        assertFalse(ConnectTaskUtils.isLastTaskUpdateLaterThanLastSync(context))
    }

    @Test
    fun `isLastTaskUpdateLaterThanLastSync returns true when task was modified after last sync`() {
        mockkStatic(ConnectJobUtils::class)
        mockkStatic(SyncDetailCalculations::class)
        every { ConnectJobUtils.getJobForSeatedApp(context) } returns makeJob()
        every { SyncDetailCalculations.getLastSyncTime() } returns 0L
        seedTask(makeTask(taskId = "task-1", status = STATUS_ASSIGNED))
        ConnectTaskUtils.storeTasks(context, listOf(makeTask(taskId = "task-1", status = STATUS_COMPLETED)), jobUUID)

        assertTrue(ConnectTaskUtils.isLastTaskUpdateLaterThanLastSync(context))
    }

    // ===========================
    // getPendingTasksForJob
    // ===========================

    @Test
    fun `getPendingTasksForJob returns only assigned tasks`() {
        seedTask(makeTask(taskId = "assigned", status = STATUS_ASSIGNED))
        seedTask(makeTask(taskId = "completed", status = STATUS_COMPLETED))

        val pending = ConnectTaskUtils.getPendingTasksForJob(context, jobUUID)

        assertEquals(listOf("assigned"), pending.map { it.taskId })
    }

    @Test
    fun `getPendingTasksForJob orders tasks by soonest due date`() {
        seedTask(makeTask(taskId = "later", dueDate = Date(3_000)))
        seedTask(makeTask(taskId = "soonest", dueDate = Date(1_000)))
        seedTask(makeTask(taskId = "middle", dueDate = Date(2_000)))

        val pending = ConnectTaskUtils.getPendingTasksForJob(context, jobUUID)

        assertEquals(listOf("soonest", "middle", "later"), pending.map { it.taskId })
    }

    @Test
    fun `getPendingTasksForJob ignores tasks belonging to another opportunity`() {
        seedTask(makeTask(taskId = "ours"))
        seedTask(makeTask(taskId = "theirs").apply { jobUUID = "another-job-uuid" })

        val pending = ConnectTaskUtils.getPendingTasksForJob(context, jobUUID)

        assertEquals(listOf("ours"), pending.map { it.taskId })
    }

    @Test
    fun `getPendingTasksForJob returns nothing when the job has no tasks`() {
        assertTrue(ConnectTaskUtils.getPendingTasksForJob(context, jobUUID).isEmpty())
    }
}

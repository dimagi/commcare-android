package org.commcare.android.database.connect.models

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.CommCareTestApplication
import org.commcare.connect.ConnectConstants.RELEARN_TASKS_COMPLETED_TIME
import org.commcare.connect.ConnectConstants.RELEARN_TASK_PENDING_PREFIX
import org.commcare.connect.network.connect.models.ConnectTaskStatus
import org.commcare.connect.network.connect.models.ParsedConnectTask
import org.commcare.core.services.CommCarePreferenceManagerFactory
import org.commcare.core.services.ICommCarePreferenceManager
import org.javarosa.core.model.utils.DateUtils
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectJobRecordRelearnTasksTest {
    private val jobUUID = "job-uuid-1"
    private val pendingKey = RELEARN_TASK_PENDING_PREFIX + jobUUID

    private lateinit var prefs: ICommCarePreferenceManager

    @Before
    fun setUp() {
        mockkStatic(CommCarePreferenceManagerFactory::class)
        prefs = mockk(relaxed = true)
        every { CommCarePreferenceManagerFactory.getCommCarePreferenceManager() } returns prefs
    }

    @After
    fun tearDown() {
        unmockkStatic(CommCarePreferenceManagerFactory::class)
    }

    private fun task(
        status: String,
        dateModified: Date? = null,
    ) = ParsedConnectTask(status, dateModified)

    // --- syncRelearnTasksPrefs ---

    @Test
    fun sync_nullJobUUID_noop() {
        ConnectJobRecord.syncRelearnTasksPrefs(null, listOf(task(ConnectTaskStatus.ASSIGNED)))

        verify(exactly = 0) { prefs.putLong(any(), any()) }
    }

    @Test
    fun sync_nullPrefsManager_noop() {
        every { CommCarePreferenceManagerFactory.getCommCarePreferenceManager() } returns null

        ConnectJobRecord.syncRelearnTasksPrefs(jobUUID, listOf(task(ConnectTaskStatus.ASSIGNED)))

        verify(exactly = 0) { prefs.putLong(any(), any()) }
    }

    @Test
    fun sync_emptyList_clearsPendingAndLeavesCompletedTimeAlone() {
        ConnectJobRecord.syncRelearnTasksPrefs(jobUUID, emptyList())

        verify { prefs.putLong(pendingKey, 0) }
        verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }
    }

    @Test
    fun sync_nullList_clearsPendingAndLeavesCompletedTimeAlone() {
        ConnectJobRecord.syncRelearnTasksPrefs(jobUUID, null)

        verify { prefs.putLong(pendingKey, 0) }
        verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }
    }

    @Test
    fun sync_anyAssigned_setsPendingOneAndResetsCompletedTimeToMinusOne() {
        ConnectJobRecord.syncRelearnTasksPrefs(
            jobUUID,
            listOf(
                task(ConnectTaskStatus.COMPLETED),
                task(ConnectTaskStatus.ASSIGNED),
            ),
        )

        verify { prefs.putLong(pendingKey, 1) }
        verify { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, -1) }
    }

    @Test
    fun sync_allCompletedAndCompletedTimeUnset_writesLatestDateModified() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns -1L
        val earlier = DateUtils.parseDateTime("2026-04-20T09:00:00.000")
        val later = DateUtils.parseDateTime("2026-04-21T11:00:00.000")

        ConnectJobRecord.syncRelearnTasksPrefs(
            jobUUID,
            listOf(
                task(ConnectTaskStatus.COMPLETED, earlier),
                task(ConnectTaskStatus.COMPLETED, later),
            ),
        )

        verify { prefs.putLong(pendingKey, 0) }
        verify { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, later.time) }
    }

    @Test
    fun sync_allCompletedAndCompletedTimeUnsetNoDateModified_writesCurrentTime() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns -1L
        val captured = slot<Long>()
        every { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, capture(captured)) } returns Unit

        val before = System.currentTimeMillis()
        ConnectJobRecord.syncRelearnTasksPrefs(
            jobUUID,
            listOf(task(ConnectTaskStatus.COMPLETED)),
        )
        val after = System.currentTimeMillis()

        verify { prefs.putLong(pendingKey, 0) }
        assertTrue(captured.captured in before..after)
    }

    @Test
    fun sync_allCompletedAndCompletedTimeAlreadySet_leavesCompletedTimeAlone() {
        every { prefs.getLong(RELEARN_TASKS_COMPLETED_TIME, -1) } returns 123456789L

        ConnectJobRecord.syncRelearnTasksPrefs(
            jobUUID,
            listOf(task(ConnectTaskStatus.COMPLETED)),
        )

        verify { prefs.putLong(pendingKey, 0) }
        verify(exactly = 0) { prefs.putLong(RELEARN_TASKS_COMPLETED_TIME, any()) }
    }

    @Test
    fun sync_usesJobUUIDAsKeySuffix() {
        val otherJobUUID = "job-uuid-2"
        val otherPendingKey = RELEARN_TASK_PENDING_PREFIX + otherJobUUID

        ConnectJobRecord.syncRelearnTasksPrefs(
            otherJobUUID,
            listOf(task(ConnectTaskStatus.ASSIGNED)),
        )

        verify { prefs.putLong(otherPendingKey, 1) }
        verify(exactly = 0) { prefs.putLong(pendingKey, any()) }
    }

    // --- isRelearnTaskPending ---

    @Test
    fun isRelearnTaskPending_prefOne_returnsTrue() {
        val job = ConnectJobRecord()
        job.jobUUID = jobUUID
        every { prefs.getLong(pendingKey, 0) } returns 1L

        assertTrue(job.isRelearnTaskPending)
    }

    @Test
    fun isRelearnTaskPending_prefZero_returnsFalse() {
        val job = ConnectJobRecord()
        job.jobUUID = jobUUID
        every { prefs.getLong(pendingKey, 0) } returns 0L

        assertFalse(job.isRelearnTaskPending)
    }

    @Test
    fun isRelearnTaskPending_nullJobUUID_returnsFalse() {
        val job = ConnectJobRecord()
        job.jobUUID = null

        assertFalse(job.isRelearnTaskPending)
        verify(exactly = 0) { prefs.getLong(any(), any()) }
    }

    @Test
    fun isRelearnTaskPending_nullPrefsManager_returnsFalse() {
        every { CommCarePreferenceManagerFactory.getCommCarePreferenceManager() } returns null
        val job = ConnectJobRecord()
        job.jobUUID = jobUUID

        assertFalse(job.isRelearnTaskPending)
    }
}

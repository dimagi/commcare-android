package org.commcare.activities

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectTaskUtils
import java.util.Date

/**
 * Shared base for the home screen's PersonalId/Connect characterization tests (`HomeConnect*`).
 *
 * [HomeScreenActivityTest] already pins the Connect statics to "no job / nothing to show" so the
 * home screen boots deterministically; this class adds the fixtures a test needs when Connect state
 * *is* the subject — seating a job and rendering the job tile.
 *
 * Traditional (non-Connect) home tests extend [HomeScreenActivityTest] directly and should not need
 * anything here.
 */
abstract class HomeConnectTestBase : HomeScreenActivityTest() {
    /** Make [ConnectJobHelper.getJobForSeatedApp] return [job] for the seated app. */
    protected fun seatJob(job: ConnectJobRecord) {
        every { ConnectJobHelper.getJobForSeatedApp(any()) } returns job
    }

    /**
     * A lightweight [ConnectJobRecord] stub. `relaxed = true` means unspecified numeric/boolean
     * getters return 0/false, so each test only overrides the accessors it asserts on.
     */
    protected fun connectJob(
        jobUUID: String = "test-job-uuid",
        status: Int = ConnectJobRecord.STATUS_DELIVERING,
        title: String = "Test Job",
        shortDescription: String = "Job description",
        deliveryComplete: Boolean = false,
    ): ConnectJobRecord =
        mockk(relaxed = true) {
            every { this@mockk.jobUUID } returns jobUUID
            every { this@mockk.status } returns status
            every { this@mockk.title } returns title
            every { this@mockk.shortDescription } returns shortDescription
            every { deliveryComplete() } returns deliveryComplete
        }

    /**
     * Seating a job also drives the rest of setupConnectJobTile()/updateConnectJobProgress(),
     * which calls the real (unmocked) ConnectTaskUtils and ConnectDateUtils.formatDate(). Stub
     * ConnectTaskUtils so it doesn't fall through into the mocked-but-unstubbed
     * ConnectDatabaseHelper/ConnectJobUtils statics, and give getProjectEndDate() a real Date so
     * formatDate() has a non-null value to format.
     *
     * Also seats an app record: [HomeScreenActivityTest] pins `getAppRecord` to null for the
     * "nothing to show" boot, but updateConnectJobMessage() reads the record's learn/deliver
     * metadata, so a tile-rendering test needs one present.
     */
    protected fun seatJobForTileRender(job: ConnectJobRecord) {
        every { job.projectEndDate } returns Date()
        every { ConnectJobUtils.getAppRecord(any(), any()) } returns mockk(relaxed = true)
        mockkStatic(ConnectTaskUtils::class)
        every { ConnectTaskUtils.hasPendingTask(any(), any()) } returns false
        every { ConnectTaskUtils.getValidPendingOcsTask(any(), any()) } returns null
        every { ConnectTaskUtils.shouldShowTasksCompletedMessage(any(), any()) } returns false
        seatJob(job)
    }
}

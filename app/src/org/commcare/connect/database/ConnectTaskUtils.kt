package org.commcare.connect.database

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.models.database.SqlStorage
import org.commcare.preferences.ConnectJobPreferences
import org.commcare.utils.SyncDetailCalculations
import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.services.Logger
import java.util.Date

/**
 * Utility methods related to Tasks I/O
 */
object ConnectTaskUtils {
    private val TASKS_COMPLETED_MESSAGE_WINDOW_MS = DateUtils.HOUR_IN_MS * 6

    /**
     *  Upserts tasks based on if any mutable fields value have changed,
     *  deletes any no longer in incoming payload.
     */
    @JvmStatic
    fun storeTasks(
        context: Context,
        incoming: List<ConnectTaskRecord>,
        jobUUID: String,
    ) {
        val storage = ConnectDatabaseHelper.getConnectStorage(ConnectTaskRecord::class.java)
        val existing = getTasksForJob(context, jobUUID, storage)
        var changed = false

        val incomingIds = incoming.map { it.taskId }.toSet()

        for (incomingTask in incoming) {
            val match = existing.find { it.taskId == incomingTask.taskId }
            if (match == null) {
                storage.write(incomingTask)
                changed = true
            } else if (incomingTask.dateModified.after(match.dateModified)) {
                incomingTask.id = match.id
                storage.write(incomingTask)
                changed = true
            }
        }

        for (orphan in existing.filter { it.taskId !in incomingIds }) {
            storage.remove(orphan)
            changed = true
        }

        ConnectJobUtils.getJobPreferences(jobUUID).apply {
            setRelearnTaskPending(incoming.any { it.status == ConnectTaskRecord.STATUS_ASSIGNED })
            resetRelearnTasksCompletedTime()
            if (changed) {
                updateTaskModifiedTime()
            }
        }
    }

    /**
     * First looks into DB to see if there is a pending task assigned for the given [jobUUID]
     * If DB is not populated, falls back to legacy preference which should be removed after 2.64 release
     */
    @JvmStatic
    fun hasPendingTask(
        context: Context,
        jobUUID: String,
    ): Boolean {
        val tasks = getTasksForJob(context, jobUUID, null)
        if (tasks.isNotEmpty()) {
            return tasks.any { it.status == ConnectTaskRecord.STATUS_ASSIGNED }
        }
        return ConnectJobUtils.getJobPreferences(jobUUID).isRelearnTaskPending()
    }

    @JvmStatic
    fun hasPendingTaskOfMode(
        context: Context,
        jobUUID: String,
        mode: String,
    ): Boolean = getPendingTaskOfMode(context, jobUUID, mode) != null

    @JvmStatic
    fun getPendingTaskOfMode(
        context: Context,
        jobUUID: String,
        mode: String,
    ): ConnectTaskRecord? =
        getTasksForJob(context, jobUUID, null).find { it.mode == mode && it.status == ConnectTaskRecord.STATUS_ASSIGNED }

    @JvmStatic
    fun getValidPendingOcsTask(
        context: Context,
        job: ConnectJobRecord,
    ): ConnectTaskRecord? {
        if (job.status != ConnectJobRecord.STATUS_DELIVERING) return null
        val task = getPendingTaskOfMode(context, job.jobUUID, ConnectTaskRecord.MODE_OCS) ?: return null
        if (task.connectChannelId.isEmpty()) {
            Logger.exception(
                "Invalid messaging task",
                Throwable("Messaging task has no channel id: ${task.taskId}"),
            )
            return null
        }
        return task
    }

    @JvmStatic
    fun shouldShowTasksCompletedMessage(
        context: Context,
        job: ConnectJobRecord,
    ): Boolean {
        if (hasPendingTask(context, job.jobUUID)) return false
        val mostRecent = getMostRecentlyCompletedTask(context, job.jobUUID) ?: return false
        val timeElapsed = Date().time - mostRecent.dateModified.time
        return timeElapsed < TASKS_COMPLETED_MESSAGE_WINDOW_MS && job.status == ConnectJobRecord.STATUS_DELIVERING
    }

    @JvmStatic
    fun isLastTaskUpdateLaterThanLastSync(context: Context): Boolean {
        val job = ConnectJobUtils.getJobForSeatedApp(context)
        if (job == null || job.status != ConnectJobRecord.STATUS_DELIVERING) {
            return false
        }
        val prefs = ConnectJobUtils.getJobPreferences(job.jobUUID)
        val lastTaskUpdate = prefs.getTaskModifiedTime()
        if (lastTaskUpdate == ConnectJobPreferences.TIMESTAMP_NOT_SET) {
            return false
        }
        return lastTaskUpdate > SyncDetailCalculations.getLastSyncTime()
    }

    private fun getMostRecentlyCompletedTask(
        context: Context,
        jobUUID: String,
    ): ConnectTaskRecord? {
        val tasks = getTasksForJob(context, jobUUID, null)
        if (tasks.isNotEmpty()) {
            return tasks.filter { it.status == ConnectTaskRecord.STATUS_COMPLETED }.maxByOrNull { it.dateModified }
        }
        val prefs = ConnectJobUtils.getJobPreferences(jobUUID)
        val completedTimeMs = prefs.getRelearnTasksCompletedTimeMs()
        if (completedTimeMs != ConnectJobPreferences.TIMESTAMP_NOT_SET) {
            val synthetic = ConnectTaskRecord()
            synthetic.dateModified = Date(completedTimeMs)
            return synthetic
        }
        return null
    }

    private fun getTasksForJob(
        context: Context,
        jobUUID: String,
        storage: SqlStorage<ConnectTaskRecord>?,
    ): List<ConnectTaskRecord> {
        val taskStorage = storage ?: ConnectDatabaseHelper.getConnectStorage(ConnectTaskRecord::class.java)
        return taskStorage
            .getRecordsForValues(
                arrayOf(ConnectTaskRecord.META_JOB_UUID),
                arrayOf(jobUUID),
            ).toList()
    }
}

package org.commcare.connect.database

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.models.database.SqlStorage
import org.commcare.preferences.ConnectJobPreferences
import org.javarosa.core.model.utils.DateUtils
import java.util.Date
import java.util.Vector

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
    ): Boolean {
        val storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectTaskRecord::class.java)
        val existing = getTasksForJob(context, jobUUID, storage)
        var changed = false

        for (incomingTask in incoming) {
            val match = existing.find { it.taskId == incomingTask.taskId }
            if (match == null) {
                storage.write(incomingTask)
                changed = true
            } else if (match.mutableFieldsDiffer(incomingTask)) {
                match.copyMutableFieldsFrom(incomingTask)
                match.dateModified = Date()
                storage.write(match)
                changed = true
            }
        }

        val toDelete = Vector<Int>()
        for (existingTask in existing) {
            if (incoming.none { it.taskId == existingTask.taskId }) {
                toDelete.add(existingTask.getID())
                changed = true
            }
        }
        if (toDelete.isNotEmpty()) {
            storage.removeAll(toDelete)
        }

        ConnectJobUtils.getJobPreferences(jobUUID).apply {
            setRelearnTaskPending(incoming.any { it.status == "assigned" })
            resetRelearnTasksCompletedTime()
        }

        return changed
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
            return tasks.any { it.status == "assigned" }
        }
        return ConnectJobUtils.getJobPreferences(jobUUID).isRelearnTaskPending()
    }

    @JvmStatic
    fun hasPendingTaskOfType(
        context: Context,
        jobUUID: String,
        type: String,
    ): Boolean = getPendingTaskOfType(context, jobUUID, type) != null

    @JvmStatic
    fun getPendingTaskOfType(
        context: Context,
        jobUUID: String,
        type: String,
    ): ConnectTaskRecord? = getTasksForJob(context, jobUUID, null).find { it.type == type && it.status == "assigned" }

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
    private fun getMostRecentlyCompletedTask(
        context: Context,
        jobUUID: String,
    ): ConnectTaskRecord? {
        val tasks = getTasksForJob(context, jobUUID, null)
        if (tasks.isNotEmpty()) {
            return tasks.filter { it.status == "completed" }.maxByOrNull { it.dateModified }
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
        val taskStorage = storage ?: ConnectDatabaseHelper.getConnectStorage(context, ConnectTaskRecord::class.java)
        return taskStorage
            .getRecordsForValues(
                arrayOf(ConnectTaskRecord.META_JOB_UUID),
                arrayOf(jobUUID),
            ).toList()
    }
}

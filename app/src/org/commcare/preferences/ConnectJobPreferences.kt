package org.commcare.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.commcare.CommCareApplication

class ConnectJobPreferences(jobUUID: String) {
    private val prefs: SharedPreferences =
        CommCareApplication.instance().getSharedPreferences(PREF_NAME_PREFIX + jobUUID, Context.MODE_PRIVATE)

    fun isRelearnTaskPending(): Boolean = prefs.getBoolean(KEY_RELEARN_TASK_PENDING, false)

    fun setRelearnTaskPending(relearnTaskPending: Boolean) {
        prefs.edit { putBoolean(KEY_RELEARN_TASK_PENDING, relearnTaskPending) }
    }

    fun getRelearnTasksCompletedTimeMs(): Long = prefs.getLong(KEY_RELEARN_TASKS_COMPLETED_TIME_MS, -1)

    fun setRelearnTasksCompletedTime(completedTimeMs: Long) {
        prefs.edit { putLong(KEY_RELEARN_TASKS_COMPLETED_TIME_MS, completedTimeMs) }
    }

    companion object {
        private const val PREF_NAME_PREFIX = "connect_job_prefs_"
        private const val KEY_RELEARN_TASK_PENDING = "relearn_task_pending"
        private const val KEY_RELEARN_TASKS_COMPLETED_TIME_MS = "relearn_tasks_completed_time_ms"
    }
}

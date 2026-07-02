package org.commcare.preferences

import android.content.Context
import androidx.core.content.edit
import org.commcare.CommCareApplication

class ConnectJobPreferences(
    jobUUID: String,
) {
    private val prefs =
        CommCareApplication.instance().getSharedPreferences(
            PREF_NAME_PREFIX + jobUUID,
            Context.MODE_PRIVATE,
        )

    @Deprecated("Remove after 2.64 release. Task state is now persisted in ConnectTaskRecord.")
    fun isRelearnTaskPending(): Boolean = prefs.getBoolean(KEY_RELEARN_TASK_PENDING, false)

    @Deprecated("Remove after 2.64 release. Task state is now persisted in ConnectTaskRecord.")
    fun setRelearnTaskPending(relearnTaskPending: Boolean) {
        prefs.edit { putBoolean(KEY_RELEARN_TASK_PENDING, relearnTaskPending) }
    }

    @Deprecated("Remove after 2.64 release. Task state is now persisted in ConnectTaskRecord.")
    fun getRelearnTasksCompletedTimeMs(): Long =
        prefs.getLong(
            KEY_RELEARN_TASKS_COMPLETED_TIME_MS,
            TIMESTAMP_NOT_SET,
        )

    @Deprecated("Remove after 2.64 release. Task state is now persisted in ConnectTaskRecord.")
    fun setRelearnTasksCompletedTime(completedTimeMs: Long) {
        prefs.edit { putLong(KEY_RELEARN_TASKS_COMPLETED_TIME_MS, completedTimeMs) }
    }

    @Deprecated("Remove after 2.64 release. Task state is now persisted in ConnectTaskRecord.")
    fun resetRelearnTasksCompletedTime() {
        setRelearnTasksCompletedTime(TIMESTAMP_NOT_SET)
    }

    fun getPaymentConfirmationHiddenSinceTime(): Long =
        prefs.getLong(
            PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME,
            TIMESTAMP_NOT_SET,
        )

    fun paymentConfirmationHiddenSinceTimeNotSet(): Boolean = getPaymentConfirmationHiddenSinceTime() == TIMESTAMP_NOT_SET

    fun setPaymentConfirmationHiddenSinceTime(hiddenSinceTimeMs: Long) {
        prefs.edit { putLong(PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME, hiddenSinceTimeMs) }
    }

    fun resetPaymentConfirmationHiddenSinceTime() {
        setPaymentConfirmationHiddenSinceTime(TIMESTAMP_NOT_SET)
    }

    companion object {
        private const val PREF_NAME_PREFIX = "connect_job_prefs_"
        private const val KEY_RELEARN_TASK_PENDING = "relearn_task_pending"
        private const val KEY_RELEARN_TASKS_COMPLETED_TIME_MS = "relearn_tasks_completed_time_ms"
        private const val PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME =
            "payment_confirmation_hidden_since_time"
        const val TIMESTAMP_NOT_SET = -1L
        private const val KEY_LAST_TASK_UPDATE_TIME_MS = "last_task_update_time_ms"
    }
}

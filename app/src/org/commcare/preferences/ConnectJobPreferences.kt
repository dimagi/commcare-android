package org.commcare.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.commcare.CommCareApplication

class ConnectJobPreferences(jobUUID: String) {
    private val prefs = CommCareApplication.instance().getSharedPreferences(PREF_NAME_PREFIX + jobUUID, Context.MODE_PRIVATE)

    fun isRelearnTaskPending(): Boolean = prefs.getBoolean(KEY_RELEARN_TASK_PENDING, false)

    fun setRelearnTaskPending(relearnTaskPending: Boolean) {
        prefs.edit { putBoolean(KEY_RELEARN_TASK_PENDING, relearnTaskPending) }
    }

    fun getRelearnTasksCompletedTimeMs(): Long = prefs.getLong(KEY_RELEARN_TASKS_COMPLETED_TIME_MS, RELEARN_TASKS_COMPLETED_TIME_NOT_SET)

    fun relearnTasksCompletedTimeNotSet(): Boolean = getRelearnTasksCompletedTimeMs() == RELEARN_TASKS_COMPLETED_TIME_NOT_SET

    fun setRelearnTasksCompletedTime(completedTimeMs: Long) {
        prefs.edit { putLong(KEY_RELEARN_TASKS_COMPLETED_TIME_MS, completedTimeMs) }
    }

    fun resetRelearnTasksCompletedTime() {
        setRelearnTasksCompletedTime(RELEARN_TASKS_COMPLETED_TIME_NOT_SET)
    }

    fun getPaymentConfirmationHiddenSinceTime(): Long = prefs.getLong(PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME, PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME_NOT_SET)

    fun paymentConfirmationHiddenSinceTimeNotSet(): Boolean = getPaymentConfirmationHiddenSinceTime() == PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME_NOT_SET

    fun setPaymentConfirmationHiddenSinceTime(hiddenSinceTimeMs: Long) {
        prefs.edit { putLong(PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME, hiddenSinceTimeMs) }
    }

    fun resetPaymentConfirmationHiddenSinceTime() {
        setPaymentConfirmationHiddenSinceTime(PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME_NOT_SET)
    }

    companion object {
        private const val PREF_NAME_PREFIX = "connect_job_prefs_"
        private const val KEY_RELEARN_TASK_PENDING = "relearn_task_pending"
        private const val KEY_RELEARN_TASKS_COMPLETED_TIME_MS = "relearn_tasks_completed_time_ms"
        private const val RELEARN_TASKS_COMPLETED_TIME_NOT_SET = -1L
        private const val PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME = "payment_confirmation_hidden_since_time"
        private const val PAYMENT_CONFIRMATION_HIDDEN_SINCE_TIME_NOT_SET = -1L
    }
}

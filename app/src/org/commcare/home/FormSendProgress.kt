package org.commcare.home

import org.commcare.sync.FormSubmissionHelper

/**
 * Reads a form send's raw progress updates into how many forms are sent and how far the send is.
 */
class FormSendProgress {
    /** How far a send is: [sent] of [total] forms, and [percent] of the way through. */
    data class State(
        val sent: Int,
        val total: Int,
        val percent: Int,
    )

    private var total = 0
    private var currentItemSize = 0L

    /** The send's state after [update], or null when the update doesn't move it. */
    fun onUpdate(update: Array<out Long?>): State? {
        val code = update.getOrNull(0) ?: return null
        return when (code) {
            FormSubmissionHelper.SUBMISSION_BEGIN -> {
                total = update.intAt(1)
                state(sent = 0, fraction = 0.0)
            }

            FormSubmissionHelper.SUBMISSION_START -> {
                currentItemSize = update.getOrNull(2) ?: 0L
                state(sent = update.intAt(1), fraction = 0.0)
            }

            FormSubmissionHelper.SUBMISSION_NOTIFY -> {
                val bytesSent = update.getOrNull(2) ?: 0L
                val fraction = if (currentItemSize > 0) bytesSent.toDouble() / currentItemSize else 0.0
                state(sent = update.intAt(1), fraction = fraction.coerceIn(0.0, 1.0))
            }

            FormSubmissionHelper.SUBMISSION_DONE -> {
                if (update.getOrNull(1) == FormSubmissionHelper.SUBMISSION_SUCCESS) state(sent = total, fraction = 0.0) else null
            }

            else -> {
                null
            }
        }
    }

    private fun state(
        sent: Int,
        fraction: Double,
    ): State? {
        if (total <= 0) {
            return null
        }
        val done = sent.coerceIn(0, total)
        val percent = (((done + fraction) / total) * 100).toInt().coerceIn(0, 100)
        return State(done, total, percent)
    }

    private fun Array<out Long?>.intAt(index: Int): Int = getOrNull(index)?.toInt() ?: 0
}

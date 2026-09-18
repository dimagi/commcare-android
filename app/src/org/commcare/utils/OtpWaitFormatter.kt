package org.commcare.utils

import android.content.Context
import org.commcare.dalvik.R

/**
 * Renders an OTP resend wait at the coarsest unit that still describes it. The wait doubles with
 * every resend, and jumps to hours once the server has thrown a code away after too many wrong
 * guesses, so the same value can be anything from seconds to the server's four-hour ceiling.
 */
object OtpWaitFormatter {
    @JvmStatic
    fun format(
        context: Context,
        waitSeconds: Int,
    ): String {
        if (waitSeconds < SECONDS_PER_MINUTE) {
            return getFormattedQuantity(context, R.plurals.personalid_otp_retry_after_seconds, waitSeconds.coerceAtLeast(1))
        }
        val minutes = roundUpToWholeUnits(waitSeconds, SECONDS_PER_MINUTE)
        if (minutes < MINUTES_PER_HOUR) {
            return getFormattedQuantity(context, R.plurals.personalid_otp_retry_after_minutes, minutes)
        }
        return getFormattedQuantity(
            context,
            R.plurals.personalid_otp_retry_after_hours,
            roundUpToWholeUnits(minutes, MINUTES_PER_HOUR),
        )
    }

    private fun getFormattedQuantity(
        context: Context,
        pluralsResId: Int,
        quantity: Int,
    ): String = context.resources.getQuantityString(pluralsResId, quantity, quantity)

    private fun roundUpToWholeUnits(
        amount: Int,
        unitSize: Int,
    ): Int = (amount + unitSize - 1) / unitSize

    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60
}

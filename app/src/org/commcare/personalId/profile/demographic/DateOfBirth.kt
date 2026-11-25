package org.commcare.personalId.profile.demographic

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date of birth is stored as a plain ISO `yyyy-MM-dd` string, with no time or timezone.
 *
 * Milliseconds appear only because `MaterialDatePicker` takes and returns the day as midnight UTC in
 * millis. Converting those millis in the device's timezone could land on the previous or next day, so
 * every conversion here is done in UTC.
 */
object DateOfBirth {
    private const val STORED_PATTERN = "yyyy-MM-dd"

    fun fromUtcMillis(utcMillis: Long): String = storedFormat().format(Date(utcMillis))

    fun toUtcMillis(stored: String?): Long? =
        try {
            stored?.let { storedFormat().parse(it)?.time }
        } catch (e: ParseException) {
            null
        }

    fun formatDate(
        stored: String?,
        locale: Locale = Locale.getDefault(),
    ): String? =
        toUtcMillis(stored)?.let {
            DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = UTC }.format(Date(it))
        }

    private fun storedFormat() = SimpleDateFormat(STORED_PATTERN, Locale.ROOT).apply { timeZone = UTC }

    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
}

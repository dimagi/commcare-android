package org.commcare.connect

import android.content.Context
import org.commcare.dalvik.R
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ConnectDateUtils {

    @JvmStatic
    fun formatDate(date: Date?): String {
        val formatter = DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            Locale.getDefault()
        )
        return formatter.format(date)
    }

    fun paymentDateFormat(date: Date?): String {
        val formatter = DateFormat.getDateInstance(
            DateFormat.LONG,
            Locale.getDefault()
        )
        return formatter.format(date)
    }

    fun formatDateTime(date: Date?): String {
        return DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            Locale.getDefault()
        ).format(date)
    }

    @Throws(ParseException::class)
    fun convertIsoDate(
        inputDate: String,
        outputStyle: Int = DateFormat.MEDIUM
    ): String {
        require(inputDate.isNotEmpty()) { "Input date string is empty" }

        val cleaned = inputDate
            .replace(Regex("\\.\\d{1,6}"), "")
            .replace("+00:00", "Z")

        val isoFormat = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val date =
            isoFormat.parse(cleaned)
                ?: throw ParseException("Failed to parse ISO date: $cleaned", 0)

        val outputFormat = DateFormat.getDateInstance(
            outputStyle,
            Locale.getDefault()
        )

        return outputFormat.format(date)
    }

    fun parseIsoDateForSorting(dateStr: String): Date? {
        return try {
            if (dateStr.isEmpty()) return null

            val isoFormat = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.US
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            isoFormat.parse(dateStr)
        } catch (_: ParseException) {
            null
        }
    }

    fun formatNotificationTime(
        context: Context,
        date: Date,
    ): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = date }

        val diffMillis = now.timeInMillis - then.timeInMillis
        val minutes = diffMillis / (1000 * 60)
        val hours = diffMillis / (1000 * 60 * 60)
        val days = diffMillis / (1000 * 60 * 60 * 24)

        return when {
            minutes < 1 -> context.getString(R.string.just_now)
            minutes < 60 -> context.getString(R.string.minutes_ago, minutes.toInt())
            hours < 24 -> context.getString(R.string.hours_ago, hours.toInt())
            days < 2 -> {
                val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT,Locale.getDefault())
                context.getString(R.string.yesterday, timeFormat.format(date))
            }
            days <= 7 -> {
                DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, Locale.getDefault()).format(date)
            }
            else -> {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(date)
            }
        }
    }
}

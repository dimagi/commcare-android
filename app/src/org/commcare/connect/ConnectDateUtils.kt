package org.commcare.connect

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ConnectDateUtils {
    val dateFormat: DateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

    fun formatDate(date: Date?): String {
        synchronized(dateFormat) {
            return dateFormat.format(date)
        }
    }

    val paymentDateFormat: DateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())

    fun paymentDateFormat(date: Date?): String {
        synchronized(paymentDateFormat) {
            return paymentDateFormat.format(date)
        }
    }

    fun formatDateTime(date: Date?): String {
        return SimpleDateFormat.getDateTimeInstance().format(date)
    }

    @Throws(ParseException::class)
    fun convertIsoDate(inputDate: String, outputPattern: String): String {
        require(inputDate.isNotEmpty()) { "Input date string is empty" }

        val cleaned = inputDate
            .replace(Regex("\\.\\d{1,6}"), "")
            .replace("+00:00", "Z")

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val date = isoFormat.parse(cleaned)
            ?: throw ParseException("Failed to parse ISO date: $cleaned", 0)

        val outputFormat = SimpleDateFormat(outputPattern, Locale.US)
        return outputFormat.format(date)
    }

    fun parseIsoDateForSorting(dateStr: String): Date? {
        return try {
            if (dateStr.isEmpty()) return null
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")
            isoFormat.parse(dateStr)
        } catch (e: ParseException) {
            null
        }
    }
}
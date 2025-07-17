package org.commcare.utils

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Throws(ParseException::class)
fun convertIsoDate(inputDate: String, outputPattern: String): String {
    require(inputDate.isNotEmpty()) { "Input date string is null or empty" }

    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    isoFormat.timeZone = TimeZone.getTimeZone("UTC")
    val date = isoFormat.parse(inputDate)

    val outputFormat = SimpleDateFormat(outputPattern, Locale.US)
    return outputFormat.format(date!!)
}

fun parseIsoDateForSorting(dateStr: String): Date? {
    return try {
        if (dateStr.isEmpty()) return null
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
        isoFormat.parse(dateStr)
    } catch (e: Exception) {
        null
    }
}

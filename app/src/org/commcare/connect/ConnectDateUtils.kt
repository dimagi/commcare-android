package org.commcare.connect

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
}
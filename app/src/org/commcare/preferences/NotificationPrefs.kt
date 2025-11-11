package org.commcare.preferences

import android.content.Context
import androidx.core.content.edit

object NotificationPrefs {
    private const val PREF_NAME = "notification_prefs"
    private const val KEY_NOTIFICATION_READ_STATUS = "notification_read_status"

    fun setNotificationAsUnread(context: Context) {
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_NOTIFICATION_READ_STATUS, false) }
    }

    fun setNotificationAsRead(context: Context) {
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_NOTIFICATION_READ_STATUS, true) }
    }

    fun getNotificationReadStatus(context: Context): Boolean =
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATION_READ_STATUS, true)

    fun removeNotificationReadPref(context: Context) {
        context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { remove(PREF_NAME) }
    }
}

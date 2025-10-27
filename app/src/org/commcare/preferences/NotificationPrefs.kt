package org.commcare.preferences

import android.content.Context
import androidx.core.content.edit

object NotificationPrefs {
    private const val PREF_NAME = "notification_prefs"
    private const val KEY_PN_READ_STATUS = "pn_read_status"

    fun setNotificationAsUnread(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PN_READ_STATUS, false) }
    }

    fun setNotificationAsRead(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_PN_READ_STATUS, true) }
    }

    fun getNotificationReadStatus(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PN_READ_STATUS, true)
    }
}
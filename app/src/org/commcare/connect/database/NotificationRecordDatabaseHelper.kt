package org.commcare.connect.database

import android.content.Context
import org.commcare.android.database.connect.models.PushNotificationRecord

class NotificationRecordDatabaseHelper {
    fun getAllNotifications(context: Context): List<PushNotificationRecord> {
        return ConnectDatabaseHelper
            .getConnectStorage(context, PushNotificationRecord::class.java)
            .getRecordsForValues(arrayOf(), arrayOf())
    }
}
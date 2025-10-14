package org.commcare.connect.database

import android.content.Context
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.models.database.SqlStorage

class NotificationRecordDatabaseHelper {

    private fun getStorage(context: Context): SqlStorage<PushNotificationRecord> {
        return ConnectDatabaseHelper.getConnectStorage(context, PushNotificationRecord::class.java)
    }

    /**
     * Fetch all notifications
     */
    fun getAllNotifications(context: Context): List<PushNotificationRecord>? {
        return getStorage(context).getRecordsForValues(arrayOf(), arrayOf())
    }

    /**
     * Fetch a notification from notification_id
     */
    fun getNotificationById(context: Context, notificationId: String): PushNotificationRecord? {
        val records = getStorage(context).getRecordsForValues(
            arrayOf(PushNotificationRecord.META_NOTIFICATION_ID),
            arrayOf(notificationId)
        )
        return records.firstOrNull()
    }

    /**
     * Update the read status for a notification using notification_id
     */
    fun updateReadStatus(context: Context, notificationId: String, isRead: Boolean) {
        val record = getNotificationById(context, notificationId) ?: return
        record.readStatus = isRead
        getStorage(context).write(record)
    }

    /**
     * Append notification(s) to DB (insert or update)
     */
    fun storeNotifications(
        context: Context,
        notifications: List<PushNotificationRecord>,
    ) {
        val storage = getStorage(context)

        for (incoming in notifications) {
            getNotificationById(context, incoming.notificationId)?.let { existing ->
                incoming.id = existing.id
            }
            storage.write(incoming)
        }
    }
}
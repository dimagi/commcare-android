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
     * Returns list of notificationIds successfully persisted
     */
    fun storeNotifications(
        context: Context,
        notifications: List<PushNotificationRecord>,
    ): List<String> {
        val storage = getStorage(context)
        val savedNotificationIds = mutableListOf<String>()

        for (incoming in notifications) {
            getNotificationById(context, incoming.notificationId)?.let { existing ->
                incoming.id = existing.id
            }
            storage.write(incoming)

            // Verify persistence
            val savedRecord = getNotificationById(context, incoming.notificationId)
            if (savedRecord != null) {
                savedNotificationIds.add(incoming.notificationId)
            }
        }

        return savedNotificationIds
    }

    /**
     * Update a single column/field for multiple notifications
     * @param notificationIds List of notification IDs to update
     * @param updateAction Lambda to update a field of PushNotificationRecord
     */
    fun updateColumnForNotifications(
        context: Context,
        notificationIds: List<String>,
        updateAction: (PushNotificationRecord) -> Unit
    ) {
        val storage = getStorage(context)

        for (id in notificationIds) {
            val record = getNotificationById(context, id)
            if (record != null) {
                updateAction(record)
                storage.write(record)
            }
        }
    }
}
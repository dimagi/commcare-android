package org.commcare.connect.database

import android.content.Context
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.models.database.SqlStorage

object NotificationRecordDatabaseHelper {
    private fun getStorage(context: Context): SqlStorage<PushNotificationRecord> =
        ConnectDatabaseHelper.getConnectStorage(context, PushNotificationRecord::class.java)

    /**
     * Fetch all notifications
     */
    fun getAllNotifications(context: Context): List<PushNotificationRecord>? = getStorage(context).getRecordsForValues(arrayOf(), arrayOf())

    /**
     * Fetch a notification from notification_id
     */
    fun getNotificationById(
        context: Context,
        notificationId: String,
    ): PushNotificationRecord? {
        val records =
            getStorage(context).getRecordsForValues(
                arrayOf(PushNotificationRecord.META_NOTIFICATION_ID),
                arrayOf(notificationId),
            )
        return records.firstOrNull()
    }

    /**
     * Update the read status for a notification using notification_id
     */
    fun updateReadStatus(
        context: Context,
        notificationId: String,
        isRead: Boolean,
    ) {
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
            val existing = getNotificationById(context, incoming.notificationId)

            // Skip if record already exists
            if (existing != null) {
                savedNotificationIds.add(existing.notificationId)
                continue
            }

            // Otherwise, write new record
            storage.write(incoming)
            FirebaseAnalyticsUtil.reportNotificationEvent(
                AnalyticsParamValue.NOTIFICATION_EVENT_TYPE_RECEIVED,
                AnalyticsParamValue.REPORT_NOTIFICATION_METHOD_PERSONAL_ID_API,
                incoming.action,
                incoming.notificationId,
            )

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
        updateAction: (PushNotificationRecord) -> Unit,
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

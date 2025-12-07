package org.commcare.connect.services

import android.content.Context
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.connect.network.connectId.parser.NotificationParseResult
import org.commcare.utils.PushNotificationApiHelper

/**
 * Service class to handle notification data processing and database operations
 */
object NotificationService {

    /**
     * Processes parsed notification data into the DB
     * @param context Android context
     * @param parseResult Result from parsing notification response
     * @return ProcessedNotificationResult containing all processing results
     */
    fun processNotificationData(
        context: Context, 
        parseResult: NotificationParseResult
    ): ProcessedNotificationResult {
        if (parseResult.channels.isNotEmpty()) {
            ConnectMessagingDatabaseHelper.storeMessagingChannels(context, parseResult.channels, true)
        }

        if (parseResult.messages.isNotEmpty()) {
            ConnectMessagingDatabaseHelper.storeMessagingMessages(context, parseResult.messages, false)
        }

        val messagingNotificationIds = parseResult.notifications
            .filter { it.notificationType == PushNotificationApiHelper.NOTIFICATION_TYPE_MESSAGING }
            .map { it.notificationId }
        
        val nonMessagingNotifications = parseResult.notifications
            .filter { it.notificationType != PushNotificationApiHelper.NOTIFICATION_TYPE_MESSAGING }

        val savedNotificationIds = if (nonMessagingNotifications.isNotEmpty()) {
            NotificationRecordDatabaseHelper.storeNotifications(context, nonMessagingNotifications)
        } else {
            emptyList()
        }
            
        return ProcessedNotificationResult(
            savedNotifications = nonMessagingNotifications,
            messagingNotificationIds = messagingNotificationIds,
            savedNotificationIds = savedNotificationIds
        )
    }
}

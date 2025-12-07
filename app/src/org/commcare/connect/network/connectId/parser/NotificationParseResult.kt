package org.commcare.connect.network.connectId.parser

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.database.connect.models.PushNotificationRecord

/**
 * Data class to hold the result of parsing notification response
 * Contains three separate, exclusive entities: notifications, channels, and messages
 */
data class NotificationParseResult(
    val notifications: List<PushNotificationRecord>,
    val channels: List<ConnectMessagingChannelRecord>,
    val messages: List<ConnectMessagingMessageRecord>
)
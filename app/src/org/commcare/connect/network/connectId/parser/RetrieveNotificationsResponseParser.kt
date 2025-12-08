package org.commcare.connect.network.connectId.parser

import android.content.Context
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_NOTIFICATION_ID
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.network.base.BaseApiResponseParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Parser for retrieve_notification API endpoint
 * Parses JSON response into separate notifications, channels, and messages
 */
class RetrieveNotificationsResponseParser(
    private val context: Context
) : BaseApiResponseParser<NotificationParseResult> {

    private var notificationsJsonArray: JSONArray = JSONArray()

    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): NotificationParseResult {
        val jsonText = responseData.bufferedReader().use { it.readText() }
        val responseJsonObject = JSONObject(jsonText)

        if (responseJsonObject.has("notifications")) {
            notificationsJsonArray = responseJsonObject.getJSONArray("notifications")
        }
        
        val channels = parseChannels(responseJsonObject)
        val (nonMessageNotifications, messages, messagesNotificationsIds) = parseAndSeparateNotifications()
        
        return NotificationParseResult(
            nonMessageNotifications,
            channels,
            messages,
            messagesNotificationsIds
        )
    }

    private fun parseChannels(responseJsonObject: JSONObject): MutableList<ConnectMessagingChannelRecord> {
        val channels: MutableList<ConnectMessagingChannelRecord> = ArrayList()
        if (responseJsonObject.has("channels")) {
            val channelsJson: JSONArray = responseJsonObject.getJSONArray("channels")
            for (i in 0 until channelsJson.length()) {
                val obj = channelsJson.get(i) as JSONObject
                val channel = ConnectMessagingChannelRecord.fromJson(obj)
                channels.add(channel)
            }
        }
        return channels
    }

    /**
     * Parses and separates notifications into two categories in a single pass:
     * - Non-messaging notifications as PushNotificationRecord
     * - Messaging notifications as ConnectMessagingMessageRecord
     * This avoids double parsing and eliminates filtering overhead
     */
    private fun parseAndSeparateNotifications(): Triple<MutableList<PushNotificationRecord>, MutableList<ConnectMessagingMessageRecord>, MutableList<String>> {
        val nonMessageNotifications = mutableListOf<PushNotificationRecord>()
        val messages = mutableListOf<ConnectMessagingMessageRecord>()
        val messagesNotificationsIds = mutableListOf<String>()

        notificationsJsonArray.let { jsonArray ->
            // Get existing channels from database - these have the encryption keys required for message decryption
            val existingChannels = ConnectMessagingDatabaseHelper.getMessagingChannels(context)
            for (notificationIndex in 0 until jsonArray.length()) {
                val notificationJsonObject = jsonArray.getJSONObject(notificationIndex)

                if (isNotificationMessageType(notificationJsonObject)) {
                    // Handle messaging notifications - parse as ConnectMessagingMessageRecord
                    val message = ConnectMessagingMessageRecord.fromJson(
                        notificationJsonObject,
                        existingChannels
                    )
                    if (message != null) {
                        messages.add(message)
                        messagesNotificationsIds.add(notificationJsonObject.getString(META_NOTIFICATION_ID))
                    }
                } else {
                    // Handle non-messaging notifications
                    val notification = PushNotificationRecord.fromJson(notificationJsonObject)
                    nonMessageNotifications.add(notification)
                }
            }
        }
        
        return Triple(nonMessageNotifications, messages, messagesNotificationsIds)
    }

    /**
     * Checks if a notification JSON object is of messaging type
     */
    private fun isNotificationMessageType(notificationJsonObject: JSONObject): Boolean {
        return notificationJsonObject.has("notification_type") &&
            "MESSAGING" == notificationJsonObject.getString("notification_type")
    }
}

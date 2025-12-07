package org.commcare.connect.network.connectId.parser

import android.content.Context
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.database.connect.models.PushNotificationRecord
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

    private var notificationsJsonArray: JSONArray? = null

    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): NotificationParseResult {
        val jsonText = responseData.bufferedReader().use { it.readText() }
        val responseJsonObject = JSONObject(jsonText)
        val notifications = parseNotifications(responseJsonObject)
        val channels = parseChannels(responseJsonObject)
        val messages = parseMessages()
        
        return NotificationParseResult(
            notifications,
            channels,
            messages
        )
    }

    private fun parseNotifications(responseJsonObject: JSONObject): List<PushNotificationRecord> {
        if (responseJsonObject.has("notifications")) {
            notificationsJsonArray = responseJsonObject.getJSONArray("notifications")
            return PushNotificationRecord.fromJsonArray(notificationsJsonArray ?: JSONArray())
        }
        return emptyList()
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
     * Parses messages from messaging notifications using database channels with keys
     */
    private fun parseMessages(): List<ConnectMessagingMessageRecord> {
        val messages = mutableListOf<ConnectMessagingMessageRecord>()
        notificationsJsonArray?.let { jsonArray ->
            // Get existing channels from database - these have the encryption keys populated required to decrypt message payload
            val existingChannels = ConnectMessagingDatabaseHelper.getMessagingChannels(context)
            
            for (notificationIndex in 0 until jsonArray.length()) {
                val notificationJsonObject = jsonArray.getJSONObject(notificationIndex)
                if (isNotificationMessageType(notificationJsonObject)) {
                        val message = ConnectMessagingMessageRecord.fromJson(
                            notificationJsonObject,
                            existingChannels
                        )
                        if (message != null) {
                            messages.add(message)
                        }
                }
            }
        }
        return messages
    }

    /**
     * Checks if a notification JSON object is of messaging type
     */
    private fun isNotificationMessageType(notificationJsonObject: JSONObject): Boolean {
        return notificationJsonObject.has("notification_type") &&
            "MESSAGING" == notificationJsonObject.getString("notification_type")
    }
}

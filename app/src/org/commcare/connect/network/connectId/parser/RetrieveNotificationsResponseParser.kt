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
 * Parser for retrieving notification response
 */
class RetrieveNotificationsResponseParser<T>(
    val context: Context,
) : BaseApiResponseParser<T> {
    val channels: MutableList<ConnectMessagingChannelRecord> = ArrayList()
    val messages: MutableList<ConnectMessagingMessageRecord?> = ArrayList()
    var notificationsJsonArray: JSONArray? = null

    override fun parse(
        responseCode: Int,
        responseData: InputStream,
        anyInputObject: Any?,
    ): T {
        val jsonText = responseData.bufferedReader().use { it.readText() }
        val responseJsonObject = JSONObject(jsonText)
        parseNotifications(responseJsonObject)
        parseChannel(responseJsonObject)
        parseMessages()
        return PushNotificationRecord.fromJsonArray(notificationsJsonArray ?: JSONArray()) as T
    }

    private fun parseNotifications(responseJsonObject: JSONObject) {
        if (responseJsonObject.has("notifications")) {
            notificationsJsonArray = responseJsonObject.getJSONArray("notifications")
        }
    }

    private fun parseChannel(responseJsonObject: JSONObject) {
        if (responseJsonObject.has("channels")) {
            val channelsJson: JSONArray = responseJsonObject.getJSONArray("channels")
            for (i in 0 until channelsJson.length()) {
                val obj = channelsJson.get(i) as JSONObject
                val channel = ConnectMessagingChannelRecord.fromJson(obj)
                channels.add(channel)
            }
            ConnectMessagingDatabaseHelper.storeMessagingChannels(context, channels, true)
            for (channel in channels) {
                // if there is no key for channel, remove that messages for PN so that it can be retrieved back again by calling API
                if (channel.getConsented() && channel.getKey().length == 0) {
                    excludeMessagesForChannel(channel.channelId)
                }
            }
        }
    }

    private fun parseMessages() {
        notificationsJsonArray?.let {
            val existingChannels = ConnectMessagingDatabaseHelper.getMessagingChannels(context)
            for (notificationJsonIndex in 0 until notificationsJsonArray!!.length()) {
                val notificationJsonObject =
                    notificationsJsonArray!!.getJSONObject(notificationJsonIndex)
                if (isNotificationMessageType(notificationJsonObject) &&
                    notificationJsonObject.getJSONObject("data").has("message_id")
                ) {
                    val message =
                        ConnectMessagingMessageRecord.fromJson(
                            notificationJsonObject.getJSONObject("data"),
                            existingChannels,
                        )
                    if (message != null) {
                        messages.add(message)
                    }
                }
            }
            ConnectMessagingDatabaseHelper.storeMessagingMessages(context, messages, false)
        }
    }

    private fun excludeMessagesForChannel(channelId: String) {
        val newNotificationsJsonArray = JSONArray()
        notificationsJsonArray?.let {
            for (notificationJsonIndex in 0 until it.length()) {
                val notificationJsonObject =
                    notificationsJsonArray!!.getJSONObject(notificationJsonIndex)
                if (!shouldRemoveChannel(notificationJsonObject, channelId)) {
                    newNotificationsJsonArray.put(notificationJsonObject)
                }
            }
        }
        notificationsJsonArray = newNotificationsJsonArray
    }

    private fun shouldRemoveChannel(
        notificationJsonObject: JSONObject,
        channelId: String,
    ): Boolean =
        isNotificationMessageType(notificationJsonObject) && notificationJsonObject
            .getJSONObject("data")
            .get("channel") != null &&
            channelId.equals(
                notificationJsonObject.getJSONObject("data").get("channel"),
            )

    private fun isNotificationMessageType(notificationJsonObject: JSONObject) =
        notificationJsonObject.has("notification_type") &&
            "MESSAGING".equals(
                notificationJsonObject.get(
                    "notification_type",
                ),
            ) && notificationJsonObject.has("data") && notificationJsonObject.getJSONObject("data") != null
}

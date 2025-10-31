package org.commcare.connect.network.connectId.parser

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.MessageManager
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.network.base.BaseApiResponseParser
import org.commcare.pn.workermanager.NotificationsSyncWorkerManager.Companion.scheduleImmediatePushNotificationRetrieval
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Parser for retrieving notification response
 */
class RetrieveNotificationsResponseParser<T>(val context: Context) : BaseApiResponseParser<T> {

    val channels: MutableList<ConnectMessagingChannelRecord> = ArrayList()
    val messages: MutableList<ConnectMessagingMessageRecord?> = ArrayList()
    var notificationsJsonArray: JSONArray? = null

    override fun parse(responseCode: Int, responseData: InputStream, anyInputObject: Any?): T {
        val jsonText = responseData.bufferedReader().use { it.readText() }
        val responseJsonObject = JSONObject(jsonText)

        if (responseJsonObject.has("notifications")) {
            notificationsJsonArray = responseJsonObject.getJSONArray("notifications")
        }

        var needReloadDueToMissingChannelKey = false
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
                    needReloadDueToMissingChannelKey = true
                    excludeMessagesForChannel(channel.channelId)
                    MessageManager.getChannelEncryptionKey(context, channel, null)
                }
            }
        }

        notificationsJsonArray?.let {
            val existingChannels = ConnectMessagingDatabaseHelper.getMessagingChannels(context)
            for (notificationJsonIndex in 0 until notificationsJsonArray!!.length()) {
                val notificationJsonObject =
                    notificationsJsonArray!!.getJSONObject(notificationJsonIndex)
                if (pushNotificationIsMessageType(notificationJsonObject) &&
                    notificationJsonObject.getJSONObject("data").has("message_id")
                ) {
                    val message = ConnectMessagingMessageRecord.fromJson(
                        notificationJsonObject.getJSONObject("data"),
                        existingChannels
                    )
                    if (message != null) {
                        messages.add(message)
                    }
                }
            }
            ConnectMessagingDatabaseHelper.storeMessagingMessages(context, messages, false)
        }

        if (needReloadDueToMissingChannelKey) {
            Handler(Looper.getMainLooper()).postDelayed({
                scheduleImmediatePushNotificationRetrieval(CommCareApplication.instance())
            }, 3000)
        }

        val result = PushNotificationRecord.fromJsonArray(notificationsJsonArray ?: JSONArray())
        return result as T
    }

    private fun excludeMessagesForChannel(channelId: String) {
        val newNotificationsJsonArray = JSONArray()
        notificationsJsonArray?.let {
            for (notificationJsonIndex in 0 until notificationsJsonArray!!.length()) {
                var removeThisObject = false
                val notificationJsonObject =
                    notificationsJsonArray!!.getJSONObject(notificationJsonIndex)
                if (pushNotificationIsMessageType(notificationJsonObject) &&
                    notificationJsonObject.getJSONObject("data").get("channel") != null &&
                    notificationJsonObject.getJSONObject("data").get("channel").equals(channelId)
                ) {
                    removeThisObject = true
                }
                if (!removeThisObject) {
                    newNotificationsJsonArray.put(notificationJsonObject)
                }
            }
        }
        notificationsJsonArray = newNotificationsJsonArray
    }

    private fun pushNotificationIsMessageType(notificationJsonObject: JSONObject) =
        notificationJsonObject.has("notification_type") && "MESSAGING".equals(
            notificationJsonObject.get(
                "notification_type"
            )
        ) && notificationJsonObject.has("data") && notificationJsonObject.getJSONObject("data") != null
}

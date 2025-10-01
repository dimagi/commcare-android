package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.javarosa.core.services.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable

@Table(PushNotificationRecord.STORAGE_KEY)
class PushNotificationRecord : Persisted(), Serializable {

    @Persisting(1)
    @MetaField(META_NOTIFICATION_ID)
    var notificationId: String = ""

    @Persisting(2)
    @MetaField(META_NOTIFICATION_TYPE)
    var notificationType: String = ""

    @Persisting(3)
    @MetaField(META_ACTION)
    var action: String = ""

    @Persisting(4)
    @MetaField(META_TITLE)
    var title: String = ""

    @Persisting(5)
    @MetaField(META_BODY)
    var body: String = ""

    @Persisting(6)
    @MetaField(META_TIME_STAMP)
    var timeStamp: String = ""

    @Persisting(7)
    @MetaField(META_CONFIRMATION_STATUS)
    var confirmationStatus: String = ""

    @Persisting(8)
    @MetaField(META_OPPORTUNITY_ID)
    var opportunityId: String = ""

    @Persisting(9)
    @MetaField(META_MESSAGE_ID)
    var connectMessageId: String = ""

    @Persisting(10)
    @MetaField(META_CHANNEL)
    var channel: String = ""

    @Persisting(11)
    @MetaField(META_PAYMENT_ID)
    var paymentId: String = ""

    @Persisting(12)
    @MetaField(META_READ_STATUS)
    var readStatus: Boolean = false

    companion object {
        const val STORAGE_KEY = "push_notification_history"

        const val META_NOTIFICATION_ID = "notification_id"
        const val META_NOTIFICATION_TYPE = "notification_type"
        const val META_ACTION = "action"
        const val META_TITLE = "title"
        const val META_BODY = "body"
        const val META_TIME_STAMP = "timestamp"
        const val META_CONFIRMATION_STATUS = "confirmation_status"
        const val META_OPPORTUNITY_ID = "opportunity_id"
        const val META_MESSAGE_ID = "message_id"
        const val META_CHANNEL = "channel"
        const val META_PAYMENT_ID = "payment_id"
        const val META_READ_STATUS = "read_status"

        fun fromJsonArray(jsonArray: JSONArray): List<PushNotificationRecord> {
            val records = mutableListOf<PushNotificationRecord>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val record = PushNotificationRecord().apply {
                        notificationId = getRequiredString(obj, META_NOTIFICATION_ID, i)
                        title = getRequiredString(obj, META_TITLE, i)
                        body = getRequiredString(obj, META_BODY, i)
                        notificationType = obj.optString(META_NOTIFICATION_TYPE, "")
                        confirmationStatus = obj.optString(META_CONFIRMATION_STATUS, "")
                        paymentId = obj.optString(META_PAYMENT_ID, "")
                        readStatus = obj.optBoolean(META_READ_STATUS, false)
                        timeStamp = obj.optString(META_TIME_STAMP, "")

                        val dataObj = obj.optJSONObject("data")
                        dataObj?.let {
                            connectMessageId = it.optString(META_MESSAGE_ID, "")
                            channel = it.optString(META_CHANNEL, "")
                            action = getRequiredString(it, META_ACTION, i)
                            opportunityId = it.optString(META_OPPORTUNITY_ID, "")
                        }
                    }
                    records.add(record)
                } catch (e: JSONException) {
                    Logger.exception("Error parsing push notification", e)
                    throw RuntimeException("Error parsing push notification history", e)
                }
            }
            return records
        }

        private fun getRequiredString(obj: JSONObject, key: String, index: Int): String {
            return obj.optString(key, "").takeIf { it.isNotBlank() && it != "null" }
                ?: throw RuntimeException("$key is missing at index $index")
        }
    }
}
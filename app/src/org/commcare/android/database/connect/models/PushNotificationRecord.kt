package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONObject
import java.io.Serializable
import java.util.Date

@Table(PushNotificationRecord.STORAGE_KEY)
class PushNotificationRecord :
    Persisted(),
    Serializable {
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
    @MetaField(META_CREATED_DATE)
    var createdDate: Date = Date()

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

    @Persisting(13)
    @MetaField(META_ACKNOWLEDGED)
    var acknowledged: Boolean = false

    @Persisting(14)
    @MetaField(META_PAYMENT_UUID)
    var paymentUUID: String = ""

    @Persisting(15)
    @MetaField(META_OPPORTUNITY_UUID)
    var opportunityUUID: String = ""

    companion object {
        const val STORAGE_KEY = "push_notification_history"

        const val META_NOTIFICATION_ID = "notification_id"
        const val META_NOTIFICATION_TYPE = "notification_type"
        const val META_ACTION = "action"
        const val META_TITLE = "title"
        const val META_BODY = "body"
        const val META_CREATED_DATE = "created_date"
        const val META_CONFIRMATION_STATUS = "confirmation_status"
        const val META_OPPORTUNITY_ID = "opportunity_id"
        const val META_MESSAGE_ID = "message_id"
        const val META_CHANNEL = "channel"
        const val META_PAYMENT_ID = "payment_id"
        const val META_READ_STATUS = "read_status"
        const val META_ACKNOWLEDGED = "acknowledged"
        const val META_TIME_STAMP = "timestamp"
        const val META_OPPORTUNITY_UUID = "opportunity_uuid"
        const val META_PAYMENT_UUID = "payment_uuid"

        fun fromJson(obj: JSONObject): PushNotificationRecord =
            PushNotificationRecord().apply {
                notificationId = obj.optString(META_NOTIFICATION_ID, "")
                title = obj.optString(META_TITLE, "")
                body = obj.optString(META_BODY, "")
                notificationType = obj.optString(META_NOTIFICATION_TYPE, "")
                confirmationStatus = obj.optString(META_CONFIRMATION_STATUS, "")
                paymentId = obj.optString(META_PAYMENT_ID, "")
                readStatus = obj.optBoolean(META_READ_STATUS, false)
                val dateString: String = obj.getString(META_TIME_STAMP)
                createdDate = DateUtils.parseDateTime(dateString)
                connectMessageId = obj.optString(META_MESSAGE_ID, "")
                channel = obj.optString(META_CHANNEL, "")
                action = obj.optString(META_ACTION, "")
                opportunityId = obj.optString(META_OPPORTUNITY_ID, "")
                opportunityUUID = obj.optString(META_OPPORTUNITY_UUID, "")
                paymentUUID = obj.optString(META_PAYMENT_UUID, "")
            }

        fun fromV21(pushNotificationRecordV21: PushNotificationRecordV21): PushNotificationRecord =
            PushNotificationRecord().apply {
                notificationId = pushNotificationRecordV21.notificationId
                title = pushNotificationRecordV21.title
                body = pushNotificationRecordV21.body
                notificationType = pushNotificationRecordV21.notificationType
                confirmationStatus = pushNotificationRecordV21.confirmationStatus
                paymentId = pushNotificationRecordV21.paymentId
                readStatus = pushNotificationRecordV21.readStatus
                createdDate = pushNotificationRecordV21.createdDate
                connectMessageId = pushNotificationRecordV21.connectMessageId
                channel = pushNotificationRecordV21.channel
                action = pushNotificationRecordV21.action
                acknowledged = pushNotificationRecordV21.acknowledged
                opportunityId = pushNotificationRecordV21.opportunityId
                opportunityUUID = pushNotificationRecordV21.opportunityId
                paymentUUID = pushNotificationRecordV21.paymentId
            }
    }
}

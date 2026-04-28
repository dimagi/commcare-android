package org.commcare.android.database.connect.models

import android.text.TextUtils
import androidx.annotation.StringDef
import org.commcare.android.storage.framework.Persisted
import org.commcare.connect.ConnectConstants.CCC_GENERIC_OPPORTUNITY
import org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS_DELIVERY
import org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS_LEARN
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.commcare.utils.PushNotificationHelper.MESSAGE_NOTIFICATION_TITLE
import org.commcare.utils.PushNotificationHelper.NOTIFICATION
import org.commcare.utils.PushNotificationHelper.truncateMessage
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
        set(value) {
            field = truncateMessage(value, MESSAGE_NOTIFICATION_TITLE)
        }

    @Persisting(5)
    @MetaField(META_BODY)
    var body: String = ""
        set(value) {
            field = truncateMessage(value, NOTIFICATION)
        }

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

    @Persisting(16)
    @MetaField(META_KEY)
    var key: String = ""

    @Persisting(17)
    @MetaField(META_OPPORTUNITY_STATUS)
    @OpportunityStatusType
    var opportunityStatus: String = ""

    @Persisting(18)
    @MetaField(META_SESSION_ENDPOINT_ID)
    var sessionEndpointId: String = ""

    // if we do need to do an app sync, only matters when a session endpoint id is present.
    @Persisting(19)
    @MetaField(META_REQUIRE_APP_SYNC)
    var requireAppSync: Boolean = true

    fun getNotificationActionFromRecord() =
        if (CCC_GENERIC_OPPORTUNITY.equals(action) &&
            !TextUtils.isEmpty(key)
        ) {
            key
        } else {
            action
        }

    @StringDef(OPPORTUNITY_STATUS_LEARN, OPPORTUNITY_STATUS_DELIVERY)
    @Retention(AnnotationRetention.SOURCE)
    annotation class OpportunityStatusType

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
        const val META_KEY = "key"
        const val META_OPPORTUNITY_STATUS = "opportunity_status"
        const val META_SESSION_ENDPOINT_ID = "session_endpoint_id"
        const val META_REQUIRE_APP_SYNC = "require_app_sync"

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
                key = obj.optString(META_KEY, "")
                opportunityStatus = obj.optString(META_OPPORTUNITY_STATUS, "")
                sessionEndpointId = obj.optString(META_SESSION_ENDPOINT_ID, "")
                requireAppSync = obj.optBoolean(META_REQUIRE_APP_SYNC, true)
            }

        fun fromV24(v24: PushNotificationRecordV24): PushNotificationRecord =
            PushNotificationRecord().apply {
                notificationId = v24.notificationId
                title = v24.title
                body = v24.body
                notificationType = v24.notificationType
                confirmationStatus = v24.confirmationStatus
                paymentId = v24.paymentId
                readStatus = v24.readStatus
                createdDate = v24.createdDate
                connectMessageId = v24.connectMessageId
                channel = v24.channel
                action = v24.action
                acknowledged = v24.acknowledged
                opportunityId = v24.opportunityId
                opportunityUUID = v24.opportunityUUID
                paymentUUID = v24.paymentUUID
                key = v24.key
                opportunityStatus = v24.opportunityStatus
                sessionEndpointId = ""
                requireAppSync = true
            }
    }
}

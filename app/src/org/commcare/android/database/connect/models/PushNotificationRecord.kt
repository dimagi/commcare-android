package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable

@Table(PushNotificationRecord.STORAGE_KEY)
class PushNotificationRecord : Persisted(), Serializable {

    @Persisting(1)
    @MetaField(META_NOTIFICATION_ID)
    var notificationId: Int = 0

    @Persisting(2)
    @MetaField(META_NOTIFICATION_TYPE)
    var notificationType: String? = null

    @Persisting(3)
    @MetaField(META_ACTION)
    var action: String = ""

    @Persisting(4)
    @MetaField(META_TITLE)
    var title: String? = null

    @Persisting(5)
    @MetaField(META_BODY)
    var body: String? = null

    @Persisting(6)
    @MetaField(META_DATE_TIME)
    var dateTime: String? = null

    @Persisting(7)
    @MetaField(META_CONFIRMATION_STATUS)
    var confirmationStatus: String? = null

    @Persisting(8)
    @MetaField(META_OPPORTUNITY_ID)
    var opportunityId: Int? = null

    @Persisting(9)
    @MetaField(META_MESSAGE_ID)
    var connectMessageId: Int? = null

    @Persisting(10)
    @MetaField(META_CHANNEL)
    var channel: Int? = null

    @Persisting(11)
    @MetaField(META_PAYMENT_ID)
    var paymentId: Int? = null

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
        const val META_DATE_TIME = "date_time"
        const val META_CONFIRMATION_STATUS = "confirmation_status"
        const val META_OPPORTUNITY_ID = "opportunity_id"
        const val META_MESSAGE_ID = "message_id"
        const val META_CHANNEL = "channel"
        const val META_PAYMENT_ID = "payment_id"
        const val META_READ_STATUS = "read_status"
    }
}
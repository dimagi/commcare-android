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
    @MetaField(META_ACTION)
    var action: String = ""

    @Persisting(3)
    @MetaField(META_TITLE)
    var title: String = ""

    @Persisting(4)
    @MetaField(META_BODY)
    var body: String = ""

    @Persisting(5)
    @MetaField(META_DATE_TIME)
    var dateTime: String = ""

    @Persisting(6)
    @MetaField(META_CONFIRMATION_STATUS)
    var confirmationStatus: String = ""

    @Persisting(7)
    @MetaField(META_OPPORTUNITY_ID)
    var opportunityId: Int = 0

    @Persisting(8)
    @MetaField(META_CONNECT_MESSAGE_ID)
    var connectMessageId: Int = 0

    @Persisting(9)
    @MetaField(META_CONNECT_CHANNEL_ID)
    var connectChannelId: Int = 0

    companion object {
        const val STORAGE_KEY = "push_notification_history"

        const val META_NOTIFICATION_ID = "notification_id"
        const val META_ACTION = "action"
        const val META_TITLE = "title"
        const val META_BODY = "body"
        const val META_DATE_TIME = "date_time"
        const val META_CONFIRMATION_STATUS = "confirmation_status"
        const val META_OPPORTUNITY_ID = "opportunity_id"
        const val META_CONNECT_MESSAGE_ID = "connect_message_id"
        const val META_CONNECT_CHANNEL_ID = "connect_channel_id"
    }
}
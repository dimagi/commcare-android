package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(PushNotificationRecord.STORAGE_KEY)
class PushNotificationRecord : Persisted(), Serializable {

    @Persisting(1)
    @MetaField(META_NOTIFICATION_ID)
    var notificationId: Int? = null

    @Persisting(2)
    @MetaField(META_TITLE)
    var title: String = ""

    @Persisting(3)
    @MetaField(META_BODY)
    var body: String = ""

    @Persisting(4)
    @MetaField(META_ACTION)
    var action: String = ""

    @Persisting(5)
    @MetaField(META_NOTIFICATION_TYPE)
    var notificationType: String? = null

    @Persisting(6)
    @MetaField(META_RECEIVED)
    var received: Date? = null

    companion object {
        const val STORAGE_KEY = "push_notification_history"

        const val META_NOTIFICATION_ID = "notification_id"
        const val META_TITLE = "title"
        const val META_BODY = "body"
        const val META_ACTION = "action"
        const val META_NOTIFICATION_TYPE = "notification_type"
        const val META_RECEIVED = "received"
    }
}
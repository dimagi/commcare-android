package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_ACKNOWLEDGED
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_ACTION
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_BODY
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_CHANNEL
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_CONFIRMATION_STATUS
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_CREATED_DATE
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_MESSAGE_ID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_NOTIFICATION_ID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_NOTIFICATION_TYPE
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_OPPORTUNITY_ID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_PAYMENT_ID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_READ_STATUS
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_TITLE
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(PushNotificationRecordV21.STORAGE_KEY)
class PushNotificationRecordV21 :
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

    companion object {
        const val STORAGE_KEY = "push_notification_history"
    }
}

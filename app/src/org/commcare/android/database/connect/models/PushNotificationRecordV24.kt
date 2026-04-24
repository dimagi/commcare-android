package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_ACKNOWLEDGED
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_ACTION
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_BODY
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_CHANNEL
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_CONFIRMATION_STATUS
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_CREATED_DATE
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_KEY
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_MESSAGE_ID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_NOTIFICATION_ID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_NOTIFICATION_TYPE
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_OPPORTUNITY_ID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_OPPORTUNITY_STATUS
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_OPPORTUNITY_UUID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_PAYMENT_ID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_PAYMENT_UUID
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_READ_STATUS
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_TITLE
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(PushNotificationRecordV24.STORAGE_KEY)
class PushNotificationRecordV24 :
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

    @Persisting(16)
    @MetaField(META_KEY)
    var key: String = ""

    @Persisting(17)
    @MetaField(META_OPPORTUNITY_STATUS)
    var opportunityStatus: String = ""

    companion object {
        const val STORAGE_KEY = PushNotificationRecord.STORAGE_KEY

        fun fromV23(pushNotificationRecordV23: PushNotificationRecordV23): PushNotificationRecord =
            PushNotificationRecord().apply {
                notificationId = pushNotificationRecordV23.notificationId
                title = pushNotificationRecordV23.title
                body = pushNotificationRecordV23.body
                notificationType = pushNotificationRecordV23.notificationType
                confirmationStatus = pushNotificationRecordV23.confirmationStatus
                paymentId = pushNotificationRecordV23.paymentId
                readStatus = pushNotificationRecordV23.readStatus
                createdDate = pushNotificationRecordV23.createdDate
                connectMessageId = pushNotificationRecordV23.connectMessageId
                channel = pushNotificationRecordV23.channel
                action = pushNotificationRecordV23.action
                acknowledged = pushNotificationRecordV23.acknowledged
                opportunityId = pushNotificationRecordV23.opportunityId
                opportunityUUID = pushNotificationRecordV23.opportunityUUID
                paymentUUID = pushNotificationRecordV23.paymentUUID
                key = ""
                opportunityStatus = ""
            }
    }
}

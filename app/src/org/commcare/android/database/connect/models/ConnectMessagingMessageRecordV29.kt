package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord.META_MESSAGE
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord.META_MESSAGE_CONFIRM
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord.META_MESSAGE_ID
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord.META_MESSAGE_IS_OUTGOING
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord.META_MESSAGE_TIMESTAMP
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord.META_MESSAGE_USER_VIEWED
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(ConnectMessagingMessageRecordV29.STORAGE_KEY)
class ConnectMessagingMessageRecordV29 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_MESSAGE_ID)
    var messageId: String = ""

    @Persisting(2)
    @MetaField(META_MESSAGE_CHANNEL_ID)
    var channelId: String = ""

    @Persisting(3)
    @MetaField(META_MESSAGE_TIMESTAMP)
    var timeStamp: Date = Date()

    @Persisting(4)
    @MetaField(META_MESSAGE)
    var message: String = ""

    @Persisting(5)
    @MetaField(META_MESSAGE_IS_OUTGOING)
    var isOutgoing: Boolean = false

    @Persisting(6)
    @MetaField(META_MESSAGE_CONFIRM)
    var confirmed: Boolean = false

    @Persisting(7)
    @MetaField(META_MESSAGE_USER_VIEWED)
    var userViewed: Boolean = false

    companion object {
        const val STORAGE_KEY = ConnectMessagingMessageRecord.STORAGE_KEY
    }
}

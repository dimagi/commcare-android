package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable

@Table(ConnectMessagingAttachmentRecord.STORAGE_KEY)
class ConnectMessagingAttachmentRecord :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_ATTACHMENT_ID)
    var attachmentId: String = ""

    @Persisting(2)
    @MetaField(META_MESSAGE_ID)
    var messageId: String = ""

    @Persisting(3)
    var position: Int = 0

    @Persisting(4)
    var name: String = ""

    @Persisting(5)
    var type: String = ""

    @Persisting(6)
    var size: Long = 0

    @Persisting(7)
    @MetaField(META_STATE)
    var state: String = ConnectMessagingAttachmentState.WAITING.value

    @Persisting(8)
    var attempts: Int = 0

    var downloadState: ConnectMessagingAttachmentState
        get() = ConnectMessagingAttachmentState.fromValue(state)
        set(value) {
            state = value.value
        }

    val isImage: Boolean
        get() = type.startsWith("image/")

    val isAudio: Boolean
        get() = type.startsWith("audio/")

    companion object {
        const val STORAGE_KEY = "connect_messaging_attachment"
        const val META_ATTACHMENT_ID = "attachment_id"
        const val META_MESSAGE_ID = "message_id"
        const val META_STATE = "state"
    }
}

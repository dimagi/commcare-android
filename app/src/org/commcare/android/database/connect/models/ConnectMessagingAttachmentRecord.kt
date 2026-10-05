package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.util.UUID

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

    companion object {
        const val STORAGE_KEY = "connect_messaging_attachment"
        const val META_ATTACHMENT_ID = "attachment_id"
        const val META_MESSAGE_ID = "message_id"
        const val META_STATE = "state"

        private const val JSON_ATTACHMENTS = "attachments"
        private const val JSON_ID = "id"
        private const val JSON_NAME = "name"
        private const val JSON_TYPE = "type"
        private const val JSON_SIZE = "size"

        @JvmStatic
        @Throws(JSONException::class)
        fun listFromMessageJson(
            messageJson: JSONObject,
            messageId: String,
        ): List<ConnectMessagingAttachmentRecord> {
            val attachmentsJson = messageJson.optJSONArray(JSON_ATTACHMENTS) ?: return emptyList()
            return (0 until attachmentsJson.length()).map { index ->
                fromJson(attachmentsJson.getJSONObject(index), messageId, index)
            }
        }

        private fun fromJson(
            attachmentJson: JSONObject,
            messageId: String,
            position: Int,
        ): ConnectMessagingAttachmentRecord {
            val attachmentId = attachmentJson.getString(JSON_ID)
            if (!isCanonicalUuid(attachmentId)) {
                throw JSONException("Attachment id for message $messageId is not a UUID: '$attachmentId'")
            }
            val size = attachmentJson.getLong(JSON_SIZE)
            if (size < 0) {
                throw JSONException("Attachment $attachmentId has a negative size: $size")
            }
            return ConnectMessagingAttachmentRecord().apply {
                this.attachmentId = attachmentId
                this.messageId = messageId
                this.position = position
                name = attachmentJson.getString(JSON_NAME)
                type = attachmentJson.getString(JSON_TYPE)
                this.size = size
            }
        }

        private fun isCanonicalUuid(value: String): Boolean =
            try {
                UUID.fromString(value).toString().equals(value, ignoreCase = true)
            } catch (e: IllegalArgumentException) {
                false
            }
    }
}

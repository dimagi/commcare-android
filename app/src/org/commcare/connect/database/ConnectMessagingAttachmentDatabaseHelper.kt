package org.commcare.connect.database

import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.connect.messaging.ConnectMessagingAttachmentAutoDownloadPolicy
import org.commcare.models.database.SqlStorage
import java.util.Date

object ConnectMessagingAttachmentDatabaseHelper {
    private fun storage(): SqlStorage<ConnectMessagingAttachmentRecord> =
        ConnectDatabaseHelper.getConnectStorage(ConnectMessagingAttachmentRecord::class.java)

    @JvmStatic
    fun getAttachmentsForMessage(messageId: String): List<ConnectMessagingAttachmentRecord> =
        storage()
            .getRecordsForValues(
                arrayOf(ConnectMessagingAttachmentRecord.META_MESSAGE_ID),
                arrayOf<Any>(messageId),
            ).sortedBy { it.position }

    @JvmStatic
    fun getAttachment(attachmentId: String): ConnectMessagingAttachmentRecord? =
        storage()
            .getRecordsForValues(
                arrayOf(ConnectMessagingAttachmentRecord.META_ATTACHMENT_ID),
                arrayOf<Any>(attachmentId),
            ).firstOrNull()

    @JvmStatic
    @JvmOverloads
    fun storeNewAttachments(
        incoming: List<ConnectMessagingAttachmentRecord>,
        now: Date = Date(),
    ) {
        val newAttachments = incoming.filter { getAttachment(it.attachmentId) == null }
        if (newAttachments.isEmpty()) {
            return
        }

        val messagesById =
            newAttachments
                .map { it.messageId }
                .distinct()
                .associateWith { messageId ->
                    getMessage(messageId)
                        ?: throw IllegalStateException("Attachment refers to message $messageId, which is not stored")
                }
        val newestIncomingIdsByChannel =
            messagesById.values
                .map { it.channelId }
                .distinct()
                .associateWith { channelId ->
                    ConnectMessagingAttachmentAutoDownloadPolicy.newestIncomingMessageIds(
                        ConnectMessagingDatabaseHelper.getMessagingMessagesForChannel(channelId),
                    )
                }

        val storage = storage()
        for (attachment in newAttachments) {
            val message = messagesById.getValue(attachment.messageId)
            val newestIncomingIds = newestIncomingIdsByChannel.getValue(message.channelId)
            attachment.downloadState =
                ConnectMessagingAttachmentAutoDownloadPolicy.initialState(
                    message.timeStamp,
                    message.expiresAt,
                    message.messageId in newestIncomingIds,
                    now,
                )
            attachment.attempts = 0
            storage.write(attachment)
        }
    }

    private fun getMessage(messageId: String): ConnectMessagingMessageRecord? =
        ConnectDatabaseHelper
            .getConnectStorage(ConnectMessagingMessageRecord::class.java)
            .getRecordsForValues(
                arrayOf(ConnectMessagingMessageRecord.META_MESSAGE_ID),
                arrayOf<Any>(messageId),
            ).firstOrNull()
}

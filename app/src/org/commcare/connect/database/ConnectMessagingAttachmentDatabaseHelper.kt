package org.commcare.connect.database

import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.connect.messaging.ConnectMessagingAttachmentAutoDownloadPolicy
import org.commcare.models.database.SqlStorage
import java.util.Date

object ConnectMessagingAttachmentDatabaseHelper {
    private fun storage(): SqlStorage<ConnectMessagingAttachmentRecord> =
        ConnectDatabaseHelper.getConnectStorage(ConnectMessagingAttachmentRecord::class.java)

    @JvmStatic
    fun getAttachment(attachmentId: String): ConnectMessagingAttachmentRecord? =
        storage()
            .getRecordsForValues(
                arrayOf(ConnectMessagingAttachmentRecord.META_ATTACHMENT_ID),
                arrayOf<Any>(attachmentId),
            ).firstOrNull()

    @JvmStatic
    fun getAttachmentsByMessageId(): Map<String, List<ConnectMessagingAttachmentRecord>> =
        storage()
            .getRecordsForValues(arrayOf<String>(), arrayOf<Any>())
            .groupBy { it.messageId }
            .mapValues { (_, attachments) -> attachments.sortedBy { it.position } }

    fun getAttachmentsInState(state: ConnectMessagingAttachmentState): List<ConnectMessagingAttachmentRecord> =
        storage().getRecordsForValues(
            arrayOf(ConnectMessagingAttachmentRecord.META_STATE),
            arrayOf<Any>(state.value),
        )

    fun save(attachment: ConnectMessagingAttachmentRecord) {
        storage().write(attachment)
    }

    fun returnPendingAttachmentsToWaiting() {
        val pending =
            getAttachmentsInState(ConnectMessagingAttachmentState.QUEUED) +
                getAttachmentsInState(ConnectMessagingAttachmentState.DOWNLOADING)
        for (attachment in pending) {
            attachment.downloadState = ConnectMessagingAttachmentState.WAITING
            attachment.attempts = 0
            save(attachment)
        }
    }

    @JvmStatic
    fun requeue(attachmentId: String): Boolean {
        val attachment = getAttachment(attachmentId) ?: return false
        if (attachment.downloadState != ConnectMessagingAttachmentState.WAITING &&
            attachment.downloadState != ConnectMessagingAttachmentState.FAILED
        ) {
            return false
        }
        attachment.downloadState = ConnectMessagingAttachmentState.QUEUED
        attachment.attempts = 0
        save(attachment)
        return true
    }

    @JvmStatic
    @JvmOverloads
    fun storeNewAttachments(
        incoming: List<ConnectMessagingAttachmentRecord>,
        automaticDownloadEnabled: Boolean,
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
                    automaticDownloadEnabled && message.messageId in newestIncomingIds,
                    now,
                )
            attachment.attempts = 0
            storage.write(attachment)
        }
    }

    fun getMessage(messageId: String): ConnectMessagingMessageRecord? =
        ConnectDatabaseHelper
            .getConnectStorage(ConnectMessagingMessageRecord::class.java)
            .getRecordsForValues(
                arrayOf(ConnectMessagingMessageRecord.META_MESSAGE_ID),
                arrayOf<Any>(messageId),
            ).firstOrNull()
}

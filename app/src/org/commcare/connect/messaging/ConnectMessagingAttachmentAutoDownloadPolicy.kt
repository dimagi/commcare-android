package org.commcare.connect.messaging

import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import java.util.Date
import java.util.concurrent.TimeUnit

object ConnectMessagingAttachmentAutoDownloadPolicy {
    const val MAX_AUTO_DOWNLOAD_AGE_DAYS = 7L
    const val NEWEST_INCOMING_MESSAGES_TO_AUTO_DOWNLOAD = 50

    fun initialState(
        messageTimestamp: Date,
        expiresAt: Date?,
        isAmongNewestIncoming: Boolean,
        now: Date,
    ): ConnectMessagingAttachmentState {
        val isRecent = now.time - messageTimestamp.time < TimeUnit.DAYS.toMillis(MAX_AUTO_DOWNLOAD_AGE_DAYS)
        return when {
            expiresAt != null && !expiresAt.after(now) -> ConnectMessagingAttachmentState.EXPIRED
            isAmongNewestIncoming && isRecent -> ConnectMessagingAttachmentState.QUEUED
            else -> ConnectMessagingAttachmentState.WAITING
        }
    }

    fun newestIncomingMessageIds(channelMessages: Collection<ConnectMessagingMessageRecord>): Set<String> =
        channelMessages
            .filter { !it.isOutgoing }
            .sortedByDescending { it.timeStamp }
            .take(NEWEST_INCOMING_MESSAGES_TO_AUTO_DOWNLOAD)
            .map { it.messageId }
            .toSet()
}

package org.commcare.connect.messaging

import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState

object ConnectMessagingMessagePackage {
    fun stateOf(attachmentStates: Collection<ConnectMessagingAttachmentState>): ConnectMessagingAttachmentState {
        val pending = attachmentStates.filter { it != ConnectMessagingAttachmentState.AVAILABLE }
        return when {
            pending.isEmpty() -> ConnectMessagingAttachmentState.AVAILABLE
            ConnectMessagingAttachmentState.EXPIRED in pending -> ConnectMessagingAttachmentState.EXPIRED
            ConnectMessagingAttachmentState.DOWNLOADING in pending -> ConnectMessagingAttachmentState.DOWNLOADING
            ConnectMessagingAttachmentState.QUEUED in pending -> ConnectMessagingAttachmentState.QUEUED
            ConnectMessagingAttachmentState.FAILED in pending -> ConnectMessagingAttachmentState.FAILED
            else -> ConnectMessagingAttachmentState.WAITING
        }
    }

    @JvmStatic
    fun stateOfAttachments(attachments: List<ConnectMessagingAttachmentRecord>?): ConnectMessagingAttachmentState =
        stateOf(attachments.orEmpty().map { it.downloadState })
}

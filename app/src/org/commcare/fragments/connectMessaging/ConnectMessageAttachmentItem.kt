package org.commcare.fragments.connectMessaging

import android.content.Context
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.connect.database.ConnectMessagingAttachmentFileStore
import java.io.File

data class ConnectMessageAttachmentItem(
    val attachmentId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val state: ConnectMessagingAttachmentState,
    val file: File?,
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/")

    companion object {
        @JvmStatic
        fun fromRecord(
            context: Context,
            record: ConnectMessagingAttachmentRecord,
        ): ConnectMessageAttachmentItem {
            val state = record.downloadState
            return ConnectMessageAttachmentItem(
                attachmentId = record.attachmentId,
                name = record.name,
                mimeType = record.type,
                sizeBytes = record.size,
                state = state,
                file =
                    if (state == ConnectMessagingAttachmentState.AVAILABLE) {
                        ConnectMessagingAttachmentFileStore.fileFor(context, record.attachmentId)
                    } else {
                        null
                    },
            )
        }
    }
}

interface ConnectMessageAttachmentListener {
    fun onAttachmentDownloadRequested(attachmentId: String)

    fun onAttachmentOpenRequested(attachment: ConnectMessageAttachmentItem)
}

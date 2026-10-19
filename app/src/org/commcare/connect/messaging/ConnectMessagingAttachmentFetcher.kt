package org.commcare.connect.messaging

import java.io.IOException

fun interface ConnectMessagingAttachmentFetcher {
    @Throws(IOException::class)
    fun fetch(
        messageId: String,
        attachmentId: String,
        expectedSize: Long,
    ): ConnectMessagingAttachmentFetchResponse
}

class ConnectMessagingAttachmentFetchResponse(
    val statusCode: Int,
    val body: ByteArray?,
)

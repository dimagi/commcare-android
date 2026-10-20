package org.commcare.connect.messaging

import org.commcare.connect.network.personalId.ApiPersonalId
import java.io.ByteArrayOutputStream
import java.io.InputStream

class PersonalIdAttachmentFetcher(
    private val userId: String,
    private val password: String,
) : ConnectMessagingAttachmentFetcher {
    override fun fetch(
        messageId: String,
        attachmentId: String,
        expectedSize: Long,
    ): ConnectMessagingAttachmentFetchResponse {
        val response =
            ApiPersonalId
                .buildMessageAttachmentDownloadCall(userId, password, messageId, attachmentId)
                .execute()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            return ConnectMessagingAttachmentFetchResponse(response.code(), null)
        }
        val body = response.body() ?: return ConnectMessagingAttachmentFetchResponse(response.code(), null)
        return body.use {
            ConnectMessagingAttachmentFetchResponse(response.code(), readAtMost(it.byteStream(), expectedSize + 1))
        }
    }

    private fun readAtMost(
        stream: InputStream,
        limit: Long,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (total < limit) {
            val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
            if (read == -1) {
                break
            }
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}

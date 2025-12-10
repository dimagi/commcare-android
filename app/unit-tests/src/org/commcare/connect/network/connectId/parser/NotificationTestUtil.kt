package org.commcare.connect.network.connectId.parser

import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import java.io.ByteArrayInputStream

/**
 * Test utility class providing builders for creating notification test data.
 * 
 * Contains helper methods for:
 * - Building JSON notifications (push, messaging, channels)
 * - Creating complete API responses
 * - Generating properly encrypted messaging notifications
 */
object NotificationTestUtil {

    // ========== Constants ==========
    
    /** Valid 256-bit Base64 encoded encryption key for testing */
    const val TEST_ENCRYPTION_KEY = "MTIzNDU2Nzg5MGFiY2RlZmdoaWprbG1ub3BxcnM3dXY="
    
    // ========== JSON Builders ==========

    fun createPushNotificationJson(
        notificationId: String,
        title: String,
        body: String = "Test body",
        notificationType: String = "PUSH",
        timestamp: String = "2023-01-01T12:00:00Z",
        messageId: String? = null,
        channel: String? = null,
        action: String = "",
        confirmationStatus: String = "",
        opportunityId: String = "",
        paymentId: String = ""
    ): String {
        val parts = mutableListOf<String>()
        parts.add("\"notification_id\": \"$notificationId\"")
        parts.add("\"title\": \"$title\"")
        parts.add("\"body\": \"$body\"")
        parts.add("\"notification_type\": \"$notificationType\"")
        parts.add("\"timestamp\": \"$timestamp\"")

        messageId?.let { parts.add("\"message_id\": \"$it\"") }
        channel?.let { parts.add("\"channel\": \"$it\"") }
        if (action.isNotEmpty()) parts.add("\"action\": \"$action\"")
        if (confirmationStatus.isNotEmpty()) parts.add("\"confirmation_status\": \"$confirmationStatus\"")
        if (opportunityId.isNotEmpty()) parts.add("\"opportunity_id\": \"$opportunityId\"")
        if (paymentId.isNotEmpty()) parts.add("\"payment_id\": \"$paymentId\"")

        return "{\n" + parts.joinToString(",\n") + "\n}"
    }

    fun createMessagingNotificationJson(
        notificationId: String,
        messageId: String,
        channel: String,
        title: String = "Message Title",
        timestamp: String = "2023-01-01T12:00:00Z"
    ): String {
        return """
        {
            "notification_id": "$notificationId",
            "message_id": "$messageId",
            "channel": "$channel",
            "title": "$title",
            "notification_type": "MESSAGING",
            "timestamp": "$timestamp",
            "tag": "test_tag",
            "nonce": "test_nonce",
            "ciphertext": "encrypted_content"
        }
        """.trimIndent()
    }

    fun createMessagingNotificationWithValidEncryption(
        notificationId: String,
        messageId: String,
        channel: String,
        messageContent: String,
        encryptionKey: String = TEST_ENCRYPTION_KEY,
        title: String = "Message Title",
        timestamp: String = "2023-01-01T12:00:00Z"
    ): String {
        // Use the actual encryption method from ConnectMessagingMessageRecord
        val encryptionResult = ConnectMessagingMessageRecord.encrypt(messageContent, encryptionKey)
        val cipherText = encryptionResult[0]
        val nonce = encryptionResult[1] 
        val tag = encryptionResult[2]

        return """
        {
            "notification_id": "$notificationId",
            "message_id": "$messageId",
            "channel": "$channel",
            "title": "$title",
            "notification_type": "MESSAGING",
            "timestamp": "$timestamp",
            "tag": "$tag",
            "nonce": "$nonce",
            "ciphertext": "$cipherText"
        }
        """.trimIndent()
    }

    fun createChannelJson(
        channelId: String,
        channelSource: String = "Test Channel",
        consent: Boolean = true,
        keyUrl: String = "test_key_url"
    ): String {
        return """
        {
            "channel_id": "$channelId",
            "channel_source": "$channelSource",
            "consent": $consent,
            "key_url": "$keyUrl"
        }
        """.trimIndent()
    }

    fun createCompleteResponse(
        notifications: List<String> = emptyList(),
        channels: List<String> = emptyList()
    ): String {
        val notificationArray = if (notifications.isNotEmpty()) {
            notifications.joinToString(",\n")
        } else ""

        val channelArray = if (channels.isNotEmpty()) {
            channels.joinToString(",\n")
        } else ""

        return buildString {
            append("{\n")
            append("\"notifications\": [")
            if (notificationArray.isNotEmpty()) {
                append("\n$notificationArray\n")
            }
            append("]")

            if (channels.isNotEmpty()) {
                append(",\n\"channels\": [")
                if (channelArray.isNotEmpty()) {
                    append("\n$channelArray\n")
                }
                append("]")
            }

            append("\n}")
        }
    }

    // ========== Parser Helpers ==========

    fun createInputStreamFromResponse(jsonResponse: String): ByteArrayInputStream {
        return ByteArrayInputStream(jsonResponse.toByteArray())
    }
}
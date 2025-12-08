package org.commcare.connect.network.connectId.parser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Comprehensive test suite for RetrieveNotificationsResponseParser
 *
 * Tests the parser's ability to:
 * - Separate messaging vs non-messaging notifications
 * - Parse channels correctly
 * - Handle various edge cases and error conditions
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RetrieveNotificationsResponseParserTest {

    private var context: Context = CommCareTestApplication.instance()
    private lateinit var parser: RetrieveNotificationsResponseParser

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        parser = RetrieveNotificationsResponseParser(context)
    }

    // ========== Test Data Builders ==========

    private fun createPushNotificationJson(
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

    private fun createMessagingNotificationJson(
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

    private fun createChannelJson(
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

    private fun createCompleteResponse(
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

    private fun parseResponse(jsonResponse: String): NotificationParseResult {
        val inputStream = ByteArrayInputStream(jsonResponse.toByteArray())
        return parser.parse(200, inputStream, null)
    }

    // ========== Core Functionality Tests ==========

    @Test
    fun testParseEmptyNotificationsArray() {
        val response = createCompleteResponse()

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
                ConnectMessagingDatabaseHelper.getMessagingChannels(any())
            }.thenReturn(emptyList())

            val result = parseResponse(response)

            assertEquals(0, result.notifications.size)
            assertEquals(0, result.channels.size)
            assertEquals(0, result.messages.size)
        }
    }

    @Test
    fun testParsePushNotifications() {
        val pushNotification1 = createPushNotificationJson("push_001", "Title 1")
        val pushNotification2 = createPushNotificationJson("push_002", "Title 2")
        val response = createCompleteResponse(notifications = listOf(pushNotification1, pushNotification2))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
                ConnectMessagingDatabaseHelper.getMessagingChannels(any())
            }.thenReturn(emptyList())

            val result = parseResponse(response)

            assertEquals(2, result.notifications.size)
            assertEquals(0, result.channels.size)
            assertEquals(0, result.messages.size)

            assertEquals("push_001", result.notifications[0].notificationId)
            assertEquals("push_002", result.notifications[1].notificationId)
        }
    }

    @Test
    fun testParseChannels() {
        val channel1 = createChannelJson("channel_001", "Channel 1")
        val channel2 = createChannelJson("channel_002", "Channel 2", false)
        val response = createCompleteResponse(channels = listOf(channel1, channel2))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use {
            val result = parseResponse(response)

            assertEquals(0, result.notifications.size)
            assertEquals(2, result.channels.size)
            assertEquals(0, result.messages.size)

            assertEquals("channel_001", result.channels[0].channelId)
            assertEquals("channel_002", result.channels[1].channelId)
            assertTrue(result.channels[0].consented)
            assertTrue(!result.channels[1].consented)
        }
    }

    @Test
    fun testParseCompleteResponseWithAllTypes() {
        val pushNotification = createPushNotificationJson("push_001", "Push Title")
        val messagingNotification = createMessagingNotificationJson("msg_001", "message_001", "channel_001")
        val channel = createChannelJson("channel_001", "Test Channel")

        val response = createCompleteResponse(
            notifications = listOf(pushNotification, messagingNotification),
            channels = listOf(channel)
        )

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
                ConnectMessagingDatabaseHelper.getMessagingChannels(any())
            }.thenReturn(emptyList())

            val result = parseResponse(response)

            assertEquals(1, result.notifications.size)
            assertEquals(1, result.channels.size)

            // no channel associated, so message cannot be decrypted and won't be added to the result
            assertEquals(0, result.messages.size)

            // Verify push notification
            assertEquals("push_001", result.notifications[0].notificationId)

            // Verify channel
            assertEquals("channel_001", result.channels[0].channelId)
        }
    }

    @Test
    fun testParseNotificationWithAllOptionalFields() {
        val pushNotification = createPushNotificationJson(
            notificationId = "push_full",
            title = "Full Notification",
            body = "Complete body",
            notificationType = "PAYMENT",
            messageId = "msg_123",
            channel = "channel_123",
            action = "redirect_action",
            confirmationStatus = "confirmed",
            opportunityId = "opp_456",
            paymentId = "pay_789"
        )
        val response = createCompleteResponse(notifications = listOf(pushNotification))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
                ConnectMessagingDatabaseHelper.getMessagingChannels(any())
            }.thenReturn(emptyList())

            val result = parseResponse(response)

            assertEquals(1, result.notifications.size)
            val notification = result.notifications[0]

            assertEquals("push_full", notification.notificationId)
            assertEquals("Full Notification", notification.title)
            assertEquals("Complete body", notification.body)
            assertEquals("PAYMENT", notification.notificationType)
            assertEquals("msg_123", notification.connectMessageId)
            assertEquals("channel_123", notification.channel)
            assertEquals("redirect_action", notification.action)
            assertEquals("confirmed", notification.confirmationStatus)
            assertEquals("opp_456", notification.opportunityId)
            assertEquals("pay_789", notification.paymentId)
        }
    }

    // ========== Error Handling Tests ==========

    @Test(expected = JSONException::class)
    fun testParseInvalidJson() {
        val invalidJson = "{ invalid json structure"
        parseResponse(invalidJson)
    }

    @Test(expected = JSONException::class)
    fun testParseNullNotificationsArray() {
        val response = """{"notifications": null}"""
        parseResponse(response)
    }

    @Test(expected = JSONException::class)
    fun testParseNotificationMissingRequiredFields() {
        // Test notification without required timestamp field
        val incompleteNotification = """
        {
            "notification_id": "incomplete_001",
            "title": "Incomplete Notification",
            "notification_type": "PUSH"
        }
        """.trimIndent()
        val response = createCompleteResponse(notifications = listOf(incompleteNotification))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
                ConnectMessagingDatabaseHelper.getMessagingChannels(any())
            }.thenReturn(emptyList())

            parseResponse(response)
        }
    }
}

package org.commcare.connect.network.personalId.parser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ========== Helper Methods ==========

    private fun parseResponse(jsonResponse: String): NotificationParseResult {
        val inputStream = NotificationTestUtil.createInputStreamFromResponse(jsonResponse)
        return parser.parse(200, inputStream, null)
    }

    // ========== Core Functionality Tests ==========

    @Test
    fun testParseEmptyNotificationsArray() {
        val response = NotificationTestUtil.createCompleteResponse()

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper
                .`when`<List<ConnectMessagingChannelRecord>> {
                    ConnectMessagingDatabaseHelper.getMessagingChannels(any())
                }.thenReturn(emptyList())

            val result = parseResponse(response)

            assertEquals(0, result.nonMessagingNotifications.size)
            assertEquals(0, result.channels.size)
            assertEquals(0, result.messages.size)
            assertEquals(0, result.messagingNotificationIds.size)
        }
    }

    @Test
    fun testParsePushNotifications() {
        val pushNotification1 = NotificationTestUtil.createPushNotificationJson("push_001", "Title 1")
        val pushNotification2 = NotificationTestUtil.createPushNotificationJson("push_002", "Title 2")
        val response = NotificationTestUtil.createCompleteResponse(notifications = listOf(pushNotification1, pushNotification2))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper
                .`when`<List<ConnectMessagingChannelRecord>> {
                    ConnectMessagingDatabaseHelper.getMessagingChannels(any())
                }.thenReturn(emptyList())

            val result = parseResponse(response)

            assertEquals(2, result.nonMessagingNotifications.size)
            assertEquals(0, result.channels.size)
            assertEquals(0, result.messages.size)
            assertEquals(0, result.messagingNotificationIds.size)

            assertEquals("push_001", result.nonMessagingNotifications[0].notificationId)
            assertEquals("push_002", result.nonMessagingNotifications[1].notificationId)
        }
    }

    @Test
    fun testParseChannels() {
        val channel1 = NotificationTestUtil.createChannelJson("channel_001", "Channel 1")
        val channel2 = NotificationTestUtil.createChannelJson("channel_002", "Channel 2", false)
        val response = NotificationTestUtil.createCompleteResponse(channels = listOf(channel1, channel2))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use {
            val result = parseResponse(response)

            assertEquals(0, result.nonMessagingNotifications.size)
            assertEquals(2, result.channels.size)
            assertEquals(0, result.messages.size)
            assertEquals(0, result.messagingNotificationIds.size)

            assertEquals("channel_001", result.channels[0].channelId)
            assertEquals("channel_002", result.channels[1].channelId)
            assertTrue(result.channels[0].consented)
            assertFalse(result.channels[1].consented)
        }
    }

    @Test
    fun testParseCompleteResponseWithAllTypes() {
        val pushNotification = NotificationTestUtil.createPushNotificationJson("push_001", "Push Title")
        val messagingNotification = NotificationTestUtil.createMessagingNotificationJson("msg_001", "message_001", "channel_001")
        val channel = NotificationTestUtil.createChannelJson("channel_001", "Test Channel")

        val response =
            NotificationTestUtil.createCompleteResponse(
                notifications = listOf(pushNotification, messagingNotification),
                channels = listOf(channel),
            )

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper
                .`when`<List<ConnectMessagingChannelRecord>> {
                    ConnectMessagingDatabaseHelper.getMessagingChannels(any())
                }.thenReturn(emptyList())

            val result = parseResponse(response)

            assertEquals(1, result.nonMessagingNotifications.size)
            assertEquals(1, result.channels.size)

            // no channel associated, so message cannot be decrypted and won't be added to the result
            assertEquals(0, result.messages.size)
            assertEquals(0, result.messagingNotificationIds.size)

            // Verify push notification
            assertEquals("push_001", result.nonMessagingNotifications[0].notificationId)

            // Verify channel
            assertEquals("channel_001", result.channels[0].channelId)
        }
    }

    @Test
    fun testParseNotificationWithAllOptionalFields() {
        val pushNotification =
            NotificationTestUtil.createPushNotificationJson(
                notificationId = "push_full",
                title = "Full Notification",
                body = "Complete body",
                notificationType = "PAYMENT",
                messageId = "msg_123",
                channel = "channel_123",
                action = "redirect_action",
                confirmationStatus = "confirmed",
                opportunityId = "opp_456",
                paymentId = "pay_789",
            )
        val response = NotificationTestUtil.createCompleteResponse(notifications = listOf(pushNotification))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper
                .`when`<List<ConnectMessagingChannelRecord>> {
                    ConnectMessagingDatabaseHelper.getMessagingChannels(any())
                }.thenReturn(emptyList())

            val result = parseResponse(response)

            assertEquals(1, result.nonMessagingNotifications.size)
            val notification = result.nonMessagingNotifications[0]

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

    @Test
    fun testParseCompleteResponseWithMessagesPopulated() {
        val encryptionKey = NotificationTestUtil.TEST_ENCRYPTION_KEY
        val messageContent = "Hello, this is a test message!"

        val pushNotification = NotificationTestUtil.createPushNotificationJson("push_001", "Push Title")
        val messagingNotification =
            NotificationTestUtil.createMessagingNotificationWithValidEncryption(
                "msg_001",
                "message_001",
                "channel_001",
                messageContent,
                encryptionKey,
            )
        val channel = NotificationTestUtil.createChannelJson("channel_001", "Test Channel")

        val response =
            NotificationTestUtil.createCompleteResponse(
                notifications = listOf(pushNotification, messagingNotification),
                channels = listOf(channel),
            )

        // Create a mock channel with encryption key for decryption
        val mockChannel = mock(ConnectMessagingChannelRecord::class.java)
        `when`(mockChannel.channelId).thenReturn("channel_001")
        `when`(mockChannel.channelSource).thenReturn("Test Channel")
        `when`(mockChannel.key).thenReturn(encryptionKey)
        `when`(mockChannel.consented).thenReturn(true)

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper
                .`when`<List<ConnectMessagingChannelRecord>> {
                    ConnectMessagingDatabaseHelper.getMessagingChannels(any())
                }.thenReturn(listOf(mockChannel))

            val result = parseResponse(response)

            assertEquals(1, result.nonMessagingNotifications.size)
            assertEquals(1, result.channels.size)
            assertEquals(1, result.messages.size)
            assertEquals("message_001", result.messages[0].messageId)
            assertEquals("channel_001", result.messages[0].channelId)
            assertEquals(messageContent, result.messages[0].message)
            assertEquals("push_001", result.nonMessagingNotifications[0].notificationId)
            assertEquals("channel_001", result.channels[0].channelId)
            assertEquals(1, result.messagingNotificationIds.size)
            assertEquals("msg_001", result.messagingNotificationIds[0])
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

    @Test
    fun `push notification missing its timestamp is skipped and the rest still parse`() {
        val incompleteNotification =
            """
            {
                "notification_id": "incomplete_001",
                "title": "Incomplete Notification",
                "notification_type": "PUSH"
            }
            """.trimIndent()
        val validNotification = NotificationTestUtil.createPushNotificationJson("push_001", "Title 1")
        val response =
            NotificationTestUtil.createCompleteResponse(
                notifications = listOf(incompleteNotification, validNotification),
            )

        val result = parseWithStoredChannels(response, emptyList())

        assertEquals(1, result.nonMessagingNotifications.size)
        assertEquals("push_001", result.nonMessagingNotifications[0].notificationId)
    }

    @Test
    fun `malformed messaging entry is skipped and not acknowledged while the rest still parse`() {
        val malformedMessage =
            """
            {
                "notification_id": "msg_bad",
                "notification_type": "MESSAGING",
                "channel": "channel_001"
            }
            """.trimIndent()
        val validMessage =
            NotificationTestUtil.createMessagingNotificationWithValidEncryption(
                "msg_001",
                "message_001",
                "channel_001",
                "Hello",
            )
        val response = NotificationTestUtil.createCompleteResponse(notifications = listOf(malformedMessage, validMessage))

        val result = parseWithStoredChannels(response, listOf(channelWithTestKey("channel_001")))

        assertEquals(listOf("message_001"), result.messages.map { it.messageId })
        assertEquals(listOf("msg_001"), result.messagingNotificationIds)
    }

    @Test
    fun `plain message is stored as version zero with no attachments`() {
        val result =
            parseMessages(
                NotificationTestUtil.createMessagingNotificationWithValidEncryption("msg_001", "message_001", "channel_001", "Hello"),
            )

        val message = result.messages.single()
        assertEquals(ConnectMessagingMessageRecord.VERSION_PLAIN, message.version)
        assertEquals("Hello", message.displayText)
        assertNull(message.richText)
        assertTrue(result.attachments.isEmpty())
    }

    @Test
    fun `version 2 message reads rich text, format, expiry and attachments in order`() {
        val notification =
            NotificationTestUtil.createRichMessagingNotification(
                "msg_001",
                "message_001",
                "channel_001",
                content = "Plain text",
                richText = "**Rich** text",
                format = "gallery",
                expiresAt = "2026-02-01T00:00:00Z",
                attachments =
                    listOf(
                        NotificationTestUtil.createAttachmentJson(FIRST_ATTACHMENT_ID, "site-map.jpg", "image/jpeg", 184279),
                        NotificationTestUtil.createAttachmentJson(SECOND_ATTACHMENT_ID, "instructions.mp3", "audio/mpeg", 412964),
                    ),
            )

        val result = parseMessages(notification)

        val message = result.messages.single()
        assertTrue(message.isRich)
        assertEquals("Plain text", message.message)
        assertEquals("**Rich** text", message.displayText)
        assertEquals("gallery", message.format)
        assertEquals(DateUtils.parseDateTime("2026-02-01T00:00:00Z"), message.expiresAt)
        assertEquals(listOf(FIRST_ATTACHMENT_ID, SECOND_ATTACHMENT_ID), result.attachments.map { it.attachmentId })
        assertEquals(listOf(0, 1), result.attachments.map { it.position })
        assertEquals(listOf("message_001", "message_001"), result.attachments.map { it.messageId })
        assertEquals("instructions.mp3", result.attachments[1].name)
        assertEquals("audio/mpeg", result.attachments[1].type)
        assertEquals(412964L, result.attachments[1].size)
    }

    @Test
    fun `version 2 message without optional fields shows its content`() {
        val result =
            parseMessages(
                NotificationTestUtil.createRichMessagingNotification("msg_001", "message_001", "channel_001", content = "Plain text"),
            )

        val message = result.messages.single()
        assertTrue(message.isRich)
        assertEquals("Plain text", message.displayText)
        assertNull(message.format)
        assertNull(message.expiresAt)
        assertTrue(result.attachments.isEmpty())
    }

    @Test
    fun `version 2 message whose rich text does not decrypt falls back to content`() {
        val notification =
            NotificationTestUtil.createRichMessagingNotification(
                "msg_001",
                "message_001",
                "channel_001",
                content = "Plain text",
                richText = "Rich text",
                encryptionKey = NotificationTestUtil.TEST_ENCRYPTION_KEY,
            )
        val richTextWithBadCipher =
            JSONObject(notification).apply {
                getJSONObject("rich_text").put("ciphertext", "AAAA")
            }

        val result = parseMessages(richTextWithBadCipher.toString())

        assertEquals("Plain text", result.messages.single().displayText)
    }

    @Test
    fun `unknown version keeps the content and ignores attachments`() {
        val notification =
            NotificationTestUtil.createRichMessagingNotification(
                "msg_001",
                "message_001",
                "channel_001",
                content = "Plain text",
                version = 3,
                attachments = listOf(NotificationTestUtil.createAttachmentJson(FIRST_ATTACHMENT_ID, "site-map.jpg")),
            )

        val result = parseMessages(notification)

        val message = result.messages.single()
        assertTrue(message.isUnsupportedVersion)
        assertEquals("Plain text", message.displayText)
        assertTrue(result.attachments.isEmpty())
    }

    @Test
    fun `version 2 message with a malformed attachment is skipped while the rest still parse`() {
        val malformed =
            NotificationTestUtil.createRichMessagingNotification(
                "msg_bad",
                "message_bad",
                "channel_001",
                content = "Broken",
                attachments = listOf("""{"name": "x"}"""),
            )
        val valid =
            NotificationTestUtil.createRichMessagingNotification(
                "msg_001",
                "message_001",
                "channel_001",
                content = "Fine",
                attachments = listOf(NotificationTestUtil.createAttachmentJson(FIRST_ATTACHMENT_ID, "site-map.jpg")),
            )

        val result = parseMessages(malformed, valid)

        assertEquals(listOf("message_001"), result.messages.map { it.messageId })
        assertEquals(listOf("msg_001"), result.messagingNotificationIds)
        assertEquals(listOf(FIRST_ATTACHMENT_ID), result.attachments.map { it.attachmentId })
    }

    @Test
    fun `attachment whose id is not a UUID fails its message`() {
        val notification =
            NotificationTestUtil.createRichMessagingNotification(
                "msg_001",
                "message_001",
                "channel_001",
                content = "Plain text",
                attachments = listOf(NotificationTestUtil.createAttachmentJson("../escape", "site-map.jpg")),
            )

        val result = parseMessages(notification)

        assertTrue(result.messages.isEmpty())
        assertTrue(result.messagingNotificationIds.isEmpty())
    }

    private fun parseMessages(vararg messagingNotifications: String): NotificationParseResult =
        parseWithStoredChannels(
            NotificationTestUtil.createCompleteResponse(notifications = messagingNotifications.toList()),
            listOf(channelWithTestKey("channel_001")),
        )

    private fun parseWithStoredChannels(
        response: String,
        storedChannels: List<ConnectMessagingChannelRecord>,
    ): NotificationParseResult =
        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper
                .`when`<List<ConnectMessagingChannelRecord>> {
                    ConnectMessagingDatabaseHelper.getMessagingChannels(any())
                }.thenReturn(storedChannels)
            parseResponse(response)
        }

    private fun channelWithTestKey(channelId: String): ConnectMessagingChannelRecord {
        val channel = mock(ConnectMessagingChannelRecord::class.java)
        `when`(channel.channelId).thenReturn(channelId)
        `when`(channel.key).thenReturn(NotificationTestUtil.TEST_ENCRYPTION_KEY)
        `when`(channel.consented).thenReturn(true)
        return channel
    }

    private companion object {
        const val FIRST_ATTACHMENT_ID = "5b0c1e7a-0f6b-4b8e-9d2a-1c3e5f7a9b0d"
        const val SECOND_ATTACHMENT_ID = "9e1d2f3a-4b5c-4d6e-8f90-a1b2c3d4e5f6"
    }
}

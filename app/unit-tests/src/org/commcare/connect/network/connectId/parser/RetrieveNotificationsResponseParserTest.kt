package org.commcare.connect.network.connectId.parser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
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
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
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

        val response = NotificationTestUtil.createCompleteResponse(
            notifications = listOf(pushNotification, messagingNotification),
            channels = listOf(channel)
        )

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
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
        val pushNotification = NotificationTestUtil.createPushNotificationJson(
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
        val response = NotificationTestUtil.createCompleteResponse(notifications = listOf(pushNotification))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
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
        val messagingNotification = NotificationTestUtil.createMessagingNotificationWithValidEncryption(
            "msg_001",
            "message_001",
            "channel_001",
            messageContent,
            encryptionKey
        )
        val channel = NotificationTestUtil.createChannelJson("channel_001", "Test Channel")

        val response = NotificationTestUtil.createCompleteResponse(
            notifications = listOf(pushNotification, messagingNotification),
            channels = listOf(channel)
        )

        // Create a mock channel with encryption key for decryption
        val mockChannel = mock(ConnectMessagingChannelRecord::class.java)
        `when`(mockChannel.channelId).thenReturn("channel_001")
        `when`(mockChannel.channelName).thenReturn("Test Channel")
        `when`(mockChannel.key).thenReturn(encryptionKey)
        `when`(mockChannel.consented).thenReturn(true)

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
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
        val response = NotificationTestUtil.createCompleteResponse(notifications = listOf(incompleteNotification))

        mockStatic(ConnectMessagingDatabaseHelper::class.java).use { mockedHelper ->
            mockedHelper.`when`<List<ConnectMessagingChannelRecord>> {
                ConnectMessagingDatabaseHelper.getMessagingChannels(any())
            }.thenReturn(emptyList())

            parseResponse(response)
        }
    }
}

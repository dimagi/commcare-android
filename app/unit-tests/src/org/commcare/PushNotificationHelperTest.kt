package org.commcare

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.models.database.SqlStorage
import org.commcare.utils.PushNotificationHelper.MAX_MESSAGE_LENGTH
import org.commcare.utils.PushNotificationHelper.truncateMessage
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PushNotificationHelperTest {
    private val context: Context = CommCareTestApplication.instance()
    private val longMessage = "A".repeat(70000)
    private val encryptedKey = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
    private val channelId = "channel-id"
    private val messageId = "test-id"

    @Test
    fun truncateMessage_whenMessageTooLong_shouldTruncate() {
        val result =
            truncateMessage(
                longMessage,
                "message",
            )
        Assert.assertEquals(
            MAX_MESSAGE_LENGTH,
            result.length,
        )
    }

    @Test
    fun truncateMessage_whenMessageShort_shouldRemainSame() {
        val message = "Hello"
        val result =
            truncateMessage(
                message,
                "message",
            )
        Assert.assertEquals(message, result)
    }

    @Test
    fun truncateMessage_whenExactlyMaxLength_shouldNotTruncate() {
        val message =
            "A".repeat(MAX_MESSAGE_LENGTH)
        val result =
            truncateMessage(
                message,
                "message",
            )
        Assert.assertEquals(message, result)
    }

    @Test
    fun storingLongMessage_inDb_shouldStoreTruncatedMessage() {
        val mockStorage = setupMockStorage()
        val storage = getStorage()
        val record = createRecord(longMessage)
        storage.write(record)
        val stored = storage.read(record.id)
        Assert.assertNotNull(stored)
        Assert.assertEquals(
            MAX_MESSAGE_LENGTH,
            stored.message.length,
        )
        verify(exactly = 1) { mockStorage.write(any()) }
        verify(exactly = 1) { mockStorage.read(any()) }
        cleanupMock()
    }

    private fun setupMockStorage(): SqlStorage<ConnectMessagingMessageRecord> {
        val slot = slot<ConnectMessagingMessageRecord>()
        val mockStorage = mockk<SqlStorage<ConnectMessagingMessageRecord>>()
        mockkStatic(ConnectDatabaseHelper::class)
        every {
            ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingMessageRecord::class.java)
        } returns mockStorage

        every { mockStorage.write(capture(slot)) } just Runs
        every { mockStorage.read(any()) } answers { slot.captured }

        return mockStorage
    }


    private fun cleanupMock() {
        unmockkStatic(ConnectDatabaseHelper::class)
    }

    private fun getStorage():
            SqlStorage<ConnectMessagingMessageRecord> {

        return ConnectDatabaseHelper.getConnectStorage(
            context,
            ConnectMessagingMessageRecord::class.java
        )
    }

    private fun createRecord(
        message: String
    ): ConnectMessagingMessageRecord {
        val encrypted =
            ConnectMessagingMessageRecord.encrypt(
                message,
                encryptedKey
            )

        val json = JSONObject().apply {
            put("message_id", messageId)
            put("channel", channelId)
            put("channel_id", channelId)
            put("timestamp", "2024-01-01T00:00:00.000Z")
            put("ciphertext", encrypted[0])
            put("nonce", encrypted[1])
            put("tag", encrypted[2])
        }

        val channel = ConnectMessagingChannelRecord().apply {
            this.channelId = this@PushNotificationHelperTest.channelId
            key = encryptedKey
        }

        return ConnectMessagingMessageRecord.fromJson(
            json,
            listOf(channel)
        )!!
    }
}

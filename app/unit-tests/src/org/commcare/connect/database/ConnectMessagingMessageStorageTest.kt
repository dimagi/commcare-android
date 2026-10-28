package org.commcare.connect.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.connect.network.personalId.parser.NotificationTestUtil
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingMessageStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var channel: ConnectMessagingChannelRecord

    @Before
    fun setUp() {
        channel =
            ConnectMessagingChannelRecord().apply {
                channelId = CHANNEL_ID
                channelCreated = Date()
                consented = true
                channelSource = ""
                keyUrl = ""
                key = NotificationTestUtil.TEST_ENCRYPTION_KEY
                channelName = ""
            }
        ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel)
    }

    @After
    fun tearDown() {
        ConnectDatabaseHelper.teardown()
    }

    @Test
    fun `unacknowledged message delivered again stays read`() {
        deliver(version = 3)
        markRead()

        deliver(version = 3)

        assertTrue(storedMessage().userViewed)
    }

    @Test
    fun `message delivered again in a version the app now understands is unread with its full content`() {
        deliver(version = 3)
        markRead()

        deliver(version = 2)

        val message = storedMessage()
        assertFalse(message.userViewed)
        assertEquals("Site visit notes", message.displayText)
    }

    @Test
    fun `stored media-only message still shows no text instead of its legacy content`() {
        deliver(version = 2, emptyRichText = true)

        assertEquals("", storedMessage().displayText)
    }

    private fun deliver(
        version: Int,
        emptyRichText: Boolean = false,
    ) {
        val json =
            JSONObject(
                NotificationTestUtil.createRichMessagingNotification(
                    "notification-1",
                    MESSAGE_ID,
                    CHANNEL_ID,
                    content = "Site visit notes",
                    version = version,
                ),
            )
        if (emptyRichText) {
            json.put("rich_text", "")
        }
        ConnectMessagingDatabaseHelper.storeMessagingMessages(
            context,
            listOf(ConnectMessagingMessageRecord.fromJson(json, listOf(channel))!!),
            false,
        )
    }

    private fun markRead() {
        val message = storedMessage()
        message.userViewed = true
        ConnectMessagingDatabaseHelper.storeMessagingMessage(context, message)
    }

    private fun storedMessage() = ConnectMessagingDatabaseHelper.getMessagingMessagesForChannel(CHANNEL_ID).single()

    private companion object {
        const val CHANNEL_ID = "channel-1"
        const val MESSAGE_ID = "message-1"
    }
}

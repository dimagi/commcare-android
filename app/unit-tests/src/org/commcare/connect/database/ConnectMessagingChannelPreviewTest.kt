package org.commcare.connect.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.connect.network.personalId.parser.NotificationTestUtil
import org.commcare.dalvik.R
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingChannelPreviewTest {
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
    fun `rich message still downloading is previewed as a media message to download`() {
        storeRichMessageWithAttachment(ConnectMessagingAttachmentState.QUEUED)

        assertEquals(context.getString(R.string.connect_messaging_preview_pending_download), previewText())
    }

    @Test
    fun `downloaded rich message is previewed by its text`() {
        storeRichMessageWithAttachment(ConnectMessagingAttachmentState.AVAILABLE)

        assertEquals("Site visit notes", previewText())
    }

    private fun previewText() =
        ConnectMessagingDatabaseHelper
            .getMessagingChannels(context)
            .single { it.channelId == CHANNEL_ID }
            .preview
            .toString()

    private fun storeRichMessageWithAttachment(state: ConnectMessagingAttachmentState) {
        val json =
            JSONObject(
                NotificationTestUtil.createRichMessagingNotification(
                    "notification-1",
                    MESSAGE_ID,
                    CHANNEL_ID,
                    content = "Site visit notes",
                ),
            )
        val message = ConnectMessagingMessageRecord.fromJson(json, listOf(channel))!!
        ConnectMessagingDatabaseHelper.storeMessagingMessage(context, message)
        ConnectMessagingAttachmentDatabaseHelper.save(
            ConnectMessagingAttachmentRecord().apply {
                attachmentId = "attachment-1"
                messageId = MESSAGE_ID
                name = "map.png"
                type = "image/png"
                size = 100
                downloadState = state
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "channel-1"
        const val MESSAGE_ID = "message-1"
    }
}

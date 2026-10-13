package org.commcare.fragments.connectMessaging

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentRecord
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.util.ConnectTestUtils.signInToPersonalId
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectMessagingAttachmentDatabaseHelper
import org.commcare.connect.database.ConnectMessagingAttachmentFileStore
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.connect.network.personalId.parser.NotificationTestUtil
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ItemChatLeftRichViewBinding
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessageImageViewerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockApi = PersonalIdMockApiServer()
    private lateinit var activity: ConnectMessagingActivity
    private lateinit var savedPersonalIdStatus: PersonalIdManager.PersonalIdStatus
    private lateinit var channel: ConnectMessagingChannelRecord

    @Before
    fun setUp() {
        mockApi.start()
        repeat(SYNC_RESPONSES) {
            mockApi.server.enqueue(MockResponse().setBody("""{"notifications": [], "channels": []}"""))
        }
        savedPersonalIdStatus = PersonalIdManager.getInstance().status
        signInToPersonalId(userId = "test-user", password = "test-password")
        channel =
            ConnectMessagingChannelRecord().apply {
                channelId = CHANNEL_ID
                channelCreated = Date()
                consented = true
                channelSource = ""
                keyUrl = ""
                key = NotificationTestUtil.TEST_ENCRYPTION_KEY
                channelName = "Site team"
            }
        ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel)
    }

    @After
    fun tearDown() {
        mockApi.shutdown()
        ConnectMessagingAttachmentFileStore.deleteAll(context)
        ConnectDatabaseHelper.teardown()
        PersonalIdManager.getInstance().status = savedPersonalIdStatus
    }

    @Test
    fun `tapping an image opens the viewer with its date and caption, and back returns to the chat`() {
        storeImageMessage(caption = "Site map for Thursday")
        openChat()

        chatImage().performClick()
        settle()

        assertEquals(R.id.connectMessageImageViewerFragment, navController().currentDestination?.id)
        assertEquals(expectedTitle(), activity.title.toString())
        val caption = activity.findViewById<TextView>(R.id.tvCaption)
        assertEquals(View.VISIBLE, caption.visibility)
        assertEquals("Site map for Thursday", caption.text.toString().trim())

        activity.onBackPressedDispatcher.onBackPressed()
        settle()

        assertEquals(R.id.connectMessageFragment, navController().currentDestination?.id)
    }

    @Test
    fun `viewer grows out of the tapped image through a shared transition name`() {
        storeImageMessage(caption = "Site map for Thursday")
        openChat()
        val image = chatImage()

        image.performClick()
        settle()

        val transitionImage = activity.findViewById<View>(R.id.ivTransitionImage)
        assertEquals(ConnectMessageImageViewerFragment.transitionNameFor(ATTACHMENT_ID), image.transitionName)
        assertEquals(image.transitionName, transitionImage.transitionName)
    }

    @Test
    fun `caption appears only once the image is in place`() {
        storeImageMessage(caption = "Site map for Thursday")
        openChat()

        chatImage().performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.tvCaption).visibility)

        settle()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tvCaption).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.vBackdrop).visibility)
    }

    @Test
    fun `going back hands the image to the shrinking copy`() {
        storeImageMessage(caption = "Site map for Thursday")
        openChat()
        chatImage().performClick()
        settle()
        val transitionImage = activity.findViewById<View>(R.id.ivTransitionImage)
        val zoomableImage = activity.findViewById<View>(R.id.ivImage)

        activity.onBackPressedDispatcher.onBackPressed()
        ShadowLooper.idleMainLooper()

        assertEquals(View.VISIBLE, transitionImage.visibility)
        assertEquals(0f, zoomableImage.alpha, 0f)
    }

    @Test
    fun `zoomable image stays drawn while it loads so it can take over and be pinched`() {
        storeImageMessage(caption = "Site map for Thursday")
        openChat()

        chatImage().performClick()
        settle()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.ivImage).visibility)
    }

    @Test
    fun `returning from the viewer keeps the tapped image on screen for the shrink back`() {
        storeImageMessage(caption = "Site map for Thursday")
        storePlainMessages(LATER_MESSAGES)
        openChat()
        val chat = layOutChat()
        chat.scrollToPosition(0)
        layOutChat()

        imageAt(chat, 0).performClick()
        settle()
        assertEquals(R.id.connectMessageImageViewerFragment, navController().currentDestination?.id)
        activity.onBackPressedDispatcher.onBackPressed()
        settle()

        val returnedChat = layOutChat()
        assertNotSame(chat, returnedChat)
        assertEquals(0, (returnedChat.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
    }

    @Test
    fun `media-only message shows the image without a caption`() {
        storeImageMessage(caption = "")
        openChat()

        chatImage().performClick()
        settle()

        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.tvCaption).visibility)
    }

    @Test
    fun `viewer closes when the image file is gone`() {
        storeImageMessage(caption = "Site map for Thursday", writeFile = false)
        openChat()

        navController().navigate(
            R.id.connectMessageImageViewerFragment,
            Bundle().apply { putString("attachment_id", ATTACHMENT_ID) },
        )
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.connectMessageFragment, navController().currentDestination?.id)
    }

    private fun storeImageMessage(
        caption: String,
        writeFile: Boolean = true,
    ) {
        val json =
            JSONObject(
                NotificationTestUtil.createRichMessagingNotification(
                    "notification-1",
                    MESSAGE_ID,
                    CHANNEL_ID,
                    content = "[Image]",
                    richText = caption.ifEmpty { null },
                    attachments = listOf(NotificationTestUtil.createAttachmentJson(ATTACHMENT_ID, "site-map.png")),
                ),
            )
        if (caption.isEmpty()) {
            json.put("rich_text", "")
        }
        val message = ConnectMessagingMessageRecord.fromJson(json, listOf(channel))!!
        ConnectMessagingDatabaseHelper.storeMessagingMessages(context, listOf(message), false)
        for (attachment in ConnectMessagingAttachmentRecord.listFromMessageJson(json, MESSAGE_ID, CHANNEL_ID)) {
            attachment.downloadState = ConnectMessagingAttachmentState.AVAILABLE
            ConnectMessagingAttachmentDatabaseHelper.save(attachment)
        }
        if (writeFile) {
            ConnectMessagingAttachmentFileStore.directory(context).mkdirs()
            ConnectMessageTestImages.writePng(ConnectMessagingAttachmentFileStore.fileFor(context, ATTACHMENT_ID), 400, 300)
        }
    }

    private fun storePlainMessages(count: Int) {
        val messages =
            (1..count).map { index ->
                ConnectMessagingMessageRecord().apply {
                    messageId = "later-$index"
                    channelId = CHANNEL_ID
                    timeStamp = Date()
                    message = "Later message $index"
                    isOutgoing = false
                }
            }
        ConnectMessagingDatabaseHelper.storeMessagingMessages(context, messages, false)
    }

    private fun settle() {
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    private fun expectedTitle(): String {
        val message = ConnectMessagingAttachmentDatabaseHelper.getMessage(MESSAGE_ID)!!
        return DateUtils.formatDateTime(
            activity,
            message.timeStamp.time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }

    private fun openChat() {
        activity = Robolectric.buildActivity(ConnectMessagingActivity::class.java).setup().get()
        mockApi.drainHttp()
        navController().navigate(R.id.connectMessageFragment, Bundle().apply { putString("channel_id", CHANNEL_ID) })
        ShadowLooper.idleMainLooper()
        mockApi.drainHttp()
        ShadowLooper.idleMainLooper()
    }

    private fun chatImage(): View = imageAt(layOutChat(), 0)

    private fun layOutChat(): RecyclerView {
        val chat = activity.findViewById<RecyclerView>(R.id.rvChat)
        chat.measure(
            View.MeasureSpec.makeMeasureSpec(CHAT_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(CHAT_HEIGHT_PX, View.MeasureSpec.EXACTLY),
        )
        chat.layout(0, 0, CHAT_WIDTH_PX, CHAT_HEIGHT_PX)
        return chat
    }

    private fun imageAt(
        chat: RecyclerView,
        position: Int,
    ): View {
        val row = ItemChatLeftRichViewBinding.bind(chat.findViewHolderForAdapterPosition(position)!!.itemView)
        return row.llAttachments.getChildAt(0)
    }

    private fun navController() =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect_messaging) as NavHostFragment)
            .navController

    private companion object {
        const val CHANNEL_ID = "channel-1"
        const val MESSAGE_ID = "message-1"
        const val ATTACHMENT_ID = "3f0c4a52-9d1e-4b7a-8c2d-5e6f7a8b9c0d"
        const val SYNC_RESPONSES = 3
        const val CHAT_WIDTH_PX = 1080
        const val CHAT_HEIGHT_PX = 1920
        const val LATER_MESSAGES = 25
    }
}

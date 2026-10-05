package org.commcare.fragments.connectMessaging

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.util.ConnectTestUtils.signInToPersonalId
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.connect.network.personalId.parser.NotificationTestUtil
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ItemChatLeftRichViewBinding
import org.commcare.dalvik.databinding.ViewConnectMessageAttachmentPendingBinding
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessageUnsupportedVersionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockApi = PersonalIdMockApiServer()
    private lateinit var activity: ConnectMessagingActivity
    private lateinit var channel: ConnectMessagingChannelRecord

    @Before
    fun setUp() {
        mockApi.start()
        repeat(SYNC_RESPONSES) {
            mockApi.server.enqueue(MockResponse().setBody("""{"notifications": [], "channels": []}"""))
        }
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
        ConnectDatabaseHelper.teardown()
    }

    @Test
    fun `message too new for this app shows a placeholder and an update banner`() {
        storeMessage(version = 3)

        openChat()

        assertEquals(View.VISIBLE, activity.findViewById<TextView>(R.id.tvUpdateBanner).visibility)
        val row = ItemChatLeftRichViewBinding.bind(chatRow(0))
        assertEquals(View.GONE, row.tvChatMessage.visibility)
        val tile = ViewConnectMessageAttachmentPendingBinding.bind(row.llAttachments.getChildAt(0))
        assertEquals(context.getString(R.string.connect_messaging_update_app_notice), tile.tvLabel.text.toString())
    }

    @Test
    fun `channel with only supported messages shows no update banner`() {
        storeMessage(version = 2)

        openChat()

        assertEquals(View.GONE, activity.findViewById<TextView>(R.id.tvUpdateBanner).visibility)
    }

    private fun storeMessage(version: Int) {
        val json =
            JSONObject(
                NotificationTestUtil.createRichMessagingNotification(
                    "notification-1",
                    "message-1",
                    CHANNEL_ID,
                    content = "Placeholder from the server",
                    version = version,
                ),
            )
        ConnectMessagingDatabaseHelper.storeMessagingMessages(
            context,
            listOf(ConnectMessagingMessageRecord.fromJson(json, listOf(channel))!!),
            false,
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

    private fun chatRow(position: Int): View {
        val chat = activity.findViewById<RecyclerView>(R.id.rvChat)
        chat.measure(
            View.MeasureSpec.makeMeasureSpec(CHAT_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(CHAT_HEIGHT_PX, View.MeasureSpec.EXACTLY),
        )
        chat.layout(0, 0, CHAT_WIDTH_PX, CHAT_HEIGHT_PX)
        return chat.findViewHolderForAdapterPosition(position)!!.itemView
    }

    private fun navController() =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect_messaging) as NavHostFragment)
            .navController

    private companion object {
        const val CHANNEL_ID = "channel-1"
        const val SYNC_RESPONSES = 3
        const val CHAT_WIDTH_PX = 1080
        const val CHAT_HEIGHT_PX = 1920
    }
}

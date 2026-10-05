package org.commcare.adapters

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ItemChatLeftRichViewBinding
import org.commcare.dalvik.databinding.ItemChatRightViewBinding
import org.commcare.dalvik.databinding.ViewConnectMessageAttachmentPendingBinding
import org.commcare.fragments.connectMessaging.ConnectMessageAttachmentItem
import org.commcare.fragments.connectMessaging.ConnectMessageAttachmentListener
import org.commcare.fragments.connectMessaging.ConnectMessageChatData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessageAdapterTest {
    private lateinit var adapter: ConnectMessageAdapter
    private lateinit var observer: RecordingObserver

    @Before
    fun setUp() {
        adapter = ConnectMessageAdapter(ArrayList(), IgnoringAttachmentListener)
        observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)
    }

    @Test
    fun `initial load inserts all messages and reports new messages`() {
        val hasNewMessages = adapter.updateData(listOf(getIncomingChat("a"), getIncomingChat("b")))

        assertTrue(hasNewMessages)
        assertEquals(listOf(Event.Inserted(0, 2)), observer.events)
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `refresh with unchanged messages dispatches no updates`() {
        adapter.updateData(listOf(getIncomingChat("a"), getIncomingChat("b")))
        observer.events.clear()

        val hasNewMessages = adapter.updateData(listOf(getIncomingChat("a"), getIncomingChat("b")))

        assertFalse(hasNewMessages)
        assertTrue(observer.events.isEmpty())
    }

    @Test
    fun `message appended to the list is a single insertion at the end`() {
        adapter.updateData(listOf(getIncomingChat("a"), getIncomingChat("b")))
        observer.events.clear()

        val hasNewMessages = adapter.updateData(listOf(getIncomingChat("a"), getIncomingChat("b"), getIncomingChat("c")))

        assertTrue(hasNewMessages)
        assertEquals(listOf(Event.Inserted(2, 1)), observer.events)
    }

    @Test
    fun `read status change is a payload-only change on that row`() {
        adapter.updateData(listOf(getIncomingChat("a"), getOutgoingChat("b", read = false)))
        observer.events.clear()

        val hasNewMessages = adapter.updateData(listOf(getIncomingChat("a"), getOutgoingChat("b", read = true)))

        assertFalse(hasNewMessages)
        assertEquals(listOf(Event.Changed(1, 1, hasPayload = true)), observer.events)
    }

    @Test
    fun `text change is a full change on that row`() {
        adapter.updateData(listOf(getIncomingChat("a"), getIncomingChat("b")))
        observer.events.clear()

        adapter.updateData(listOf(getIncomingChat("a", text = "edited"), getIncomingChat("b")))

        assertEquals(listOf(Event.Changed(0, 1, hasPayload = false)), observer.events)
    }

    @Test
    fun `updateMessageReadStatus is a payload-only change on that row`() {
        adapter.updateData(listOf(getOutgoingChat("a", read = false), getOutgoingChat("b", read = false)))
        observer.events.clear()

        adapter.updateMessageReadStatus(getOutgoingChat("a", read = true))

        assertEquals(listOf(Event.Changed(0, 1, hasPayload = true)), observer.events)
    }

    @Test
    fun `refresh with no messages reports no new messages`() {
        val hasNewMessages = adapter.updateData(emptyList())

        assertFalse(hasNewMessages)
        assertTrue(observer.events.isEmpty())
    }

    @Test
    fun `text and read status changing together is a full change on that row`() {
        adapter.updateData(listOf(getOutgoingChat("a", read = false)))
        observer.events.clear()

        adapter.updateData(listOf(getOutgoingChat("a", read = true, text = "edited")))

        assertEquals(listOf(Event.Changed(0, 1, hasPayload = false)), observer.events)
    }

    @Test
    fun `updateMessageReadStatus for an unknown message dispatches no updates`() {
        adapter.updateData(listOf(getOutgoingChat("a", read = false)))
        observer.events.clear()

        adapter.updateMessageReadStatus(getOutgoingChat("unknown", read = true))

        assertTrue(observer.events.isEmpty())
    }

    @Test
    fun `binding with the read status payload updates only the read icon`() {
        adapter.updateData(listOf(getOutgoingChat("a", read = false)))
        val holder = adapter.onCreateViewHolder(FrameLayout(themedContext()), ConnectMessageAdapter.RIGHTVIEW)
        adapter.onBindViewHolder(holder, 0)
        val binding = ItemChatRightViewBinding.bind(holder.itemView)
        assertEquals(R.drawable.ic_connect_message_unread, getReadIconResId(binding))
        binding.tvChatMessage.text = UNTOUCHED_TEXT

        adapter.updateMessageReadStatus(getOutgoingChat("a", read = true))
        adapter.onBindViewHolder(holder, 0, mutableListOf(requireNotNull(observer.lastPayload)))

        assertEquals(R.drawable.ic_connect_message_read, getReadIconResId(binding))
        assertEquals(UNTOUCHED_TEXT, binding.tvChatMessage.text.toString())
    }

    @Test
    fun `incoming message with attachments uses the rich row and a plain one does not`() {
        adapter.updateData(listOf(getIncomingChat("plain"), getRichChat("rich", ConnectMessagingAttachmentState.QUEUED)))

        assertEquals(ConnectMessageAdapter.LEFTVIEW, adapter.getItemViewType(0))
        assertEquals(ConnectMessageAdapter.LEFT_RICH_VIEW, adapter.getItemViewType(1))
    }

    @Test
    fun `attachment state change is a payload-only change on the owning row`() {
        adapter.updateData(
            listOf(getRichChat("a", ConnectMessagingAttachmentState.QUEUED), getRichChat("b", ConnectMessagingAttachmentState.QUEUED)),
        )
        observer.events.clear()

        val hasNewMessages =
            adapter.updateData(
                listOf(getRichChat("a", ConnectMessagingAttachmentState.QUEUED), getRichChat("b", ConnectMessagingAttachmentState.FAILED)),
            )

        assertFalse(hasNewMessages)
        assertEquals(listOf(Event.Changed(1, 1, hasPayload = true)), observer.events)
    }

    @Test
    fun `binding with the attachments payload redraws the attachments but not the text`() {
        adapter.updateData(listOf(getRichChat("a", ConnectMessagingAttachmentState.QUEUED)))
        val holder = adapter.onCreateViewHolder(FrameLayout(themedContext()), ConnectMessageAdapter.LEFT_RICH_VIEW)
        adapter.onBindViewHolder(holder, 0)
        val binding = ItemChatLeftRichViewBinding.bind(holder.itemView)
        binding.tvChatMessage.text = UNTOUCHED_TEXT

        adapter.updateData(listOf(getRichChat("a", ConnectMessagingAttachmentState.FAILED)))
        adapter.onBindViewHolder(holder, 0, mutableListOf(requireNotNull(observer.lastPayload)))

        val tile = ViewConnectMessageAttachmentPendingBinding.bind(binding.llAttachments.getChildAt(0))
        assertEquals(themedContext().getString(R.string.connect_messaging_attachment_download_failed), tile.tvLabel.text.toString())
        assertEquals(UNTOUCHED_TEXT, binding.tvChatMessage.text.toString())
    }

    private fun themedContext(): Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.ConnectTheme)

    private fun getRichChat(
        id: String,
        attachmentState: ConnectMessagingAttachmentState,
    ) = ConnectMessageChatData(
        id,
        ConnectMessageAdapter.LEFTVIEW,
        "message $id",
        "them",
        Date(TIMESTAMP),
        false,
        listOf(ConnectMessageAttachmentItem("attachment-$id", "site-map.jpg", "image/jpeg", 1000, attachmentState, null)),
        false,
    )

    private object IgnoringAttachmentListener : ConnectMessageAttachmentListener {
        override fun onAttachmentDownloadRequested(attachmentId: String) = Unit

        override fun onAttachmentOpenRequested(attachment: ConnectMessageAttachmentItem) = Unit
    }

    private fun getReadIconResId(binding: ItemChatRightViewBinding) =
        Shadows.shadowOf(binding.imgMessageReadStatus.drawable).createdFromResId

    private fun getIncomingChat(
        id: String,
        text: String = "message $id",
    ) = ConnectMessageChatData(id, ConnectMessageAdapter.LEFTVIEW, text, "them", Date(TIMESTAMP), false)

    private fun getOutgoingChat(
        id: String,
        read: Boolean,
        text: String = "message $id",
    ) = ConnectMessageChatData(id, ConnectMessageAdapter.RIGHTVIEW, text, "you", Date(TIMESTAMP), read)

    private sealed class Event {
        data class Inserted(
            val position: Int,
            val count: Int,
        ) : Event()

        data class Removed(
            val position: Int,
            val count: Int,
        ) : Event()

        data class Moved(
            val from: Int,
            val to: Int,
        ) : Event()

        data class Changed(
            val position: Int,
            val count: Int,
            val hasPayload: Boolean,
        ) : Event()

        object FullRefresh : Event()
    }

    private class RecordingObserver : RecyclerView.AdapterDataObserver() {
        val events = mutableListOf<Event>()
        var lastPayload: Any? = null

        override fun onChanged() {
            events.add(Event.FullRefresh)
        }

        override fun onItemRangeInserted(
            positionStart: Int,
            itemCount: Int,
        ) {
            events.add(Event.Inserted(positionStart, itemCount))
        }

        override fun onItemRangeRemoved(
            positionStart: Int,
            itemCount: Int,
        ) {
            events.add(Event.Removed(positionStart, itemCount))
        }

        override fun onItemRangeMoved(
            fromPosition: Int,
            toPosition: Int,
            itemCount: Int,
        ) {
            events.add(Event.Moved(fromPosition, toPosition))
        }

        override fun onItemRangeChanged(
            positionStart: Int,
            itemCount: Int,
            payload: Any?,
        ) {
            events.add(Event.Changed(positionStart, itemCount, payload != null))
            lastPayload = payload
        }
    }

    companion object {
        private const val TIMESTAMP = 1_000L
        private const val UNTOUCHED_TEXT = "untouched"
    }
}

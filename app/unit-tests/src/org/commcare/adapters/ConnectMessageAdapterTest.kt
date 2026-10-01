package org.commcare.adapters

import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.fragments.connectMessaging.ConnectMessageChatData
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
class ConnectMessageAdapterTest {
    private lateinit var adapter: ConnectMessageAdapter
    private lateinit var observer: RecordingObserver

    @Before
    fun setUp() {
        adapter = ConnectMessageAdapter(ArrayList())
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

    private fun getIncomingChat(
        id: String,
        text: String = "message $id",
    ) = ConnectMessageChatData(id, ConnectMessageAdapter.LEFTVIEW, text, "them", Date(TIMESTAMP), false)

    private fun getOutgoingChat(
        id: String,
        read: Boolean,
    ) = ConnectMessageChatData(id, ConnectMessageAdapter.RIGHTVIEW, "message $id", "you", Date(TIMESTAMP), read)

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
        }
    }

    companion object {
        private const val TIMESTAMP = 1_000L
    }
}

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
    fun initialLoad_insertsAllMessages() {
        val hasNewMessages = adapter.updateData(listOf(chat("a"), chat("b")))

        assertTrue(hasNewMessages)
        assertEquals(listOf(Event.Inserted(0, 2)), observer.events)
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun unchangedRefresh_dispatchesNothing() {
        adapter.updateData(listOf(chat("a"), chat("b")))
        observer.events.clear()

        val hasNewMessages = adapter.updateData(listOf(chat("a"), chat("b")))

        assertFalse(hasNewMessages)
        assertTrue(observer.events.isEmpty())
    }

    @Test
    fun appendedMessage_isSingleInsertionAtEnd() {
        adapter.updateData(listOf(chat("a"), chat("b")))
        observer.events.clear()

        val hasNewMessages = adapter.updateData(listOf(chat("a"), chat("b"), chat("c")))

        assertTrue(hasNewMessages)
        assertEquals(listOf(Event.Inserted(2, 1)), observer.events)
    }

    @Test
    fun readStatusChange_isPayloadChangeOnThatRow() {
        adapter.updateData(listOf(chat("a"), outgoing("b", read = false)))
        observer.events.clear()

        val hasNewMessages = adapter.updateData(listOf(chat("a"), outgoing("b", read = true)))

        assertFalse(hasNewMessages)
        assertEquals(listOf(Event.Changed(1, 1, hasPayload = true)), observer.events)
    }

    @Test
    fun textChange_isFullRowChange() {
        adapter.updateData(listOf(chat("a"), chat("b")))
        observer.events.clear()

        adapter.updateData(listOf(chat("a", text = "edited"), chat("b")))

        assertEquals(listOf(Event.Changed(0, 1, hasPayload = false)), observer.events)
    }

    @Test
    fun updateMessageReadStatus_isPayloadChangeOnThatRow() {
        adapter.updateData(listOf(outgoing("a", read = false), outgoing("b", read = false)))
        observer.events.clear()

        adapter.updateMessageReadStatus(outgoing("a", read = true))

        assertEquals(listOf(Event.Changed(0, 1, hasPayload = true)), observer.events)
    }

    private fun chat(
        id: String,
        text: String = "message $id",
    ) = ConnectMessageChatData(id, ConnectMessageAdapter.LEFTVIEW, text, "them", Date(TIMESTAMP), false)

    private fun outgoing(
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

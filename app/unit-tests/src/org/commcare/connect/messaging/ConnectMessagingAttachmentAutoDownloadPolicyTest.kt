package org.commcare.connect.messaging

import org.commcare.android.database.connect.models.ConnectMessagingAttachmentState
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class ConnectMessagingAttachmentAutoDownloadPolicyTest {
    private val now = Date(1_800_000_000_000)

    @Test
    fun `recent message among the newest incoming is queued`() {
        val state = ConnectMessagingAttachmentAutoDownloadPolicy.initialState(daysAgo(1), null, true, now)

        assertEquals(ConnectMessagingAttachmentState.QUEUED, state)
    }

    @Test
    fun `message seven or more days old waits for a tap`() {
        val state = ConnectMessagingAttachmentAutoDownloadPolicy.initialState(daysAgo(7), null, true, now)

        assertEquals(ConnectMessagingAttachmentState.WAITING, state)
    }

    @Test
    fun `recent message beyond the newest incoming waits for a tap`() {
        val state = ConnectMessagingAttachmentAutoDownloadPolicy.initialState(daysAgo(1), null, false, now)

        assertEquals(ConnectMessagingAttachmentState.WAITING, state)
    }

    @Test
    fun `message past its expiry is expired even when recent`() {
        val state = ConnectMessagingAttachmentAutoDownloadPolicy.initialState(daysAgo(1), now, true, now)

        assertEquals(ConnectMessagingAttachmentState.EXPIRED, state)
    }

    @Test
    fun `message expiring in the future is not expired`() {
        val state =
            ConnectMessagingAttachmentAutoDownloadPolicy.initialState(daysAgo(1), Date(now.time + 1), true, now)

        assertEquals(ConnectMessagingAttachmentState.QUEUED, state)
    }

    @Test
    fun `newest incoming ids are the latest fifty incoming messages and skip outgoing ones`() {
        val incoming = (1..51).map { message("incoming-$it", minutesAgo = it.toLong(), outgoing = false) }
        val outgoing = message("outgoing", minutesAgo = 0, outgoing = true)

        val newest = ConnectMessagingAttachmentAutoDownloadPolicy.newestIncomingMessageIds(incoming + outgoing)

        assertEquals((1..50).map { "incoming-$it" }.toSet(), newest)
    }

    private fun daysAgo(days: Long) = Date(now.time - TimeUnit.DAYS.toMillis(days))

    private fun message(
        messageId: String,
        minutesAgo: Long,
        outgoing: Boolean,
    ) = ConnectMessagingMessageRecord().apply {
        this.messageId = messageId
        timeStamp = Date(now.time - TimeUnit.MINUTES.toMillis(minutesAgo))
        isOutgoing = outgoing
    }
}

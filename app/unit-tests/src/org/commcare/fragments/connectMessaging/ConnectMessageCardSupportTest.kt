package org.commcare.fragments.connectMessaging

import org.commcare.dalvik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ConnectMessageCardSupportTest {
    private val messageId = UUID.randomUUID().toString()

    @Test
    fun `only card-html is a card`() {
        assertTrue(ConnectMessageCardSupport.isCard("card-html"))
        assertFalse(ConnectMessageCardSupport.isCard("attachment"))
        assertFalse(ConnectMessageCardSupport.isCard(null))
    }

    @Test
    fun `a card with an entry point on a capable device is shown`() {
        assertNull(ConnectMessageCardSupport.unavailableNotice(messageId, listOf("index.html", "data.json"), true))
    }

    @Test
    fun `a device that cannot sandbox cards gets the device notice`() {
        assertEquals(
            R.string.connect_messaging_card_unsupported_device,
            ConnectMessageCardSupport.unavailableNotice(messageId, listOf("index.html"), false),
        )
    }

    @Test
    fun `a card without index html cannot be shown`() {
        assertEquals(
            R.string.connect_messaging_card_missing_entry,
            ConnectMessageCardSupport.unavailableNotice(messageId, listOf("card.html", "style.css"), true),
        )
    }

    @Test
    fun `a card that stopped stays stopped`() {
        ConnectMessageCardSupport.markStopped(messageId)

        assertEquals(
            R.string.connect_messaging_card_stopped,
            ConnectMessageCardSupport.unavailableNotice(messageId, listOf("index.html"), true),
        )
    }
}
